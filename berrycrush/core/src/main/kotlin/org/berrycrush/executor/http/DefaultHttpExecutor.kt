package org.berrycrush.executor.http

import org.berrycrush.config.BindingConfig
import org.berrycrush.exception.HttpExecutionException
import org.berrycrush.executor.BerryCrushConfigurationProvider
import org.berrycrush.executor.HttpRequestBuilder
import org.berrycrush.executor.resolvers.DefaultRequestResolver
import org.berrycrush.executor.resolvers.RequestResolver
import org.berrycrush.model.HttpRequest
import org.berrycrush.model.HttpResponse
import org.berrycrush.model.Step
import org.berrycrush.model.callDirective
import org.berrycrush.openapi.HttpMethod
import org.berrycrush.openapi.LoadedSpec
import org.berrycrush.openapi.ResolvedOperation
import org.berrycrush.openapi.SpecRegistry
import org.berrycrush.plugin.StepContext
import org.berrycrush.plugin.adapter.ScenarioContextAdapter
import org.berrycrush.plugin.adapter.StepContextAdapter
import org.berrycrush.util.toNonNullMap
import tools.jackson.databind.ObjectMapper
import java.time.Duration
import java.time.Instant

/**
 * Default implementation of [HttpExecutor] for executing HTTP requests.
 *
 * This implementation handles:
 * - URL building with path and query parameters
 * - Header merging (config defaults + spec defaults + step headers)
 * - Body resolution (inline, structured properties, or file)
 * - HTTP request execution
 * - Request/response logging (if enabled)
 *
 * @property configuration Configuration for base URL, logging, and default headers
 * @property httpBuilder Builder for constructing and executing HTTP requests
 */
class DefaultHttpExecutor(
    private val configuration: BerryCrushConfigurationProvider,
    private val httpBuilder: HttpRequestBuilder = HttpRequestBuilder(configuration),
    objectMapper: ObjectMapper = ObjectMapper(),
    private val requestResolver: RequestResolver = DefaultRequestResolver(configuration, httpBuilder, objectMapper),
) : HttpExecutor,
    DirectHttpExecutor,
    RequestResolver by requestResolver {
    override val directExecutor: DirectHttpExecutor = this

    override fun execute(
        request: HttpRequest,
        context: StepContext,
    ): HttpResponse {
        if (context is StepContextAdapter) {
            context.setRequest(request)
        }
        logRequest(request)

        // Record request start time for logging
        val requestStartTime = Instant.now()

        // Execute the HTTP request
        val rawResponse =
            httpBuilder.execute(
                method = request.method,
                url = request.url,
                headers = request.headers,
                body = request.body,
            )
        val requestEndTime = Instant.now()
        val duration = Duration.between(requestStartTime, requestEndTime)
        val response =
            rawResponse
                .map {
                    HttpResponse(
                        statusCode = it.statusCode(),
                        statusMessage = HTTP_STATUS_MESSAGES[it.statusCode()] ?: "",
                        headers = it.headers().map(),
                        body = it.body(),
                        duration = duration,
                        timestamp = requestEndTime,
                        request = request,
                    )
                }.getOrElse { e ->
                    val wrapped = HttpExecutionException(request.url, request.method, e)
                    if (configuration.autoAssertions.enabled) {
                        throw wrapped
                    }
                    HttpResponse(
                        statusCode = -1,
                        statusMessage = e.message ?: "",
                        headers = emptyMap(),
                        duration = duration,
                        timestamp = requestEndTime,
                        request = request,
                        error = wrapped,
                    )
                }

        // Log response if enabled
        logResponse(request, response, duration)

        if (context is StepContextAdapter) {
            context.setResponse(response)
            context.updateResponseTime(duration)
        }
        val scenarioContext = context.scenarioContext
        if (scenarioContext is ScenarioContextAdapter) {
            scenarioContext.addAudit(request, response)
        }
        return response
    }

    override fun resolve(
        step: Step,
        specRegistry: SpecRegistry,
    ): Pair<LoadedSpec, ResolvedOperation> {
        val call = step.callDirective ?: throw IllegalArgumentException("Step must have a call directive")
        return specRegistry.resolve(requireNotNull(call.operationId), call.specName, configuration.bindings)
    }

    override fun execute(
        step: Step,
        context: StepContext,
    ): HttpResponse = execute(buildDirectRawRequest(step, context), context)

    private fun buildDirectRawRequest(
        step: Step,
        context: StepContext,
    ): HttpRequest {
        val call = step.callDirective ?: throw IllegalArgumentException("Step must have a call directive")
        val rawRequest = requireNotNull(call.rawRequest)
        val rawMethod = context.interpolate(rawRequest.method)
        val resolvedMethod =
            HttpMethod.fromName(rawMethod)
                ?: throw IllegalArgumentException("Unsupported HTTP method '$rawMethod' in 'call raw'.")
        val rawPath = context.interpolate(rawRequest.path)
        val baseUrl = resolveRawBaseUrl(call.specName ?: BindingConfig.DEFAULT_BINDING_NAME)

        val url =
            httpBuilder.buildUrl(
                baseUrl = baseUrl,
                path = rawPath,
                pathParams = context.resolveParams(call.pathParams).toNonNullMap(),
                queryParams = context.resolveParams(call.queryParams).toNonNullMap(),
            )
        val headers =
            (configuration.defaultHeaders + call.headers)
                .mapValues { (_, value) -> context.interpolate(value) }
        val body = resolveBody(step, null, context)

        return HttpRequest(
            method = resolvedMethod,
            url = url,
            headers = headers,
            body = body,
        )
    }

    private fun resolveRawBaseUrl(specName: String): String =
        configuration.bindings[specName]?.baseUrl
            ?: throw IllegalArgumentException("Spec '$specName' not found for raw call")

    // ========== Logging ==========

    /**
     * Log HTTP request if enabled.
     */
    private fun logRequest(request: HttpRequest) {
        if (configuration.logRequests) {
            configuration.getEffectiveHttpLogger().logRequest(request.method, request.url, request.headers, request.body)
        }
    }

    /**
     * Log HTTP response if enabled.
     */
    private fun logResponse(
        request: HttpRequest,
        response: HttpResponse,
        duration: Duration,
    ) {
        if (configuration.logResponses) {
            configuration.getEffectiveHttpLogger().logResponse(request.method, request.url, response, duration.toMillis())
        }
    }
}

/**
 * HTTP status code to message mapping.
 */
@Suppress("MagicNumber")
private val HTTP_STATUS_MESSAGES =
    mapOf(
        200 to "OK",
        201 to "Created",
        204 to "No Content",
        400 to "Bad Request",
        401 to "Unauthorized",
        403 to "Forbidden",
        404 to "Not Found",
        405 to "Method Not Allowed",
        409 to "Conflict",
        422 to "Unprocessable Entity",
        500 to "Internal Server Error",
        502 to "Bad Gateway",
        503 to "Service Unavailable",
    )
