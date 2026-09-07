package org.berrycrush.executor.enricher

import org.berrycrush.exception.HttpExecutionException
import org.berrycrush.exception.ScenarioErrorContext
import org.berrycrush.executor.BerryCrushConfigurationProvider
import org.berrycrush.model.HttpMethod
import org.berrycrush.model.Step
import org.berrycrush.model.callDirective
import org.berrycrush.plugin.StepContext

class ErrorEnricher(
    private val configuration: BerryCrushConfigurationProvider,
) {
    /**
     * Build scenario error context from current execution state.
     */
    fun buildScenarioErrorContext(
        stepContext: StepContext,
        step: Step,
        stepIndex: Int,
    ): ScenarioErrorContext =
        ScenarioErrorContext(
            scenarioName = stepContext.scenarioContext.scenarioName,
            scenarioFile = stepContext.scenarioContext.scenarioFile.toString(),
            stepDescription = step.description,
            stepIndex = stepIndex,
            stepLine = step.sourceLocation?.line,
            operationId = step.callDirective?.operationId,
        )

    /**
     * Enrich an exception with scenario/step context for better debugging.
     */
    fun enrichException(
        original: Throwable,
        errorContext: ScenarioErrorContext,
        stepContext: StepContext,
    ): Exception {
        // For HTTP-related exceptions, wrap with full context
        val exception = original as? Exception ?: RuntimeException(original)

        // If this is already an HttpExecutionException with context, return as-is
        if (exception is HttpExecutionException && exception.scenarioContext != null) {
            return exception
        }
        // Create response snapshot from context
        val lastRequest = stepContext.request
        // Return enhanced exception with context
        return HttpExecutionException(
            url = lastRequest?.url ?: "unknown",
            method = lastRequest?.method ?: HttpMethod.UNKNOWN,
            cause = exception,
            response = stepContext.response,
            scenarioContext = errorContext,
            config = configuration.errorContextConfig,
        )
    }
}
