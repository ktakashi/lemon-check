package org.berrycrush.executor

import org.berrycrush.assertion.AssertionRegistry
import org.berrycrush.context.ExecutionContext
import org.berrycrush.context.propagate
import org.berrycrush.context.resolveParam
import org.berrycrush.context.resolveParams
import org.berrycrush.executor.assertion.AssertionExecutor
import org.berrycrush.executor.assertion.DefaultAssertionEngine
import org.berrycrush.executor.enricher.ErrorEnricher
import org.berrycrush.executor.fragment.DefaultFragmentExecutor
import org.berrycrush.executor.fragment.FragmentExecutor
import org.berrycrush.executor.response.ResponseProcessor
import org.berrycrush.executor.step.OperationStepExecutor
import org.berrycrush.executor.step.StepExecutor
import org.berrycrush.model.FragmentRegistry
import org.berrycrush.model.ResultStatus
import org.berrycrush.model.Scenario
import org.berrycrush.model.ScenarioResult
import org.berrycrush.model.Step
import org.berrycrush.model.StepResult
import org.berrycrush.model.includeDirective
import org.berrycrush.model.webhookDirective
import org.berrycrush.openapi.SpecRegistry
import org.berrycrush.plugin.PluginRegistry
import org.berrycrush.plugin.StepContext
import org.berrycrush.plugin.adapter.ExecutionContextAdapter
import org.berrycrush.plugin.adapter.ScenarioContextAdapter
import org.berrycrush.plugin.adapter.ScenarioResultAdapter
import org.berrycrush.util.StepRegistry
import org.berrycrush.util.forEachNonNull
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Executes BDD scenarios against API endpoints.
 *
 * @property configuration Execution configuration
 * @property pluginRegistry Optional plugin registry for lifecycle hooks
 */
class BerryCrushScenarioExecutor(
    private val configuration: BerryCrushConfigurationProvider,
    private val specRegistry: SpecRegistry,
    private val stepExecutor: StepExecutor,
    private val fragmentRegistry: FragmentRegistry? = null,
    private val pluginRegistry: PluginRegistry? = null,
) {
    companion object {
        private fun createStepExecutor(
            specRegistry: SpecRegistry,
            configuration: BerryCrushConfigurationProvider,
            pluginRegistry: PluginRegistry?,
            fragmentRegistry: FragmentRegistry?,
            stepRegistry: StepRegistry?,
            assertionRegistry: AssertionRegistry?,
        ): StepExecutor {
            val fragmentExecutor = DefaultFragmentExecutor(fragmentRegistry)
            val assertionExecutor = AssertionExecutor(DefaultAssertionEngine(assertionRegistry))
            val responseProcessor = ResponseProcessor(assertionExecutor, configuration)
            val operationStepExecutor =
                OperationStepExecutor(
                    specRegistry,
                    configuration,
                    assertionExecutor,
                    responseProcessor,
                    ErrorEnricher(configuration),
                )
            return StepExecutor(
                fragmentExecutor,
                operationStepExecutor,
                responseProcessor,
                stepRegistry,
                pluginRegistry,
            )
        }
    }

    constructor(
        specRegistry: SpecRegistry,
        configuration: BerryCrushConfigurationProvider,
        pluginRegistry: PluginRegistry? = null,
        fragmentRegistry: FragmentRegistry? = null,
        stepRegistry: StepRegistry? = null,
        assertionRegistry: AssertionRegistry? = null,
    ) :
        this(
            configuration,
            specRegistry,
            createStepExecutor(specRegistry, configuration, pluginRegistry, fragmentRegistry, stepRegistry, assertionRegistry),
            fragmentRegistry,
            pluginRegistry,
        )

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
        sourceFile: File? = null,
        executionListener: BerryCrushExecutionListener? = null,
    ): ScenarioResult =
        specRegistry.checkpoint {
            val parameters = (sharedContext?.mergedParameters ?: emptyMap()) + scenario.parameters
            configuration.withParameters(fragmentRegistry?.resolveParameters(parameters) ?: parameters) {
                val listener = executionListener ?: BerryCrushExecutionListener.NOOP

                // Notify listener that scenario is starting
                listener.onScenarioStarting(scenario)

                val startTime = Instant.now()
                var context = sharedContext?.createChild() ?: ExecutionContext(true, scenario.parameters)

                // Create execution context - use shared context if available,
                // or create one for outline scenarios with examples
                if (scenario.examples?.isNotEmpty() == true) {
                    if (!context.shareVariablesAcrossScenarios) context = ExecutionContext(true, scenario.parameters)
                    val row = scenario.examples.first()
                    context
                        .resolveParams(row.values)
                        .forEachNonNull { key, value -> context[key] = value }
                }
                // put entire configuration into parameter variables
                setupParameters(configuration.toParameterMap(), context)

                if (sharedContext?.mergedParameters != null) {
                    setupParameters(sharedContext.mergedParameters, context)
                }
                // Store scenario parameters in context for variable resolution
                setupParameters(scenario.parameters, context)

                // resolve reference in configuration
                // the references, mostly in parameters block, are use `{{param.**}` format
                // so this needs to be *after* the parameters are set up in context
                configuration.resolveReference { value -> context.resolveParam(value) ?: value }
                configuration.bindings.forEach { (name, config) ->
                    config.location?.let {
                        specRegistry.register(name, it) {
                            this.baseUrl = config.baseUrl
                        }
                    }
                }

                val scenarioContext =
                    ScenarioContextAdapter(scenario, ExecutionContextAdapter(context), startTime, configuration, sourceFile)

                pluginRegistry?.dispatchScenarioStart(scenarioContext)
                val stepResults = stepExecutor.execute(scenario, scenarioContext, listener)
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

                // Cleanup scenario-scoped webhook servers
                context.cleanupWebhookServers()

                // Copy extracted variables back to shared context for cross-scenario sharing
                if (scenarioResult.status == ResultStatus.PASSED) {
                    sharedContext.propagate(context)
                }

                // Notify listener that scenario completed
                listener.onScenarioCompleted(scenario, scenarioResult)

                scenarioResult
            }
        }

    // parameter resolve is *after* example row resolution, so the outline parameters can be dynamic.
    private fun setupParameters(
        parameters: Map<String, Any>,
        context: ExecutionContext,
    ) {
        parameters.forEach { (key, value) ->
            context["param.$key"] = context.resolveParam(value) ?: value
        }
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

    /**
     * Execute a step that has no operationId.
     *
     * Checks in order:
     * 1. Webhook configuration (start mock webhook server)
     * 2. Custom step definition match
     * 3. Assertions/extractions against last response
     * 4. No-op (just pass)
     */
    internal fun executeWebhookStep(
        step: Step,
        context: StepContext,
        startTime: Instant,
    ): StepResult? =
        step.webhookDirective?.config?.let { config ->
            stepExecutor.executeWebhookStep(step, config, context, startTime)
        }
}

internal inline fun <T> ExecutionContext.withIncludeParameters(
    step: Step,
    block: () -> T,
): T {
    val includeParameters = step.includeDirective?.parameters ?: emptyMap()
    if (includeParameters.isEmpty()) {
        return block()
    } else {
        val saved =
            includeParameters.keys
                .filter { this.contains(it) }
                .associateWith { this.get<Any>(it) as Any }
        try {
            this
                .resolveParams(includeParameters)
                .forEach { (key, value) -> value?.let { this[key] = it } }
            return block()
        } finally {
            // Restore original values
            for ((key, value) in saved) {
                this[key] = value
            }
        }
    }
}

private fun <T> SpecRegistry.checkpoint(block: () -> T): T {
    val snapshot = snapshot()
    return try {
        block()
    } finally {
        restore(snapshot)
    }
}
