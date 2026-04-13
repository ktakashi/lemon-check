package io.github.ktakashi.lemoncheck.executor

import com.jayway.jsonpath.JsonPath
import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.context.ExecutionContext
import io.github.ktakashi.lemoncheck.exception.ConfigurationException
import io.github.ktakashi.lemoncheck.model.Assertion
import io.github.ktakashi.lemoncheck.model.AssertionResult
import io.github.ktakashi.lemoncheck.model.AssertionType
import io.github.ktakashi.lemoncheck.model.BodyProperty
import io.github.ktakashi.lemoncheck.model.Condition
import io.github.ktakashi.lemoncheck.model.ConditionOperator
import io.github.ktakashi.lemoncheck.model.ConditionalActions
import io.github.ktakashi.lemoncheck.model.ConditionalAssertion
import io.github.ktakashi.lemoncheck.model.FragmentRegistry
import io.github.ktakashi.lemoncheck.model.LogicalOperator
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.ScenarioResult
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepResult
import io.github.ktakashi.lemoncheck.openapi.HttpMethod
import io.github.ktakashi.lemoncheck.openapi.ResolvedOperation
import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
import io.github.ktakashi.lemoncheck.plugin.PluginRegistry
import io.github.ktakashi.lemoncheck.plugin.adapter.ScenarioContextAdapter
import io.github.ktakashi.lemoncheck.plugin.adapter.ScenarioResultAdapter
import io.github.ktakashi.lemoncheck.plugin.adapter.StepContextAdapter
import io.github.ktakashi.lemoncheck.plugin.adapter.StepResultAdapter
import io.github.ktakashi.lemoncheck.util.FileLoader
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

private val objectMapper = ObjectMapper()

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

    // Current execution listener for the executing scenario
    // Thread-local to support concurrent execution
    private val currentExecutionListener = ThreadLocal<ExecutionListener>()

    // Lazy-initialized auto-test executor - created on first use to avoid circular dependencies
    private val autoTestExecutor: AutoTestExecutor by lazy {
        AutoTestExecutor(
            specRegistry = specRegistry,
            configuration = configuration,
            httpBuilder = httpBuilder,
            assertionRunner = ::runAssertions,
            paramResolver = ::resolveParams,
            requestLogger = { method, url, headers, body ->
                logRequest(HttpMethod.valueOf(method), url, headers, body)
            },
            responseLogger = { method, url, response, startTime ->
                logResponse(HttpMethod.valueOf(method), url, response, startTime)
            },
        )
    }

    /**
     * Execute a single scenario.
     *
     * @param scenario The scenario to execute
     * @param sharedContext Optional shared context for cross-scenario variable sharing.
     *                      If provided, variables from previous scenarios are available.
     * @param sourceFile Optional source file for the scenario (used in reports for grouping).
     * @param executionListener Optional listener for execution events (scenario, step, auto-test).
     *                          Used by frameworks (like JUnit) to receive real-time notifications.
     */
    fun execute(
        scenario: Scenario,
        sharedContext: ExecutionContext? = null,
        sourceFile: java.io.File? = null,
        executionListener: ExecutionListener? = null,
    ): ScenarioResult {
        // Set the listener for this execution (thread-local for concurrent safety)
        val listener = executionListener ?: ExecutionListener.NOOP
        currentExecutionListener.set(listener)

        try {
            // Notify listener that scenario is starting
            listener.onScenarioStarting(scenario)

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

            // Notify listener that scenario completed
            listener.onScenarioCompleted(scenario, scenarioResult)

            return scenarioResult
        } finally {
            // Clean up thread-local
            currentExecutionListener.remove()
        }
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
        // Execute background steps
        var continueExecution =
            executeStepsWithContinuation(
                scenario.background,
                context,
                scenarioContext,
                stepResults,
                stepIndex,
                true,
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
        // Get current execution listener
        val listener = currentExecutionListener.get() ?: ExecutionListener.NOOP

        // Create step context
        val stepContext = StepContextAdapter(step, stepIndex, scenarioContext)

        // Notify listener that step is starting
        listener.onStepStarting(step)

        // Dispatch plugin: onStepStart
        pluginRegistry?.dispatchStepStart(stepContext)

        // Execute the actual step
        val result = executeStep(step, context)

        // Dispatch plugin: onStepEnd
        pluginRegistry?.dispatchStepEnd(stepContext, StepResultAdapter(result))

        // Notify listener that step completed
        listener.onStepCompleted(step, result)

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
            // Check if this step has auto-test configuration
            if (step.autoTestConfig != null) {
                val listener = currentExecutionListener.get() ?: ExecutionListener.NOOP
                autoTestExecutor.executeAutoTests(step, context, stepStartTime, listener)
            } else {
                executeHttpRequest(step, context, stepStartTime)
            }
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

        // Resolve body: prefer inline body, structured properties, or fall back to bodyFile
        val body = resolveBody(step, context, resolvedOp)

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
        // Check for unconditional fail
        if (step.failMessage != null) {
            return StepResult(
                step = step,
                status = ResultStatus.FAILED,
                statusCode = response.statusCode(),
                responseBody = response.body(),
                responseHeaders = response.headers().map(),
                duration = Duration.between(stepStartTime, Instant.now()),
                error = AssertionError(step.failMessage),
            )
        }

        val extractedValues = extractValues(response, step, context)
        val assertionResults = runAssertions(response, step.assertions, context).toMutableList()

        // Run conditional assertions
        val conditionalResults = runConditionals(response, step.conditionals, context)
        assertionResults.addAll(conditionalResults.assertionResults)

        // Check for conditional fail
        if (conditionalResults.failMessage != null) {
            return StepResult(
                step = step,
                status = ResultStatus.FAILED,
                statusCode = response.statusCode(),
                responseBody = response.body(),
                responseHeaders = response.headers().map(),
                duration = Duration.between(stepStartTime, Instant.now()),
                extractedValues = extractedValues + conditionalResults.extractedValues,
                assertionResults = assertionResults,
                error = AssertionError(conditionalResults.failMessage),
            )
        }

        val allPassed = assertionResults.all { it.passed }

        return StepResult(
            step = step,
            status = if (allPassed) ResultStatus.PASSED else ResultStatus.FAILED,
            statusCode = response.statusCode(),
            responseBody = response.body(),
            responseHeaders = response.headers().map(),
            duration = Duration.between(stepStartTime, Instant.now()),
            extractedValues = extractedValues + conditionalResults.extractedValues,
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

    /**
     * Resolve the request body from either inline body, structured properties, or external file.
     *
     * Priority:
     * 1. Inline body (step.body) takes precedence
     * 2. Structured body properties (step.bodyProperties) merged with schema defaults
     * 3. External file (step.bodyFile) is used as fallback
     *
     * Variable interpolation is applied to the final body content.
     */
    private fun resolveBody(
        step: Step,
        context: ExecutionContext,
        resolvedOp: ResolvedOperation? = null,
    ): String? {
        // Inline body takes precedence
        step.body?.let { return context.interpolate(it) }

        // Structured body properties - generate from schema and merge
        step.bodyProperties?.let { props ->
            val bodyJson = generateBodyFromProperties(props, resolvedOp, context)
            return bodyJson
        }

        // Fall back to body file
        return step.bodyFile?.let { path ->
            val content = FileLoader.load(path)
            context.interpolate(content)
        }
    }

    /**
     * Generate JSON body from structured properties and OpenAPI schema defaults.
     */
    private fun generateBodyFromProperties(
        props: Map<String, BodyProperty>,
        resolvedOp: ResolvedOperation?,
        context: ExecutionContext,
    ): String {
        // Get schema defaults from OpenAPI spec
        val schemaDefaults = resolvedOp?.let { getSchemaDefaults(it) } ?: emptyMap()

        // Merge schema defaults with user-provided properties (user wins)
        val merged = mergeBodyProperties(schemaDefaults, props)

        // Convert to JSON and interpolate variables
        val json = bodyPropertyToJson(merged)
        return context.interpolate(json)
    }

    /**
     * Extract default values from OpenAPI requestBody schema.
     */
    private fun getSchemaDefaults(resolvedOp: ResolvedOperation): Map<String, BodyProperty> {
        val requestBody = resolvedOp.operation.requestBody ?: return emptyMap()
        val content = requestBody.content ?: return emptyMap()

        // Prefer application/json schema
        val mediaType = content["application/json"] ?: content.values.firstOrNull() ?: return emptyMap()
        val schema = mediaType.schema ?: return emptyMap()

        return extractPropertiesFromSchema(schema)
    }

    /**
     * Extract default properties from an OpenAPI schema.
     */
    private fun extractPropertiesFromSchema(schema: io.swagger.v3.oas.models.media.Schema<*>): Map<String, BodyProperty> {
        val result = mutableMapOf<String, BodyProperty>()

        schema.properties?.forEach { (name, propSchema) ->
            val defaultValue = getSchemaDefaultValue(propSchema)
            if (defaultValue != null) {
                result[name] = defaultValue
            }
        }

        return result
    }

    /**
     * Get a default value for a schema property.
     */
    private fun getSchemaDefaultValue(schema: io.swagger.v3.oas.models.media.Schema<*>): BodyProperty? {
        // Use explicit default if provided
        schema.default?.let { return BodyProperty.Simple(it) }

        // Use example if provided
        schema.example?.let { return BodyProperty.Simple(it) }

        // Generate a sensible default based on type
        return when (schema.type) {
            "string" -> BodyProperty.Simple("")
            "integer", "number" -> BodyProperty.Simple(0)
            "boolean" -> BodyProperty.Simple(false)
            "array" -> BodyProperty.Simple(emptyList<Any>())
            "object" -> {
                val nestedProps = extractPropertiesFromSchema(schema)
                if (nestedProps.isNotEmpty()) {
                    BodyProperty.Nested(nestedProps)
                } else {
                    BodyProperty.Simple(emptyMap<String, Any>())
                }
            }
            else -> null
        }
    }

    /**
     * Merge schema defaults with user-provided properties.
     * User properties override schema defaults.
     */
    private fun mergeBodyProperties(
        defaults: Map<String, BodyProperty>,
        userProps: Map<String, BodyProperty>,
    ): Map<String, BodyProperty> {
        val result = defaults.toMutableMap()

        userProps.forEach { (key, value) ->
            val existing = result[key]
            if (existing is BodyProperty.Nested && value is BodyProperty.Nested) {
                // Deep merge nested properties
                result[key] = BodyProperty.Nested(mergeBodyProperties(existing.properties, value.properties))
            } else {
                // User property overrides
                result[key] = value
            }
        }

        return result
    }

    /**
     * Convert BodyProperty map to JSON string.
     */
    private fun bodyPropertyToJson(props: Map<String, BodyProperty>): String {
        val jsonMap = props.mapValues { (_, prop) -> bodyPropertyToJsonValue(prop) }
        return objectMapper.writeValueAsString(jsonMap)
    }

    private fun bodyPropertyToJsonValue(prop: BodyProperty): Any =
        when (prop) {
            is BodyProperty.Simple -> prop.value
            is BodyProperty.Nested -> prop.properties.mapValues { (_, p) -> bodyPropertyToJsonValue(p) }
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
        context: ExecutionContext,
    ): List<AssertionResult> = assertions.map { assertion -> runAssertion(response, assertion, context) }

    /**
     * Result of running conditional assertions.
     */
    private data class ConditionalRunResult(
        val assertionResults: List<AssertionResult> = emptyList(),
        val extractedValues: Map<String, Any?> = emptyMap(),
        val failMessage: String? = null,
    )

    /**
     * Run conditional assertions against the response.
     *
     * Evaluates each conditional's conditions in order and runs the actions
     * for the first matching branch.
     */
    private fun runConditionals(
        response: HttpResponse<String>,
        conditionals: List<ConditionalAssertion>,
        context: ExecutionContext,
    ): ConditionalRunResult {
        val allResults = mutableListOf<AssertionResult>()
        val allExtracted = mutableMapOf<String, Any?>()
        var failMessage: String? = null

        for (conditional in conditionals) {
            val result = runConditional(response, conditional, context)
            allResults.addAll(result.assertionResults)
            allExtracted.putAll(result.extractedValues)
            if (result.failMessage != null) {
                failMessage = result.failMessage
                break // Stop on first fail
            }
        }

        return ConditionalRunResult(
            assertionResults = allResults,
            extractedValues = allExtracted,
            failMessage = failMessage,
        )
    }

    /**
     * Run a single conditional assertion.
     */
    private fun runConditional(
        response: HttpResponse<String>,
        conditional: ConditionalAssertion,
        context: ExecutionContext,
    ): ConditionalRunResult {
        // Try if branch
        if (evaluateCondition(response, conditional.ifBranch.condition, context)) {
            return runConditionalActions(response, conditional.ifBranch.actions, context)
        }

        // Try else-if branches
        for (elseIfBranch in conditional.elseIfBranches) {
            if (evaluateCondition(response, elseIfBranch.condition, context)) {
                return runConditionalActions(response, elseIfBranch.actions, context)
            }
        }

        // Run else branch if present
        if (conditional.elseActions != null) {
            return runConditionalActions(response, conditional.elseActions, context)
        }

        // No branch matched - that's OK, no assertions to run
        return ConditionalRunResult()
    }

    /**
     * Run actions within a conditional branch.
     */
    private fun runConditionalActions(
        response: HttpResponse<String>,
        actions: ConditionalActions,
        context: ExecutionContext,
    ): ConditionalRunResult {
        // Check for fail first
        if (actions.failMessage != null) {
            return ConditionalRunResult(failMessage = actions.failMessage)
        }

        val assertionResults = mutableListOf<AssertionResult>()
        val extractedValues = mutableMapOf<String, Any?>()

        // Run extractions
        for (extraction in actions.extractions) {
            val value =
                runCatching {
                    val body = response.body() ?: ""
                    JsonPath.read<Any>(body, extraction.jsonPath).also {
                        context[extraction.variableName] = it
                    }
                }.getOrNull()
            extractedValues[extraction.variableName] = value
        }

        // Run assertions
        assertionResults.addAll(runAssertions(response, actions.assertions, context))

        // Run nested conditionals
        for (nested in actions.nestedConditionals) {
            val nestedResult = runConditional(response, nested, context)
            assertionResults.addAll(nestedResult.assertionResults)
            extractedValues.putAll(nestedResult.extractedValues)
            if (nestedResult.failMessage != null) {
                return ConditionalRunResult(
                    assertionResults = assertionResults,
                    extractedValues = extractedValues,
                    failMessage = nestedResult.failMessage,
                )
            }
        }

        return ConditionalRunResult(
            assertionResults = assertionResults,
            extractedValues = extractedValues,
        )
    }

    /**
     * Evaluate a condition against the response.
     */
    private fun evaluateCondition(
        response: HttpResponse<String>,
        condition: Condition,
        context: ExecutionContext,
    ): Boolean =
        when (condition) {
            is Condition.Status -> evaluateStatusCondition(response, condition)
            is Condition.JsonPath -> evaluateJsonPathCondition(response, condition, context)
            is Condition.Header -> evaluateHeaderCondition(response, condition, context)
            is Condition.Variable -> evaluateVariableCondition(condition, context)
            is Condition.Negated -> !evaluateCondition(response, condition.condition, context)
            is Condition.Compound -> {
                val leftResult = evaluateCondition(response, condition.left, context)
                when (condition.operator) {
                    LogicalOperator.AND -> leftResult && evaluateCondition(response, condition.right, context)
                    LogicalOperator.OR -> leftResult || evaluateCondition(response, condition.right, context)
                }
            }
        }

    /**
     * Evaluate a variable condition.
     */
    private fun evaluateVariableCondition(
        condition: Condition.Variable,
        context: ExecutionContext,
    ): Boolean {
        val actual: Any? = context.get<Any>(condition.name)
        val expected = condition.expected

        return when (condition.operator) {
            ConditionOperator.EQUALS -> actual?.toString() == expected?.toString()
            ConditionOperator.NOT_EQUALS -> actual?.toString() != expected?.toString()
            ConditionOperator.CONTAINS -> actual?.toString()?.contains(expected?.toString() ?: "") == true
            ConditionOperator.NOT_CONTAINS -> actual?.toString()?.contains(expected?.toString() ?: "") != true
            ConditionOperator.MATCHES -> {
                val pattern = expected?.toString() ?: ""
                actual?.toString()?.matches(pattern.toRegex()) == true
            }
            ConditionOperator.EXISTS -> actual != null
            ConditionOperator.NOT_EXISTS -> actual == null
            ConditionOperator.GREATER_THAN -> {
                val actualNum = (actual as? Number)?.toDouble() ?: actual?.toString()?.toDoubleOrNull() ?: return false
                val expectedNum = (expected as? Number)?.toDouble() ?: expected?.toString()?.toDoubleOrNull() ?: return false
                actualNum > expectedNum
            }
            ConditionOperator.LESS_THAN -> {
                val actualNum = (actual as? Number)?.toDouble() ?: actual?.toString()?.toDoubleOrNull() ?: return false
                val expectedNum = (expected as? Number)?.toDouble() ?: expected?.toString()?.toDoubleOrNull() ?: return false
                actualNum < expectedNum
            }
        }
    }

    /**
     * Evaluate a status code condition.
     */
    private fun evaluateStatusCondition(
        response: HttpResponse<String>,
        condition: Condition.Status,
    ): Boolean {
        val actual = response.statusCode()
        return when (val expected = condition.expected) {
            is Number -> actual == expected.toInt()
            is IntRange -> actual in expected
            is String -> {
                // Handle patterns like "2xx", "20x", "200-299"
                val pattern = expected.lowercase()
                when {
                    pattern.contains('-') -> {
                        val (start, end) = pattern.split('-').map { it.trim().toIntOrNull() }
                        if (start != null && end != null) actual in start..end else false
                    }
                    pattern.contains('x') -> {
                        val regex = pattern.replace("x", "\\d").toRegex()
                        actual.toString().matches(regex)
                    }
                    else -> expected.toIntOrNull()?.let { actual == it } ?: false
                }
            }
            else -> false
        }
    }

    /**
     * Evaluate a JSON path condition.
     */
    private fun evaluateJsonPathCondition(
        response: HttpResponse<String>,
        condition: Condition.JsonPath,
        context: ExecutionContext,
    ): Boolean {
        val body = response.body() ?: ""
        val actualValue = runCatching { JsonPath.read<Any>(body, condition.path) }.getOrNull()
        val expectedValue = condition.expected?.let { resolveConditionValue(it, context) }

        return when (condition.operator) {
            ConditionOperator.EXISTS -> actualValue != null
            ConditionOperator.NOT_EXISTS -> actualValue == null
            ConditionOperator.EQUALS -> actualValue == expectedValue || actualValue?.toString() == expectedValue?.toString()
            ConditionOperator.NOT_EQUALS -> actualValue != expectedValue && actualValue?.toString() != expectedValue?.toString()
            ConditionOperator.CONTAINS -> {
                when (actualValue) {
                    is String -> actualValue.contains(expectedValue?.toString() ?: "")
                    is Collection<*> -> actualValue.contains(expectedValue)
                    else -> false
                }
            }
            ConditionOperator.NOT_CONTAINS -> {
                when (actualValue) {
                    is String -> !actualValue.contains(expectedValue?.toString() ?: "")
                    is Collection<*> -> !actualValue.contains(expectedValue)
                    else -> true
                }
            }
            ConditionOperator.MATCHES -> {
                val pattern = expectedValue?.toString() ?: ""
                actualValue?.toString()?.matches(pattern.toRegex()) ?: false
            }
            ConditionOperator.GREATER_THAN -> {
                val actualNum = (actualValue as? Number)?.toDouble()
                val expectedNum = (expectedValue as? Number)?.toDouble()
                if (actualNum != null && expectedNum != null) actualNum > expectedNum else false
            }
            ConditionOperator.LESS_THAN -> {
                val actualNum = (actualValue as? Number)?.toDouble()
                val expectedNum = (expectedValue as? Number)?.toDouble()
                if (actualNum != null && expectedNum != null) actualNum < expectedNum else false
            }
        }
    }

    /**
     * Evaluate a header condition.
     */
    private fun evaluateHeaderCondition(
        response: HttpResponse<String>,
        condition: Condition.Header,
        context: ExecutionContext,
    ): Boolean {
        val headerValues = response.headers().allValues(condition.name)
        val actualValue = headerValues.firstOrNull()
        val expectedValue = condition.expected?.let { resolveConditionValue(it, context) }

        return when (condition.operator) {
            ConditionOperator.EXISTS -> headerValues.isNotEmpty()
            ConditionOperator.NOT_EXISTS -> headerValues.isEmpty()
            ConditionOperator.EQUALS -> actualValue == expectedValue?.toString()
            ConditionOperator.NOT_EQUALS -> actualValue != expectedValue?.toString()
            ConditionOperator.CONTAINS -> actualValue?.contains(expectedValue?.toString() ?: "") ?: false
            ConditionOperator.NOT_CONTAINS -> !(actualValue?.contains(expectedValue?.toString() ?: "") ?: false)
            ConditionOperator.MATCHES -> actualValue?.matches((expectedValue?.toString() ?: "").toRegex()) ?: false
            ConditionOperator.GREATER_THAN, ConditionOperator.LESS_THAN -> false // Not applicable for headers
        }
    }

    /**
     * Resolve condition value, handling variable interpolation.
     */
    private fun resolveConditionValue(
        value: Any,
        context: ExecutionContext,
    ): Any =
        when (value) {
            is String -> context.interpolate(value)
            else -> value
        }

    private fun runAssertion(
        response: HttpResponse<String>,
        assertion: Assertion,
        context: ExecutionContext,
    ): AssertionResult {
        val baseResult =
            when (assertion.type) {
                AssertionType.STATUS_CODE -> assertStatusCode(response, assertion)
                AssertionType.BODY_CONTAINS -> assertBodyContains(response, assertion, context)
                AssertionType.BODY_EQUALS -> assertBodyEquals(response, assertion, context)
                AssertionType.BODY_MATCHES -> assertBodyMatches(response, assertion, context)
                AssertionType.BODY_ARRAY_SIZE -> assertBodyArraySize(response, assertion)
                AssertionType.BODY_ARRAY_NOT_EMPTY -> assertBodyArrayNotEmpty(response, assertion)
                AssertionType.HEADER_EXISTS -> assertHeaderExists(response, assertion)
                AssertionType.HEADER_EQUALS -> assertHeaderEquals(response, assertion, context)
                AssertionType.MATCHES_SCHEMA -> AssertionResult(assertion, true, "Schema validation not implemented yet")
                AssertionType.RESPONSE_TIME -> AssertionResult(assertion, true, "Response time assertion not implemented yet")
            }

        // Apply negation if the assertion has negate flag
        return if (assertion.negate) {
            baseResult.copy(
                passed = !baseResult.passed,
                message =
                    if (!baseResult.passed) {
                        // Was failed, now passes with negation
                        "NOT: ${baseResult.message}"
                    } else {
                        // Was passed, now fails with negation - need to show failure message
                        "Negated assertion failed: expected condition to NOT be true\n${baseResult.message}"
                    },
            )
        } else {
            baseResult
        }
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
        context: ExecutionContext,
    ): AssertionResult {
        val body = response.body() ?: ""
        val rawSubstring = assertion.expected as? String ?: ""
        val substring = context.interpolate(rawSubstring)
        val passed = body.contains(substring)

        val message =
            if (passed) {
                "Body contains '$substring'"
            } else {
                buildString {
                    append("Assertion failed: body does not contain expected string\n")
                    append("  Expected to find: '$substring'\n")
                    append("  Actual body (first 200 chars): ${body.take(200)}")
                }
            }

        return AssertionResult(
            assertion = assertion,
            passed = passed,
            message = message,
            actual = body.take(200),
        )
    }

    private fun assertBodyEquals(
        response: HttpResponse<String>,
        assertion: Assertion,
        context: ExecutionContext,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"
        // Interpolate expected value if it's a string
        val expected =
            when (val rawExpected = assertion.expected) {
                is String -> context.interpolate(rawExpected)
                else -> rawExpected
            }

        return runCatching { JsonPath.read<Any>(body, jsonPath) }
            .fold(
                onSuccess = { actual ->
                    val passed = valuesEqual(actual, expected)
                    val message =
                        if (passed) {
                            "$jsonPath equals $expected"
                        } else {
                            buildString {
                                append("Assertion failed at $jsonPath\n")
                                append("  Expected: $expected\n")
                                append("  Actual:   $actual")
                            }
                        }
                    AssertionResult(
                        assertion = assertion,
                        passed = passed,
                        message = message,
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
        context: ExecutionContext,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"
        val rawPattern = assertion.pattern ?: ""
        val pattern = context.interpolate(rawPattern)

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
        context: ExecutionContext,
    ): AssertionResult {
        val headerName = assertion.headerName ?: ""
        val rawExpected = assertion.expected as? String ?: ""
        val expected = context.interpolate(rawExpected)
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
