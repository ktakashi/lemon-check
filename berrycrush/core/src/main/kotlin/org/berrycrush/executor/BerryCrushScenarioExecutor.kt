package org.berrycrush.executor

import com.jayway.jsonpath.JsonPath
import org.berrycrush.assertion.SchemaValidator
import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.context.ExecutionContext
import org.berrycrush.exception.ConfigurationException
import org.berrycrush.model.Assertion
import org.berrycrush.model.AssertionResult
import org.berrycrush.model.BodyProperty
import org.berrycrush.model.Condition
import org.berrycrush.model.ConditionOperator
import org.berrycrush.model.ConditionalActions
import org.berrycrush.model.ConditionalAssertion
import org.berrycrush.model.FragmentRegistry
import org.berrycrush.model.LogicalOperator
import org.berrycrush.model.ResultStatus
import org.berrycrush.model.Scenario
import org.berrycrush.model.ScenarioResult
import org.berrycrush.model.Step
import org.berrycrush.model.StepResult
import org.berrycrush.openapi.HttpMethod
import org.berrycrush.openapi.ResolvedOperation
import org.berrycrush.openapi.SpecRegistry
import org.berrycrush.openapi.findResponse
import org.berrycrush.plugin.PluginRegistry
import org.berrycrush.plugin.adapter.ScenarioContextAdapter
import org.berrycrush.plugin.adapter.ScenarioResultAdapter
import org.berrycrush.plugin.adapter.StepContextAdapter
import org.berrycrush.plugin.adapter.StepResultAdapter
import org.berrycrush.step.StepContext
import org.berrycrush.step.StepContextImpl
import org.berrycrush.step.StepMatch
import org.berrycrush.step.StepRegistry
import org.berrycrush.util.FileLoader
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

private val objectMapper = ObjectMapper()
private val schemaValidator = SchemaValidator(objectMapper)

/**
 * Executes BDD scenarios against API endpoints.
 *
 * @property specRegistry Registry for OpenAPI specifications
 * @property configuration Execution configuration
 * @property pluginRegistry Optional plugin registry for lifecycle hooks
 * @property fragmentRegistry Optional registry for reusable fragments
 * @property stepRegistry Optional registry for custom step definitions
 * @property assertionRegistry Optional registry for custom assertion definitions
 */
class BerryCrushScenarioExecutor(
    private val specRegistry: SpecRegistry,
    private val configuration: BerryCrushConfiguration,
    private val pluginRegistry: PluginRegistry? = null,
    private val fragmentRegistry: FragmentRegistry? = null,
    private val stepRegistry: StepRegistry? = null,
    private val assertionRegistry: org.berrycrush.assertion.AssertionRegistry? = null,
) {
    private val httpBuilder = HttpRequestBuilder(configuration)

    // Current execution listener for the executing scenario
    // Thread-local to support concurrent execution
    private val currentExecutionListener = ThreadLocal<BerryCrushExecutionListener>()

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
        executionListener: BerryCrushExecutionListener? = null,
    ): ScenarioResult {
        // Set the listener for this execution (thread-local for concurrent safety)
        val listener = executionListener ?: BerryCrushExecutionListener.NOOP
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
        val listener = currentExecutionListener.get() ?: BerryCrushExecutionListener.NOOP

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

        // If no operation to call, check for custom step or assertions
        return step.operationId?.let {
            executeOperationStep(step, context, stepStartTime)
        } ?: executeNonOperationStep(step, context, stepStartTime)
    }

    /**
     * Execute a step that has no operationId.
     *
     * Checks in order:
     * 1. Custom step definition match
     * 2. Assertions/extractions against last response
     * 3. No-op (just pass)
     */
    private fun executeNonOperationStep(
        step: Step,
        context: ExecutionContext,
        stepStartTime: Instant,
    ): StepResult {
        // First, check if this is a custom step
        stepRegistry?.let { registry ->
            val resolvedDescription = resolveVariables(step.description, context)
            val match = registry.findMatch(resolvedDescription)
            if (match != null) {
                return executeCustomStep(step, match, context, stepStartTime)
            }
        }

        // Not a custom step - check for assertions/extractions
        return if (step.assertions.isEmpty() && step.extractions.isEmpty()) {
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
    }

    /**
     * Execute a custom step definition.
     */
    private fun executeCustomStep(
        step: Step,
        match: StepMatch,
        context: ExecutionContext,
        stepStartTime: Instant,
    ): StepResult =
        runCatching {
            val stepContext =
                StepContextImpl(
                    executionContext = context,
                    configuration = configuration,
                    sharedVariables = null, // TODO: Add shared variables support
                    sharingEnabled = false,
                )

            // Invoke the custom step method with extracted parameters and context
            val method = match.definition.method
            val parameters = match.parameters.toTypedArray()

            // Check if method accepts StepContext as last parameter
            val methodParams = method.parameters
            val args =
                if (methodParams.isNotEmpty() &&
                    methodParams.last().type.isAssignableFrom(StepContext::class.java)
                ) {
                    // Append StepContext to parameters
                    arrayOf(*parameters, stepContext)
                } else {
                    parameters
                }

            // Invoke the method
            val result = method.invoke(match.definition.instance, *args)

            // Check if the method returned a StepResult
            if (result is StepResult) {
                // Ensure custom step flag is set
                result.copy(isCustomStep = true)
            } else {
                StepResult(
                    step = step,
                    status = ResultStatus.PASSED,
                    duration = Duration.between(stepStartTime, Instant.now()),
                    isCustomStep = true,
                )
            }
        }.getOrElse { e ->
            // Unwrap InvocationTargetException to get the actual exception
            val actualException =
                when (e) {
                    is java.lang.reflect.InvocationTargetException -> e.cause ?: e
                    else -> e
                }

            // Determine status based on exception type
            val status =
                when (actualException) {
                    is AssertionError -> ResultStatus.FAILED
                    else -> ResultStatus.ERROR
                }

            StepResult(
                step = step,
                status = status,
                duration = Duration.between(stepStartTime, Instant.now()),
                error = actualException as? Exception ?: RuntimeException(actualException),
                isCustomStep = true,
            )
        }

    /**
     * Resolve variables in a string.
     */
    private fun resolveVariables(
        text: String,
        context: ExecutionContext,
    ): String {
        val regex = """\{\{(\w+)}}""".toRegex()
        return regex.replace(text) { matchResult ->
            val varName = matchResult.groupValues[1]
            context.get<Any>(varName)?.toString() ?: matchResult.value
        }
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
                val listener = currentExecutionListener.get() ?: BerryCrushExecutionListener.NOOP
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

        // Store the resolved operation for schema validation
        context.updateCurrentOperation(resolvedOp)

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
     * Check if a step contains any custom assertions.
     */
    private fun hasCustomAssertion(step: Step): Boolean {
        // Check direct assertions
        if (step.assertions.any { it.condition is Condition.CustomAssertion }) {
            return true
        }
        // Check conditional assertions
        return step.conditionals.any { conditional ->
            hasCustomAssertionInConditional(conditional)
        }
    }

    /**
     * Check if a conditional contains any custom assertions.
     */
    private fun hasCustomAssertionInConditional(conditional: ConditionalAssertion): Boolean {
        // Check if branch
        if (conditional.ifBranch.actions.assertions
                .any { it.condition is Condition.CustomAssertion }
        ) {
            return true
        }
        // Check else if branches
        if (conditional.elseIfBranches.any { branch ->
                branch.actions.assertions.any { it.condition is Condition.CustomAssertion }
            }
        ) {
            return true
        }
        // Check else actions
        if (conditional.elseActions?.assertions?.any { it.condition is Condition.CustomAssertion } == true) {
            return true
        }
        // Check nested conditionals
        val hasNestedCustom =
            conditional.ifBranch.actions.nestedConditionals
                .any { hasCustomAssertionInConditional(it) } ||
                conditional.elseIfBranches.any { branch ->
                    branch.actions.nestedConditionals.any { hasCustomAssertionInConditional(it) }
                } ||
                (conditional.elseActions?.nestedConditionals?.any { hasCustomAssertionInConditional(it) } == true)
        return hasNestedCustom
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
        val isCustom = hasCustomAssertion(step)

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
                isCustomStep = isCustom,
            )
        }

        val extractedValues = extractValues(response, step, context)
        val assertionResults = runAssertions(response, step.assertions, context).toMutableList()

        // Run conditional assertions
        val conditionalResults = runConditionals(response, step.conditionals, context)
        assertionResults.addAll(conditionalResults.assertionResults)

        // Run custom assertions (DSL assert blocks)
        val customAssertionResults = runCustomAssertions(response, step.customAssertions, context)
        assertionResults.addAll(customAssertionResults)

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
                isCustomStep = isCustom,
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
            isCustomStep = isCustom,
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
        val content = requestBody.content
        if (content.isEmpty()) return emptyMap()

        // Prefer application/json schema
        val mediaType = content["application/json"] ?: content.values.firstOrNull() ?: return emptyMap()
        val schema = mediaType.schema ?: return emptyMap()

        return extractPropertiesFromSchemaSpec(schema)
    }

    /**
     * Extract default properties from a SchemaSpec.
     */
    private fun extractPropertiesFromSchemaSpec(schema: org.berrycrush.openapi.SchemaSpec): Map<String, BodyProperty> {
        val result = mutableMapOf<String, BodyProperty>()

        schema.properties?.forEach { (name, propSchema) ->
            val defaultValue = getSchemaSpecDefaultValue(propSchema)
            if (defaultValue != null) {
                result[name] = defaultValue
            }
        }

        return result
    }

    /**
     * Get a default value for a SchemaSpec property.
     */
    private fun getSchemaSpecDefaultValue(schema: org.berrycrush.openapi.SchemaSpec): BodyProperty? {
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
                val nestedProps = extractPropertiesFromSchemaSpec(schema)
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
     * Run custom assertions defined via DSL assert blocks.
     *
     * Custom assertions receive a TestExecutionContext and can throw any exception
     * (including AssertionError from require/check/assert) to indicate failure.
     */
    private fun runCustomAssertions(
        response: HttpResponse<String>,
        customAssertions: List<org.berrycrush.model.CustomAssertionDefinition>,
        context: ExecutionContext,
    ): List<AssertionResult> =
        customAssertions.map { customAssertion ->
            runCustomAssertion(response, customAssertion, context)
        }

    /**
     * Run a single custom assertion.
     */
    private fun runCustomAssertion(
        response: HttpResponse<String>,
        customAssertion: org.berrycrush.model.CustomAssertionDefinition,
        context: ExecutionContext,
    ): AssertionResult {
        val testContext = org.berrycrush.context.MutableTestExecutionContext(context)
        val assertion =
            Assertion(
                condition = Condition.CustomAssertion(customAssertion.description),
                description = customAssertion.description,
            )
        return runCatching {
            customAssertion.assertion(testContext)
            AssertionResult(
                assertion = assertion,
                passed = true,
                message = "Custom assertion passed: ${customAssertion.description}",
            )
        }.getOrElse { e ->
            // Unwrap AssertionFailureException if present
            val actualException =
                when (e) {
                    is org.berrycrush.exception.AssertionFailureException -> e.cause ?: e
                    else -> e
                }
            AssertionResult(
                assertion = assertion,
                passed = false,
                message = actualException.message ?: "Custom assertion failed: ${customAssertion.description}",
                actual = actualException.message,
            )
        }
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
            is Condition.BodyContains -> evaluateBodyContainsCondition(response, condition, context)
            is Condition.Schema -> evaluateSchemaCondition(response, context)
            is Condition.ResponseTime -> evaluateResponseTimeCondition(response, condition, context)
            is Condition.CustomAssertion -> evaluateCustomAssertionCondition(response, condition, context)
            is Condition.Custom -> evaluateCustomPredicateCondition(response, condition, context)
        }

    /**
     * Evaluate a custom assertion by invoking the matching assertion from the registry.
     */
    private fun evaluateCustomAssertionCondition(
        response: HttpResponse<String>,
        condition: Condition.CustomAssertion,
        context: ExecutionContext,
    ): Boolean {
        val registry = assertionRegistry ?: return false

        val match = registry.findMatch(condition.pattern) ?: return false

        val assertionContext =
            org.berrycrush.assertion.AssertionContextImpl(
                executionContext = context,
                configuration = configuration,
                sharedVariables = null,
                sharingEnabled = false,
            )

        return runCatching {
            val method = match.definition.method
            val parameters = match.parameters.toTypedArray()

            // Check if method accepts AssertionContext as last parameter
            val methodParams = method.parameters
            val args =
                if (methodParams.isNotEmpty() &&
                    methodParams.last().type.isAssignableFrom(org.berrycrush.assertion.AssertionContext::class.java)
                ) {
                    arrayOf(*parameters, assertionContext)
                } else {
                    parameters
                }

            val result = method.invoke(match.definition.instance, *args)

            when (result) {
                is org.berrycrush.assertion.AssertionResult -> result.passed
                is Boolean -> result
                else -> true // Assume passed if no AssertionResult returned
            }
        }.getOrElse { e ->
            // Unwrap InvocationTargetException
            val actualException =
                when (e) {
                    is java.lang.reflect.InvocationTargetException -> e.cause ?: e
                    else -> e
                }

            // AssertionError means the assertion failed
            actualException !is AssertionError
        }
    }

    /**
     * Evaluate a custom predicate condition (from DSL conditional).
     */
    private fun evaluateCustomPredicateCondition(
        response: HttpResponse<String>,
        condition: Condition.Custom,
        context: ExecutionContext,
    ): Boolean {
        // Note: lastResponse should already be set by the executor before evaluating conditions
        val testContext = org.berrycrush.context.MutableTestExecutionContext(context)
        return runCatching {
            condition.predicate(testContext)
        }.getOrElse { false }
    }

    /**
     * Evaluate a body contains condition.
     */
    private fun evaluateBodyContainsCondition(
        response: HttpResponse<String>,
        condition: Condition.BodyContains,
        context: ExecutionContext,
    ): Boolean {
        val body = response.body() ?: ""
        val text = resolveConditionValue(condition.text, context).toString()
        return body.contains(text)
    }

    /**
     * Evaluate a schema condition by validating the response against the OpenAPI schema.
     *
     * The schema is retrieved from the current operation's response definition based on
     * the actual response status code.
     */
    private fun evaluateSchemaCondition(
        response: HttpResponse<String>,
        context: ExecutionContext,
    ): Boolean {
        val operation = context.currentOperation ?: return true // Can't validate without operation
        val responseBody = response.body() ?: return true // Empty body passes validation

        // Find the schema for this response status code
        val schemaSpec = findResponseSchema(operation, response.statusCode()) ?: return true

        // Get raw swagger schema for validation
        @Suppress("UNCHECKED_CAST")
        val rawSchema = schemaSpec.rawSchema as? io.swagger.v3.oas.models.media.Schema<*> ?: return true

        // Validate the response body against the schema
        val errors = schemaValidator.validate(responseBody, rawSchema)
        return errors.isEmpty()
    }

    /**
     * Find the response schema for a given status code from the operation's response definitions.
     */
    private fun findResponseSchema(
        operation: ResolvedOperation,
        statusCode: Int,
    ): org.berrycrush.openapi.SchemaSpec? {
        val response = operation.findResponse(statusCode) ?: return null
        return response.content
            .values
            ?.firstOrNull()
            ?.schema
    }

    /**
     * Evaluate a response time condition.
     * Note: This requires tracking request start time, which may not be available in conditional context.
     */
    private fun evaluateResponseTimeCondition(
        response: HttpResponse<String>,
        condition: Condition.ResponseTime,
        context: ExecutionContext,
    ): Boolean {
        // TODO: Implement response time check
        return true
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
        return evaluateOperator(actual, expected, condition.operator)
    }

    /**
     * Shared operator evaluation logic for variable and JSON path conditions.
     *
     * Reduces duplication between evaluateVariableCondition and evaluateJsonPathCondition.
     */
    private fun evaluateOperator(
        actual: Any?,
        expected: Any?,
        operator: ConditionOperator,
    ): Boolean =
        when (operator) {
            ConditionOperator.EXISTS -> actual != null
            ConditionOperator.NOT_EXISTS -> actual == null
            ConditionOperator.EQUALS -> actual == expected || actual?.toString() == expected?.toString()
            ConditionOperator.NOT_EQUALS -> actual != expected && actual?.toString() != expected?.toString()
            ConditionOperator.CONTAINS ->
                when (actual) {
                    is String -> actual.contains(expected?.toString() ?: "")
                    is Collection<*> -> actual.contains(expected)
                    else -> false
                }
            ConditionOperator.NOT_CONTAINS ->
                when (actual) {
                    is String -> !actual.contains(expected?.toString() ?: "")
                    is Collection<*> -> !actual.contains(expected)
                    else -> true
                }
            ConditionOperator.MATCHES -> {
                val pattern = expected?.toString() ?: ""
                actual?.toString()?.matches(pattern.toRegex()) ?: false
            }
            ConditionOperator.GREATER_THAN -> compareAsNumbers(actual, expected) { a, e -> a > e }
            ConditionOperator.LESS_THAN -> compareAsNumbers(actual, expected) { a, e -> a < e }
            ConditionOperator.HAS_SIZE -> {
                val actualSize = sizeOf(actual) ?: return false
                val expectedSize =
                    (expected as? Number)?.toInt()
                        ?: expected?.toString()?.toIntOrNull()
                        ?: return false
                actualSize == expectedSize
            }
            ConditionOperator.NOT_EMPTY ->
                when (actual) {
                    is Collection<*> -> actual.isNotEmpty()
                    is String -> actual.isNotEmpty()
                    is Array<*> -> actual.isNotEmpty()
                    else -> actual != null
                }
        }

    /**
     * Get the size of a collection, string, or array.
     */
    private fun sizeOf(value: Any?): Int? =
        when (value) {
            is Collection<*> -> value.size
            is String -> value.length
            is Array<*> -> value.size
            else -> null
        }

    /**
     * Compare two values as numbers using the provided comparison function.
     * Returns false if either value cannot be converted to a number.
     */
    private inline fun compareAsNumbers(
        actual: Any?,
        expected: Any?,
        compare: (Double, Double) -> Boolean,
    ): Boolean {
        val actualNum =
            (actual as? Number)?.toDouble()
                ?: actual?.toString()?.toDoubleOrNull()
                ?: return false
        val expectedNum =
            (expected as? Number)?.toDouble()
                ?: expected?.toString()?.toDoubleOrNull()
                ?: return false
        return compare(actualNum, expectedNum)
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
                        start?.let { s -> end?.let { e -> actual in s..e } } ?: false
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
        return evaluateOperator(actualValue, expectedValue, condition.operator)
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
            ConditionOperator.GREATER_THAN, ConditionOperator.LESS_THAN,
            ConditionOperator.HAS_SIZE, ConditionOperator.NOT_EMPTY,
            -> false // Not applicable for headers
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

    /**
     * Run a single assertion using the shared condition evaluation logic.
     *
     * This ensures that `assert status 2xx` and `if status 2xx` use the exact same
     * evaluation logic, eliminating code duplication and potential inconsistencies.
     */
    private fun runAssertion(
        response: HttpResponse<String>,
        assertion: Assertion,
        context: ExecutionContext,
    ): AssertionResult {
        // Use the shared condition evaluation
        val passed = evaluateCondition(response, assertion.condition, context)

        // Generate appropriate message based on condition type
        val message = generateAssertionMessage(response, assertion.condition, passed, context)

        // Get actual value for reporting
        val actual = getActualValueForCondition(response, assertion.condition, context)

        return AssertionResult(
            assertion = assertion,
            passed = passed,
            message = message,
            actual = actual,
        )
    }

    /**
     * Generate a human-readable message for an assertion result.
     */
    private fun generateAssertionMessage(
        response: HttpResponse<String>,
        condition: Condition,
        passed: Boolean,
        context: ExecutionContext,
    ): String =
        when (condition) {
            is Condition.Status -> {
                val actual = response.statusCode()
                if (passed) "Status code is $actual" else "Expected status ${condition.expected} but got $actual"
            }
            is Condition.JsonPath -> {
                val body = response.body() ?: ""
                val actualValue = runCatching { JsonPath.read<Any>(body, condition.path) }.getOrNull()
                val expectedValue = condition.expected?.let { resolveConditionValue(it, context) }
                if (passed) {
                    "${condition.path} ${condition.operator.name.lowercase()} ${expectedValue ?: ""}"
                } else {
                    buildString {
                        append("Assertion failed at ${condition.path}\n")
                        append("  Operator: ${condition.operator.name.lowercase()}\n")
                        append("  Expected: ${expectedValue ?: "(none)"}\n")
                        append("  Actual:   $actualValue")
                    }
                }
            }
            is Condition.Header -> {
                val actualValue = response.headers().allValues(condition.name).firstOrNull()
                if (passed) {
                    "Header ${condition.name} ${condition.operator.name.lowercase()} ${condition.expected ?: ""}"
                } else {
                    "Header assertion failed: ${condition.name} ${condition.operator.name.lowercase()}, actual: $actualValue"
                }
            }
            is Condition.BodyContains -> {
                val text = condition.text.toString()
                if (passed) "Body contains '$text'" else "Body does not contain '$text'"
            }
            is Condition.Schema -> {
                if (passed) "Response matches schema" else "Response does not match schema"
            }
            is Condition.ResponseTime -> {
                if (passed) "Response time is under ${condition.maxMs}ms" else "Response time exceeded ${condition.maxMs}ms"
            }
            is Condition.Variable -> {
                val actual = context.get<Any>(condition.name)
                if (passed) {
                    "${condition.name} ${condition.operator.name.lowercase()} ${condition.expected}"
                } else {
                    "Variable ${condition.name}: expected ${condition.expected}, got $actual"
                }
            }
            is Condition.Negated -> {
                val innerMessage = generateAssertionMessage(response, condition.condition, !passed, context)
                if (passed) "NOT: condition was false (assertion passed)" else "Negated assertion failed: $innerMessage"
            }
            is Condition.Compound -> {
                if (passed) "Compound condition passed" else "Compound condition failed"
            }
            is Condition.CustomAssertion -> {
                if (passed) "Custom assertion passed: ${condition.pattern}" else "Custom assertion failed: ${condition.pattern}"
            }
            is Condition.Custom -> {
                if (passed) "Custom predicate passed" else "Custom predicate failed"
            }
        }

    /**
     * Get the actual value from the response for a given condition type.
     */
    private fun getActualValueForCondition(
        response: HttpResponse<String>,
        condition: Condition,
        context: ExecutionContext,
    ): Any? =
        when (condition) {
            is Condition.Status -> response.statusCode()
            is Condition.JsonPath -> {
                val body = response.body() ?: ""
                runCatching { JsonPath.read<Any>(body, condition.path) }.getOrNull()
            }
            is Condition.Header -> response.headers().allValues(condition.name).firstOrNull()
            is Condition.BodyContains -> response.body()?.take(200)
            is Condition.Variable -> context.get<Any>(condition.name)
            is Condition.Negated -> getActualValueForCondition(response, condition.condition, context)
            else -> null
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
}
