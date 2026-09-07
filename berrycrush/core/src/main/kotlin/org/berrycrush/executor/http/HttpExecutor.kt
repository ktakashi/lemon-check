package org.berrycrush.executor.http

import org.berrycrush.executor.resolvers.RequestResolver
import org.berrycrush.executor.resolvers.resolveCall
import org.berrycrush.model.HttpRequest
import org.berrycrush.model.HttpResponse
import org.berrycrush.model.Step
import org.berrycrush.model.callDirective
import org.berrycrush.openapi.LoadedSpec
import org.berrycrush.openapi.ResolvedOperation
import org.berrycrush.openapi.SpecRegistry
import org.berrycrush.plugin.StepContext
import org.berrycrush.plugin.adapter.ScenarioContextAdapter

/**
 * Executor for HTTP requests during scenario execution.
 *
 * This interface abstracts HTTP request building and execution from
 * the main scenario executor.
 */
interface HttpExecutor : RequestResolver {
    val directExecutor: DirectHttpExecutor?
        get() = null

    fun execute(
        step: Step,
        specRegistry: SpecRegistry,
        stepContext: StepContext,
    ): HttpResponse {
        val resolvedStep = stepContext.resolveCall(step)
        val executor = directExecutor
        val call = resolvedStep.callDirective
        return when {
            call?.rawRequest != null && executor != null -> {
                executor.execute(step, stepContext)
            }

            call?.operationId != null -> {
                val (spec, resolvedOp) = resolve(resolvedStep, specRegistry)
                // Execute the HTTP request using the HttpExecutor
                val response = execute(resolvedStep, spec, resolvedOp, stepContext)
                // Update context with response
                response
            }

            else -> {
                throw IllegalArgumentException("Step must have either a raw request or an operation ID.")
            }
        }
    }

    /**
     * Execute an HTTP request for the given step.
     *
     * @param step The step containing the operation and parameters
     * @param spec The loaded OpenAPI specification (for base URL and default headers)
     * @param operation The resolved OpenAPI operation
     * @param context The execution context with variables and state
     * @return The HTTP response from the server
     */
    fun execute(
        step: Step,
        spec: LoadedSpec,
        operation: ResolvedOperation,
        context: StepContext,
    ): HttpResponse =
        run {
            val scenarioContext = context.scenarioContext
            if (scenarioContext is ScenarioContextAdapter) {
                scenarioContext.addOperation(operation)
            }
            execute(resolve(step, spec, operation, context), context)
        }

    fun execute(
        request: HttpRequest,
        context: StepContext,
    ): HttpResponse

    fun resolve(
        step: Step,
        specRegistry: SpecRegistry,
    ) = step.callDirective?.let { call ->
        specRegistry.resolve(requireNotNull(call.operationId), call.specName)
    } ?: throw IllegalArgumentException("Step must have a call directive")
}

fun interface DirectHttpExecutor {
    fun execute(
        step: Step,
        context: StepContext,
    ): HttpResponse
}
