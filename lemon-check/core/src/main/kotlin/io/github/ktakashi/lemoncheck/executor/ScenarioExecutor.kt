package io.github.ktakashi.lemoncheck.executor

import com.jayway.jsonpath.JsonPath
import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.context.ExecutionContext
import io.github.ktakashi.lemoncheck.exception.ConfigurationException
import io.github.ktakashi.lemoncheck.model.Assertion
import io.github.ktakashi.lemoncheck.model.AssertionResult
import io.github.ktakashi.lemoncheck.model.AssertionType
import io.github.ktakashi.lemoncheck.model.FragmentRegistry
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.ScenarioResult
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepResult
import io.github.ktakashi.lemoncheck.openapi.HttpMethod
import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
import io.github.ktakashi.lemoncheck.plugin.PluginRegistry
import io.github.ktakashi.lemoncheck.plugin.adapter.ScenarioContextAdapter
import io.github.ktakashi.lemoncheck.plugin.adapter.ScenarioResultAdapter
import io.github.ktakashi.lemoncheck.plugin.adapter.StepContextAdapter
import io.github.ktakashi.lemoncheck.plugin.adapter.StepResultAdapter
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * Executes BDD scenarios against API endpoints.
 *
 * @property specRegistry Registry for OpenAPI specifications
 * @property configuration Execution configuration
 * @property pluginRegistry Optional plugin registry for lifecycle hooks
 * @property fragmentRegistry Optional registry for reusable fragments
 */
class ScenarioExecutor(
    private val specRegistry: SpecRegistry,
    private val configuration: Configuration,
    private val pluginRegistry: PluginRegistry? = null,
    private val fragmentRegistry: FragmentRegistry? = null,
) {
    private val httpBuilder = HttpRequestBuilder(configuration)

    /**
     * Execute a single scenario.
     *
     * @param scenario The scenario to execute
     * @param sharedContext Optional shared context for cross-scenario variable sharing.
     *                      If provided, variables from previous scenarios are available.
     * @param sourceFile Optional source file for the scenario (used in reports for grouping).
     */
    fun execute(
        scenario: Scenario,
        sharedContext: ExecutionContext? = null,
        sourceFile: java.io.File? = null,
    ): ScenarioResult {
        val startTime = Instant.now()
        val context = sharedContext?.createChild() ?: ExecutionContext()
        val scenarioContext = ScenarioContextAdapter(scenario, context, startTime, sourceFile)

        pluginRegistry?.dispatchScenarioStart(scenarioContext)

        val stepResults = executeAllSteps(scenario, context, scenarioContext)
        val overallStatus = determineOverallStatus(stepResults)
        val duration = Duration.between(startTime, Instant.now())

        val scenarioResult =
            ScenarioResult(
                scenario = scenario,
                status = overallStatus,
                stepResults = stepResults,
                startTime = startTime,
                duration = duration,
            )

        pluginRegistry?.dispatchScenarioEnd(scenarioContext, ScenarioResultAdapter(scenarioResult))

        // Copy extracted variables back to shared context for cross-scenario sharing
        if (sharedContext != null && scenarioResult.status == ResultStatus.PASSED) {
            context.allVariables().forEach { (name, value) ->
                value?.let { sharedContext[name] = it }
            }
        }

        return scenarioResult
    }

    /**
     * Execute all steps (background + scenario) and return results.
     */
    private fun executeAllSteps(
        scenario: Scenario,
        context: ExecutionContext,
        scenarioContext: ScenarioContextAdapter,
    ): List<StepResult> {
        val stepResults = mutableListOf<StepResult>()
        var stepIndex = 0
        var continueExecution = true

        // Execute background steps
        continueExecution =
            executeStepsWithContinuation(
                scenario.background,
                context,
                scenarioContext,
                stepResults,
                stepIndex,
                continueExecution,
            ) { stepIndex++ }

        // Execute scenario steps
        for (step in scenario.steps) {
            if (!continueExecution) {
                // Skip remaining steps
                stepResults.add(StepResult(step = step, status = ResultStatus.SKIPPED))
                continue
            }

            val expandedSteps = expandStep(step)
            for (expandedStep in expandedSteps) {
                if (!continueExecution) {
                    stepResults.add(StepResult(step = expandedStep, status = ResultStatus.SKIPPED))
                    continue
                }

                val result = executeStepWithPlugins(expandedStep, context, scenarioContext, stepIndex++)
                stepResults.add(result)

                if (result.status != ResultStatus.PASSED) {
                    continueExecution = false
                }
            }
        }

        return stepResults
    }

    /**
     * Execute a list of steps with continuation control.
     */
    private fun executeStepsWithContinuation(
        steps: List<Step>,
        context: ExecutionContext,
        scenarioContext: ScenarioContextAdapter,
        results: MutableList<StepResult>,
        startIndex: Int,
        initialContinue: Boolean,
        onStepExecuted: () -> Unit,
    ): Boolean {
        var continueExecution = initialContinue

        for (step in steps) {
            if (!continueExecution) break

            val expandedSteps = expandStep(step)
            for (expandedStep in expandedSteps) {
                if (!continueExecution) break

                val result = executeStepWithPlugins(expandedStep, context, scenarioContext, startIndex)
                results.add(result)
                onStepExecuted()

                if (result.status != ResultStatus.PASSED) {
                    continueExecution = false
                }
            }
        }

        return continueExecution
    }

    /**
     * Determine overall status from step results.
     */
    private fun determineOverallStatus(stepResults: List<StepResult>): ResultStatus =
        when {
            stepResults.isEmpty() -> ResultStatus.PASSED
            stepResults.any { it.status == ResultStatus.ERROR } -> ResultStatus.ERROR
            stepResults.any { it.status == ResultStatus.FAILED } -> ResultStatus.FAILED
            stepResults.all { it.status == ResultStatus.SKIPPED } -> ResultStatus.SKIPPED
            else -> ResultStatus.PASSED
        }

    private fun executeStepWithPlugins(
        step: Step,
        context: ExecutionContext,
        scenarioContext: ScenarioContextAdapter,
        stepIndex: Int,
    ): StepResult {
        // Create step context
        val stepContext = StepContextAdapter(step, stepIndex, scenarioContext)

        // Dispatch plugin: onStepStart
        pluginRegistry?.dispatchStepStart(stepContext)

        // Execute the actual step
        val result = executeStep(step, context)

        // Dispatch plugin: onStepEnd
        pluginRegistry?.dispatchStepEnd(stepContext, StepResultAdapter(result))

        return result
    }

    /**
     * Expand a step by resolving any fragment references.
     *
     * If the step references a fragment (via fragmentName), returns the steps
     * from that fragment. Otherwise, returns a list containing just the original step.
     *
     * @param step The step to expand
     * @return List of steps to execute (fragment steps or original step)
     */
    private fun expandStep(step: Step): List<Step> {
        val fragmentName = step.fragmentName ?: return listOf(step)

        // Look up the fragment in the registry
        val fragment =
            fragmentRegistry?.get(fragmentName)
                ?: throw ConfigurationException(
                    "Fragment '$fragmentName' not found. " +
                        "Register it with fragmentRegistry.register() or load from a .fragment file.",
                )

        return fragment.steps
    }

    private fun executeStep(
        step: Step,
        context: ExecutionContext,
    ): StepResult {
        val stepStartTime = Instant.now()

        // If no operation to call, check if there are assertions or extractions to run against the last response
        return step.operationId?.let {
            executeOperationStep(step, context, stepStartTime)
        } ?: executeNonOperationStep(step, context, stepStartTime)
    }

    /**
     * Execute a step that has no operationId (assertions/extractions against last response or no-op).
     */
    private fun executeNonOperationStep(
        step: Step,
        context: ExecutionContext,
        stepStartTime: Instant,
    ): StepResult =
        if (step.assertions.isEmpty() && step.extractions.isEmpty()) {
            // No operation and no assertions - just pass
            StepResult(
                step = step,
                status = ResultStatus.PASSED,
                duration = Duration.between(stepStartTime, Instant.now()),
            )
        } else {
            // Run assertions/extractions against last response
            context.lastResponse?.let { response ->
                buildResultFromResponse(step, response, stepStartTime, context)
            } ?: StepResult(
                step = step,
                status = ResultStatus.ERROR,
                duration = Duration.between(stepStartTime, Instant.now()),
                error = IllegalStateException("No previous response to run assertions/extractions against"),
            )
        }

    /**
     * Execute a step with an operationId (HTTP request).
     */
    private fun executeOperationStep(
        step: Step,
        context: ExecutionContext,
        stepStartTime: Instant,
    ): StepResult =
        runCatching {
            executeHttpRequest(step, context, stepStartTime)
        }.getOrElse { e ->
            StepResult(
                step = step,
                status = ResultStatus.ERROR,
                duration = Duration.between(stepStartTime, Instant.now()),
                error = e as? Exception ?: RuntimeException(e),
            )
        }

    /**
     * Build request context and execute HTTP request.
     */
    private fun executeHttpRequest(
        step: Step,
        context: ExecutionContext,
        stepStartTime: Instant,
    ): StepResult {
        // Resolve the operation
        val (spec, resolvedOp) = specRegistry.resolve(step.operationId!!, step.specName)

        // Build the URL
        val baseUrl = configuration.baseUrl ?: spec.baseUrl
        val url =
            httpBuilder.buildUrl(
                baseUrl = baseUrl,
                path = resolvedOp.path,
                pathParams = resolveParams(step.pathParams, context),
                queryParams = resolveParams(step.queryParams, context),
            )

        // Merge headers immutably
        val headers = configuration.defaultHeaders + spec.defaultHeaders + step.headers

        // Resolve body with variable interpolation
        val body = step.body?.let { context.interpolate(it) }

        // Log request if enabled
        logRequest(resolvedOp.method, url, headers, body)

        // Record request start time for logging
        val requestStartTime = System.currentTimeMillis()

        // Execute the HTTP request
        val response =
            httpBuilder.execute(
                method = resolvedOp.method,
                url = url,
                headers = headers,
                body = body,
            )

        // Log response if enabled
        logResponse(resolvedOp.method, url, response, requestStartTime)

        // Update context with response
        context.updateLastResponse(response)

        return buildResultFromResponse(step, response, stepStartTime, context)
    }

    /**
     * Build a StepResult from an HTTP response.
     */
    private fun buildResultFromResponse(
        step: Step,
        response: HttpResponse<String>,
        stepStartTime: Instant,
        context: ExecutionContext,
    ): StepResult {
        val extractedValues = extractValues(response, step, context)
        val assertionResults = runAssertions(response, step.assertions)
        val allPassed = assertionResults.all { it.passed }

        return StepResult(
            step = step,
            status = if (allPassed) ResultStatus.PASSED else ResultStatus.FAILED,
            statusCode = response.statusCode(),
            responseBody = response.body(),
            responseHeaders = response.headers().map(),
            duration = Duration.between(stepStartTime, Instant.now()),
            extractedValues = extractedValues,
            assertionResults = assertionResults,
        )
    }

    /**
     * Log HTTP request if enabled.
     */
    private fun logRequest(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ) {
        if (configuration.logRequests) {
            configuration.getEffectiveHttpLogger().logRequest(method, url, headers, body)
        }
    }

    /**
     * Log HTTP response if enabled.
     */
    private fun logResponse(
        method: HttpMethod,
        url: String,
        response: HttpResponse<String>,
        requestStartTime: Long,
    ) {
        if (configuration.logResponses) {
            val durationMs = System.currentTimeMillis() - requestStartTime
            configuration.getEffectiveHttpLogger().logResponse(method, url, response, durationMs)
        }
    }

    private fun resolveParams(
        params: Map<String, Any>,
        context: ExecutionContext,
    ): Map<String, Any> =
        params.mapValues { (_, value) ->
            when (value) {
                is String -> context.interpolate(value)
                else -> value
            }
        }

    private fun extractValues(
        response: HttpResponse<String>,
        step: Step,
        context: ExecutionContext,
    ): Map<String, Any?> =
        step.extractions.associate { extraction ->
            val value =
                runCatching {
                    val body = response.body() ?: ""
                    JsonPath.read<Any>(body, extraction.jsonPath).also {
                        context[extraction.variableName] = it
                    }
                }.getOrNull()
            extraction.variableName to value
        }

    private fun runAssertions(
        response: HttpResponse<String>,
        assertions: List<Assertion>,
    ): List<AssertionResult> = assertions.map { assertion -> runAssertion(response, assertion) }

    private fun runAssertion(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult =
        when (assertion.type) {
            AssertionType.STATUS_CODE -> assertStatusCode(response, assertion)
            AssertionType.BODY_CONTAINS -> assertBodyContains(response, assertion)
            AssertionType.BODY_EQUALS -> assertBodyEquals(response, assertion)
            AssertionType.BODY_MATCHES -> assertBodyMatches(response, assertion)
            AssertionType.BODY_ARRAY_SIZE -> assertBodyArraySize(response, assertion)
            AssertionType.BODY_ARRAY_NOT_EMPTY -> assertBodyArrayNotEmpty(response, assertion)
            AssertionType.HEADER_EXISTS -> assertHeaderExists(response, assertion)
            AssertionType.HEADER_EQUALS -> assertHeaderEquals(response, assertion)
            AssertionType.MATCHES_SCHEMA -> AssertionResult(assertion, true, "Schema validation not implemented yet")
            AssertionType.RESPONSE_TIME -> AssertionResult(assertion, true, "Response time assertion not implemented yet")
        }

    private fun assertStatusCode(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val actual = response.statusCode()
        val expected = assertion.expected

        val passed =
            when (expected) {
                is Number -> actual == expected.toInt()
                is IntRange -> actual in expected
                else -> false
            }

        return AssertionResult(
            assertion = assertion,
            passed = passed,
            message = if (passed) "Status code is $actual" else "Expected status $expected but got $actual",
            actual = actual,
        )
    }

    private fun assertBodyContains(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val substring = assertion.expected as? String ?: ""
        val passed = body.contains(substring)

        return AssertionResult(
            assertion = assertion,
            passed = passed,
            message = if (passed) "Body contains '$substring'" else "Body does not contain '$substring'",
            actual = body.take(200),
        )
    }

    private fun assertBodyEquals(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"
        val expected = assertion.expected

        return runCatching { JsonPath.read<Any>(body, jsonPath) }
            .fold(
                onSuccess = { actual ->
                    val passed = valuesEqual(actual, expected)
                    AssertionResult(
                        assertion = assertion,
                        passed = passed,
                        message = if (passed) "$jsonPath equals $expected" else "Expected $expected at $jsonPath but got $actual",
                        actual = actual,
                    )
                },
                onFailure = { e ->
                    AssertionResult(
                        assertion = assertion,
                        passed = false,
                        message = "Failed to evaluate JSONPath '$jsonPath': ${e.message}",
                        actual = null,
                    )
                },
            )
    }

    /**
     * Compare values handling type coercion (e.g., Int 1 == String "1", Double 1.0 == Int 1).
     */
    private fun valuesEqual(
        actual: Any?,
        expected: Any?,
    ): Boolean {
        if (actual == expected) return true
        if (actual == null || expected == null) return false

        // Try numeric comparison when both can be converted to numbers
        val actualNum = toNumber(actual)
        val expectedNum = toNumber(expected)
        if (actualNum != null && expectedNum != null) {
            return actualNum.toDouble() == expectedNum.toDouble()
        }

        // Fall back to string comparison
        return actual.toString() == expected.toString()
    }

    private fun toNumber(value: Any?): Number? =
        when (value) {
            is Number -> value
            is String -> value.toDoubleOrNull()
            else -> null
        }

    private fun assertBodyMatches(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"
        val pattern = assertion.pattern ?: ""

        return runCatching {
            val actual = JsonPath.read<Any>(body, jsonPath)?.toString() ?: ""
            val regex = Regex(pattern)
            val passed = regex.containsMatchIn(actual)
            Triple(actual, passed, if (passed) "$jsonPath matches pattern" else "Value at $jsonPath does not match pattern '$pattern'")
        }.fold(
            onSuccess = { (actual, passed, message) ->
                AssertionResult(assertion = assertion, passed = passed, message = message, actual = actual)
            },
            onFailure = { e ->
                AssertionResult(assertion = assertion, passed = false, message = "Failed to evaluate: ${e.message}", actual = null)
            },
        )
    }

    private fun assertBodyArraySize(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"
        val expected = assertion.expected as? Int ?: 0

        return runCatching { JsonPath.read<List<*>>(body, jsonPath) }
            .fold(
                onSuccess = { array ->
                    val actual = array.size
                    val passed = actual == expected
                    AssertionResult(
                        assertion = assertion,
                        passed = passed,
                        message = if (passed) "Array size is $actual" else "Expected array size $expected but got $actual",
                        actual = actual,
                    )
                },
                onFailure = { e ->
                    AssertionResult(
                        assertion = assertion,
                        passed = false,
                        message = "Failed to evaluate array at '$jsonPath': ${e.message}",
                        actual = null,
                    )
                },
            )
    }

    private fun assertBodyArrayNotEmpty(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"

        return runCatching { JsonPath.read<List<*>>(body, jsonPath) }
            .fold(
                onSuccess = { array ->
                    val passed = array.isNotEmpty()
                    AssertionResult(
                        assertion = assertion,
                        passed = passed,
                        message = if (passed) "Array is not empty (size: ${array.size})" else "Array at $jsonPath is empty",
                        actual = array.size,
                    )
                },
                onFailure = { e ->
                    AssertionResult(
                        assertion = assertion,
                        passed = false,
                        message = "Failed to evaluate array at '$jsonPath': ${e.message}",
                        actual = null,
                    )
                },
            )
    }

    private fun assertHeaderExists(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val headerName = assertion.headerName ?: ""
        val exists = response.headers().firstValue(headerName).isPresent

        return AssertionResult(
            assertion = assertion,
            passed = exists,
            message = if (exists) "Header '$headerName' exists" else "Header '$headerName' not found",
            actual = exists,
        )
    }

    private fun assertHeaderEquals(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val headerName = assertion.headerName ?: ""
        val expected = assertion.expected as? String ?: ""
        val actual = response.headers().firstValue(headerName).orElse(null)
        val passed = actual == expected

        val passedMessage = "Header '$headerName' equals '$expected'"
        val failedMessage = "Expected header '$headerName' to be '$expected' but got '$actual'"
        return AssertionResult(
            assertion = assertion,
            passed = passed,
            message = if (passed) passedMessage else failedMessage,
            actual = actual,
        )
    }
}
