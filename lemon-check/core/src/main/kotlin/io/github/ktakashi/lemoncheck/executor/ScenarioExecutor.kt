package io.github.ktakashi.lemoncheck.executor

import com.jayway.jsonpath.JsonPath
import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.context.ExecutionContext
import io.github.ktakashi.lemoncheck.model.Assertion
import io.github.ktakashi.lemoncheck.model.AssertionResult
import io.github.ktakashi.lemoncheck.model.AssertionType
import io.github.ktakashi.lemoncheck.model.FragmentRegistry
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.ScenarioResult
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepResult
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
    private val httpBuilder = HttpRequestBuilder()
    private val responseHandler = ResponseHandler()

    /**
     * Execute a single scenario.
     *
     * @param scenario The scenario to execute
     * @param sharedContext Optional shared context for cross-scenario variable sharing.
     *                      If provided, variables from previous scenarios are available.
     */
    fun execute(
        scenario: Scenario,
        sharedContext: ExecutionContext? = null,
    ): ScenarioResult {
        val startTime = Instant.now()
        // Use shared context if provided, otherwise create a fresh one
        val context = sharedContext?.createChild() ?: ExecutionContext()
        val stepResults = mutableListOf<StepResult>()
        var overallStatus = ResultStatus.PASSED
        var continueExecution = true

        // Create plugin context
        val scenarioContext = ScenarioContextAdapter(scenario, context, startTime)

        // Dispatch plugin: onScenarioStart
        pluginRegistry?.dispatchScenarioStart(scenarioContext)

        var stepIndex = 0

        // Execute background steps first
        for (step in scenario.background) {
            if (!continueExecution) break

            // Expand fragments if needed
            val stepsToExecute = expandStep(step)
            for (expandedStep in stepsToExecute) {
                if (!continueExecution) break
                val result = executeStepWithPlugins(expandedStep, context, scenarioContext, stepIndex++)
                stepResults.add(result)
                if (result.status != ResultStatus.PASSED) {
                    overallStatus = result.status
                    continueExecution = false
                }
            }
        }

        // Execute scenario steps
        for (step in scenario.steps) {
            if (!continueExecution) {
                stepResults.add(
                    StepResult(
                        step = step,
                        status = ResultStatus.SKIPPED,
                    ),
                )
                continue
            }

            // Expand fragments if needed
            val stepsToExecute = expandStep(step)
            for (expandedStep in stepsToExecute) {
                if (!continueExecution) {
                    stepResults.add(
                        StepResult(
                            step = expandedStep,
                            status = ResultStatus.SKIPPED,
                        ),
                    )
                    continue
                }

                val result = executeStepWithPlugins(expandedStep, context, scenarioContext, stepIndex++)
                stepResults.add(result)

                if (result.status != ResultStatus.PASSED) {
                    overallStatus = result.status
                    continueExecution = false
                }
            }
        }

        val duration = Duration.between(startTime, Instant.now())

        val scenarioResult =
            ScenarioResult(
                scenario = scenario,
                status = overallStatus,
                stepResults = stepResults,
                startTime = startTime,
                duration = duration,
            )

        // Dispatch plugin: onScenarioEnd
        pluginRegistry?.dispatchScenarioEnd(scenarioContext, ScenarioResultAdapter(scenarioResult))

        // Copy extracted variables back to shared context for cross-scenario sharing
        if (sharedContext != null && scenarioResult.status == ResultStatus.PASSED) {
            context.allVariables().forEach { (name, value) ->
                if (value != null) {
                    sharedContext[name] = value
                }
            }
        }

        return scenarioResult
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
                ?: throw IllegalStateException(
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
        if (step.operationId == null) {
            // If there are assertions or extractions, run them against the last response
            if (step.assertions.isNotEmpty() || step.extractions.isNotEmpty()) {
                val lastResponse =
                    context.lastResponse
                        ?: return StepResult(
                            step = step,
                            status = ResultStatus.ERROR,
                            duration = Duration.between(stepStartTime, Instant.now()),
                            error = IllegalStateException("No previous response to run assertions/extractions against"),
                        )

                // Run extractions
                val extractedValues = extractValues(lastResponse, step, context)

                // Run assertions
                val assertionResults = runAssertions(lastResponse, step.assertions)
                val allPassed = assertionResults.all { it.passed }

                return StepResult(
                    step = step,
                    status = if (allPassed) ResultStatus.PASSED else ResultStatus.FAILED,
                    statusCode = lastResponse.statusCode(),
                    responseBody = lastResponse.body(),
                    responseHeaders = lastResponse.headers().map(),
                    duration = Duration.between(stepStartTime, Instant.now()),
                    extractedValues = extractedValues,
                    assertionResults = assertionResults,
                )
            }

            // No operation and no assertions - just pass
            return StepResult(
                step = step,
                status = ResultStatus.PASSED,
                duration = Duration.between(stepStartTime, Instant.now()),
            )
        }

        try {
            // Resolve the operation
            val (spec, resolvedOp) = specRegistry.resolve(step.operationId, step.specName)

            // Build the URL
            val baseUrl = configuration.baseUrl ?: spec.baseUrl
            val url =
                httpBuilder.buildUrl(
                    baseUrl = baseUrl,
                    path = resolvedOp.path,
                    pathParams = resolveParams(step.pathParams, context),
                    queryParams = resolveParams(step.queryParams, context),
                )

            // Merge headers
            val headers = mutableMapOf<String, String>()
            headers.putAll(configuration.defaultHeaders)
            headers.putAll(spec.defaultHeaders)
            headers.putAll(step.headers)

            // Resolve body with variable interpolation
            val body = step.body?.let { context.interpolate(it) }

            // Execute the HTTP request
            val response =
                httpBuilder.execute(
                    method = resolvedOp.method,
                    url = url,
                    headers = headers,
                    body = body,
                )

            // Update context with response
            context.updateLastResponse(response)

            // Extract values
            val extractedValues = extractValues(response, step, context)

            // Run assertions
            val assertionResults = runAssertions(response, step.assertions)

            val allPassed = assertionResults.all { it.passed }
            val stepDuration = Duration.between(stepStartTime, Instant.now())

            return StepResult(
                step = step,
                status = if (allPassed) ResultStatus.PASSED else ResultStatus.FAILED,
                statusCode = response.statusCode(),
                responseBody = response.body(),
                responseHeaders = response.headers().map(),
                duration = stepDuration,
                extractedValues = extractedValues,
                assertionResults = assertionResults,
            )
        } catch (e: Exception) {
            return StepResult(
                step = step,
                status = ResultStatus.ERROR,
                duration = Duration.between(stepStartTime, Instant.now()),
                error = e,
            )
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
    ): Map<String, Any?> {
        val extracted = mutableMapOf<String, Any?>()

        for (extraction in step.extractions) {
            try {
                val body = response.body() ?: ""
                val value = JsonPath.read<Any>(body, extraction.jsonPath)
                context[extraction.variableName] = value
                extracted[extraction.variableName] = value
            } catch (_: Exception) {
                // Extraction failed - store null
                extracted[extraction.variableName] = null
            }
        }

        return extracted
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

        return try {
            val actual = JsonPath.read<Any>(body, jsonPath)
            val passed = actual == expected

            AssertionResult(
                assertion = assertion,
                passed = passed,
                message = if (passed) "$jsonPath equals $expected" else "Expected $expected at $jsonPath but got $actual",
                actual = actual,
            )
        } catch (e: Exception) {
            AssertionResult(
                assertion = assertion,
                passed = false,
                message = "Failed to evaluate JSONPath '$jsonPath': ${e.message}",
                actual = null,
            )
        }
    }

    private fun assertBodyMatches(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"
        val pattern = assertion.pattern ?: ""

        return try {
            val actual = JsonPath.read<Any>(body, jsonPath)?.toString() ?: ""
            val regex = Regex(pattern)
            val passed = regex.containsMatchIn(actual)

            AssertionResult(
                assertion = assertion,
                passed = passed,
                message = if (passed) "$jsonPath matches pattern" else "Value at $jsonPath does not match pattern '$pattern'",
                actual = actual,
            )
        } catch (e: Exception) {
            AssertionResult(
                assertion = assertion,
                passed = false,
                message = "Failed to evaluate: ${e.message}",
                actual = null,
            )
        }
    }

    private fun assertBodyArraySize(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"
        val expected = assertion.expected as? Int ?: 0

        return try {
            val array = JsonPath.read<List<*>>(body, jsonPath)
            val actual = array.size
            val passed = actual == expected

            AssertionResult(
                assertion = assertion,
                passed = passed,
                message = if (passed) "Array size is $actual" else "Expected array size $expected but got $actual",
                actual = actual,
            )
        } catch (e: Exception) {
            AssertionResult(
                assertion = assertion,
                passed = false,
                message = "Failed to evaluate array at '$jsonPath': ${e.message}",
                actual = null,
            )
        }
    }

    private fun assertBodyArrayNotEmpty(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"

        return try {
            val array = JsonPath.read<List<*>>(body, jsonPath)
            val passed = array.isNotEmpty()

            AssertionResult(
                assertion = assertion,
                passed = passed,
                message = if (passed) "Array is not empty (size: ${array.size})" else "Array at $jsonPath is empty",
                actual = array.size,
            )
        } catch (e: Exception) {
            AssertionResult(
                assertion = assertion,
                passed = false,
                message = "Failed to evaluate array at '$jsonPath': ${e.message}",
                actual = null,
            )
        }
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
