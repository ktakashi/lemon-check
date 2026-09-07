package org.berrycrush.executor.step

import org.berrycrush.executor.BerryCrushConfigurationProvider
import org.berrycrush.executor.BerryCrushExecutionListener
import org.berrycrush.executor.assertion.AssertionExecutor
import org.berrycrush.executor.enricher.ErrorEnricher
import org.berrycrush.executor.http.DefaultHttpExecutor
import org.berrycrush.executor.http.HttpExecutor
import org.berrycrush.executor.http.RetryingHttpExecutor
import org.berrycrush.executor.response.ResponseProcessor
import org.berrycrush.model.ResultStatus
import org.berrycrush.model.Step
import org.berrycrush.model.StepResult
import org.berrycrush.model.callDirective
import org.berrycrush.openapi.SpecRegistry
import org.berrycrush.plugin.StepContext
import java.time.Duration
import java.time.Instant

class OperationStepExecutor(
    private val specRegistry: SpecRegistry,
    private val configuration: BerryCrushConfigurationProvider,
    private val assertionExecutor: AssertionExecutor,
    private val responseProcessor: ResponseProcessor,
    private val errorEnricher: ErrorEnricher,
) {
    companion object {
        /**
         * Create the appropriate HTTP executor based on configuration.
         *
         * If retry is enabled in the configuration, wraps the default executor
         * with a [RetryingHttpExecutor].
         */
        private fun createHttpExecutor(configuration: BerryCrushConfigurationProvider): HttpExecutor {
            val baseExecutor = DefaultHttpExecutor(configuration)
            return if (configuration.retryConfig.isEnabled) {
                RetryingHttpExecutor(
                    delegate = baseExecutor,
                    config = configuration.retryConfig,
                )
            } else {
                baseExecutor
            }
        }
    }

    private val httpExecutor = createHttpExecutor(configuration)

    // Lazy-initialized auto-test executor - created on first use to avoid circular dependencies
    private val autoTestExecutor: AutoTestExecutor by lazy {
        AutoTestExecutor(
            specRegistry = specRegistry,
            configuration = configuration,
            httpExecutor = httpExecutor,
            assertionRunner = assertionExecutor::runAssertions,
        )
    }

    fun execute(
        step: Step,
        stepContext: StepContext,
        stepIndex: Int,
        listener: BerryCrushExecutionListener,
    ): StepResult =
        Instant.now().let { stepStartTime ->
            runCatching {
                // Check if this step has auto-test configuration
                val autoTestConfig = step.callDirective?.autoTestConfig
                if (autoTestConfig != null) {
                    executeAutoTestStep(step, stepContext, stepStartTime, listener)
                } else {
                    executeHttpRequest(step, stepContext)
                }
            }.getOrElse { e ->
                // Create enriched error context for debugging
                val errorContext = errorEnricher.buildScenarioErrorContext(stepContext, step, stepIndex)
                val enrichedError = errorEnricher.enrichException(e, errorContext, stepContext)

                StepResult(
                    step = step,
                    status = ResultStatus.ERROR,
                    duration = Duration.between(stepStartTime, Instant.now()),
                    error = enrichedError,
                )
            }
        }

    /**
     * Build request context and execute HTTP request.
     */
    private fun executeHttpRequest(
        step: Step,
        stepContext: StepContext,
    ): StepResult = responseProcessor.process(step, httpExecutor.execute(step, specRegistry, stepContext), stepContext)

    /**
     * Execute auto-test step with configured test types.
     */
    private fun executeAutoTestStep(
        step: Step,
        stepContext: StepContext,
        stepStartTime: Instant,
        listener: BerryCrushExecutionListener,
    ): StepResult {
        // Extract step-level multi-test parameters from pathParams
        val stepMultiTestParams = (step.callDirective?.pathParams ?: emptyMap()).filterKeys { it.startsWith("multiTest") }

        // Merge configuration defaults -> context params -> step params (step wins)
        val parameters =
            configuration.multiTestConfig.mapKeys { "multiTest.${it.key}" } +
                stepContext.allExecutionVariables() +
                stepMultiTestParams
        listener.onAutoTestStepStarting(step)
        return autoTestExecutor.execute(step, stepContext, stepStartTime, parameters, listener).apply {
            listener.onAutoTestStepCompleted(this)
        }
    }
}
