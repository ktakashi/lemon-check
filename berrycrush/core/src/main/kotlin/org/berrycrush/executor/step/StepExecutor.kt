package org.berrycrush.executor.step

import org.berrycrush.executor.BerryCrushExecutionListener
import org.berrycrush.executor.fragment.FragmentExecutor
import org.berrycrush.executor.response.ResponseProcessor
import org.berrycrush.model.ResultStatus
import org.berrycrush.model.Scenario
import org.berrycrush.model.Step
import org.berrycrush.model.StepResult
import org.berrycrush.model.WebhookConfig
import org.berrycrush.model.callDirective
import org.berrycrush.model.directiveAssertions
import org.berrycrush.model.directiveExtractions
import org.berrycrush.model.webhookDirective
import org.berrycrush.plugin.PluginRegistry
import org.berrycrush.plugin.ScenarioContext
import org.berrycrush.plugin.StepContext
import org.berrycrush.plugin.adapter.ExecutionContextAdapter
import org.berrycrush.plugin.adapter.StepContextAdapter
import org.berrycrush.plugin.adapter.StepResultAdapter
import org.berrycrush.step.StepContextImpl
import org.berrycrush.util.StepMatch
import org.berrycrush.util.StepRegistry
import org.berrycrush.util.forEachNonNull
import org.berrycrush.webhook.MockWebhookServer
import java.lang.reflect.InvocationTargetException
import java.time.Duration
import java.time.Instant
import kotlin.collections.forEach

class StepExecutor(
    private val fragmentExecutor: FragmentExecutor,
    private val operationStepExecutor: OperationStepExecutor,
    private val responseProcessor: ResponseProcessor,
    private val stepRegistry: StepRegistry? = null,
    private val pluginRegistry: PluginRegistry? = null,
) {
    fun execute(
        scenario: Scenario,
        scenarioContext: ScenarioContext,
        listener: BerryCrushExecutionListener,
    ): List<StepResult> {
        val stepResults = mutableListOf<StepResult>()
        val (stepIndex, continueExecution) = executeSteps(scenario.background, scenarioContext, stepResults, 0, listener)
        if (continueExecution) {
            // Execute scenario steps
            executeSteps(scenario.steps, scenarioContext, stepResults, stepIndex, listener)
        }
        return stepResults
    }

    /**
     * Execute a list of steps with continuation control.
     */
    private fun executeSteps(
        steps: List<Step>,
        scenarioContext: ScenarioContext,
        results: MutableList<StepResult>,
        startIndex: Int,
        listener: BerryCrushExecutionListener,
    ): Pair<Int, Boolean> {
        var continueExecution = true
        var stepIndex = startIndex
        for (step in steps) {
            if (!continueExecution) {
                results.add(StepResult(step = step, status = ResultStatus.SKIPPED))
                continue
            }

            // Inject include parameters into context before expanding
            val includeParameters = fragmentExecutor.includeParameters(step, scenarioContext)
            scenarioContext.withIncludeParameters(includeParameters) {
                val expandedSteps = fragmentExecutor.expand(step, scenarioContext)
                for (expandedStep in expandedSteps) {
                    if (!continueExecution) {
                        results.add(StepResult(step = expandedStep, status = ResultStatus.SKIPPED))
                        continue
                    }

                    val result = executeStep(expandedStep, scenarioContext, stepIndex++, listener)
                    results.add(result)

                    if (result.status != ResultStatus.PASSED) {
                        continueExecution = false
                    }
                }
            }
        }

        return stepIndex to continueExecution
    }

    private fun executeStep(
        step: Step,
        scenarioContext: ScenarioContext,
        stepIndex: Int,
        listener: BerryCrushExecutionListener,
    ): StepResult {
        // Create step context
        val stepContext = StepContextAdapter(step, stepIndex, scenarioContext)

        // Notify listener that step is starting
        listener.onStepStarting(step)

        // Dispatch plugin: onStepStart
        pluginRegistry?.dispatchStepStart(stepContext)

        // Execute the actual step with scenario context for error enrichment
        val call = step.callDirective
        val hasCallTarget = call?.operationId != null || call?.rawRequest != null
        val result =
            if (hasCallTarget) {
                operationStepExecutor.execute(step, stepContext, stepIndex, listener)
            } else {
                executeNonOperationStep(step, stepContext)
            }

        // Dispatch plugin: onStepEnd
        pluginRegistry?.dispatchStepEnd(stepContext, StepResultAdapter(result))

        // Notify listener that step completed
        listener.onStepCompleted(result)

        return result
    }

    private fun executeNonOperationStep(
        step: Step,
        stepContext: StepContext,
    ): StepResult {
        val stepStartTime = Instant.now()
        // First, check if this is a webhook step
        step.webhookDirective?.config?.let { config ->
            return executeWebhookStep(step, config, stepContext, stepStartTime)
        }

        // Check if this is a custom step
        stepRegistry?.let { registry ->
            val resolvedDescription = stepContext.interpolate(step.description)
            val match = registry.findMatch(resolvedDescription)
            if (match != null) {
                return executeCustomStep(step, match, stepContext, stepStartTime)
            }
        }

        // Not a custom step - check for assertions/extractions
        return if (step.directiveAssertions.isEmpty() && step.directiveExtractions.isEmpty()) {
            // No operation and no assertions - just pass
            StepResult(
                step = step,
                status = ResultStatus.ERROR,
                duration = Duration.between(stepStartTime, Instant.now()),
                error = IllegalStateException("No custom step nor assertions on this step"),
            )
        } else {
            // Run assertions/extractions against last response
            stepContext.response?.let { response ->
                responseProcessor.process(step, response, stepContext)
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
        stepContext: StepContext,
        stepStartTime: Instant,
    ): StepResult =
        runCatching {
            val context = stepContext.scenarioContext.executionContext
            val stepContext =
                StepContextImpl(
                    stepContext = stepContext,
                    sharingEnabled = context.shareVariablesAcrossScenarios,
                )

            // Invoke the custom step method with extracted parameters and context
            val method = match.definition.method
            val parameters = match.parameters.toTypedArray()

            // Check if method accepts StepContext as last parameter
            val methodParams = method.parameters
            val args =
                if (methodParams.isNotEmpty() &&
                    org.berrycrush.step.StepContext::class.java.isAssignableFrom(methodParams.last().type)
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
                    is InvocationTargetException -> e.cause ?: e
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
     * Execute a webhook step that starts a mock webhook server.
     *
     * This creates a MockWebhookServer, registers expected webhook operations,
     * starts the server, and registers it in the execution context for
     * variable interpolation (e.g., {{serverName.hookName}}).
     */
    internal fun executeWebhookStep(
        step: Step,
        config: WebhookConfig,
        context: StepContext,
        stepStartTime: Instant,
    ): StepResult =
        runCatching {
            // Create and configure the webhook server
            val server = MockWebhookServer(config.port)

            // Register expected webhook operations
            config.hooks.forEach { hookName ->
                server.expect(hookName)
            }

            // Start the server
            server.start()

            val executionContext = context.scenarioContext.executionContext
            if (executionContext is ExecutionContextAdapter) {
                // Register the server in the context for variable interpolation
                executionContext.context.registerWebhookServer(config.name, server)
            }

            StepResult(
                step = step,
                status = ResultStatus.PASSED,
                duration = Duration.between(stepStartTime, Instant.now()),
            )
        }.getOrElse { e ->
            StepResult(
                step = step,
                status = ResultStatus.ERROR,
                duration = Duration.between(stepStartTime, Instant.now()),
                error = e as? Exception ?: RuntimeException(e),
            )
        }
}

internal inline fun <T> ScenarioContext.withIncludeParameters(
    includeParameters: Map<String, Any?>,
    block: () -> T,
): T =
    if (includeParameters.isEmpty()) {
        block()
    } else {
        val context = this.executionContext
        val saved =
            includeParameters.keys
                .filter { context.contains(it) }
                .associateWith { context[it] as Any? }
        try {
            context.resolveParams(includeParameters).forEachNonNull { key, value -> context[key] = value }
            block()
        } finally {
            // Restore original values
            saved.forEachNonNull { key, value -> context[key] = value }
        }
    }
