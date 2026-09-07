package org.berrycrush.model

import org.berrycrush.model.Directive.AssertionDirective
import org.berrycrush.model.Directive.CallDirective
import org.berrycrush.model.Directive.ConditionalDirective
import org.berrycrush.model.Directive.ExtractionDirective
import org.berrycrush.model.Directive.FailDirective
import org.berrycrush.model.Directive.IncludeDirective
import org.berrycrush.model.Directive.WebhookDirective
import org.berrycrush.scenario.WebhookScope

/**
 * Represents a body property value, which can be either a simple value or a nested object.
 */
sealed class BodyProperty {
    /** A simple value (string, number, boolean, etc.) */
    data class Simple(
        val value: Any?,
    ) : BodyProperty()

    data class Container(
        val value: String,
    ) : BodyProperty()

    /** A nested object with properties */
    data class Nested(
        val properties: Map<String, BodyProperty>,
    ) : BodyProperty()
}

data class Step(
    val type: StepType,
    val description: String,
    val directives: List<Directive> = emptyList(),
    val sourceLocation: SourceLocation? = null,
) {
    private val call: CallDirective?
        get() = callDirective

    @Deprecated("Use directives and Directive.CallDirective.operationId", ReplaceWith("directives"))
    val operationId: String?
        get() = call?.operationId

    @Deprecated("Use directives and Directive.CallDirective.rawRequest", ReplaceWith("directives"))
    val rawRequest: RawRequest?
        get() = call?.rawRequest

    @Deprecated("Use directives and Directive.CallDirective.specName", ReplaceWith("directives"))
    val specName: String?
        get() = call?.specName

    @Deprecated("Use directives and Directive.CallDirective.pathParams", ReplaceWith("directives"))
    val pathParams: Map<String, Any>
        get() = call?.pathParams ?: emptyMap()

    @Deprecated("Use directives and Directive.CallDirective.queryParams", ReplaceWith("directives"))
    val queryParams: Map<String, Any>
        get() = call?.queryParams ?: emptyMap()

    @Deprecated("Use directives and Directive.CallDirective.headers", ReplaceWith("directives"))
    val headers: Map<String, String>
        get() = call?.headers ?: emptyMap()

    @Deprecated("Use directives and Directive.CallDirective.body", ReplaceWith("directives"))
    val body: String?
        get() = call?.body

    @Deprecated("Use directives and Directive.CallDirective.bodyProperties", ReplaceWith("directives"))
    val bodyProperties: Map<String, BodyProperty>?
        get() = call?.bodyProperties

    @Deprecated("Use directives and Directive.CallDirective.bodyFile", ReplaceWith("directives"))
    val bodyFile: String?
        get() = call?.bodyFile

    @Deprecated("Use directives and Directive.ExtractionDirective", ReplaceWith("directives"))
    val extractions: List<Extraction>
        get() = directiveExtractions

    @Deprecated("Use directives and Directive.AssertionDirective", ReplaceWith("directives"))
    val assertions: List<Assertion>
        get() = directiveAssertions

    @Deprecated("Use directives and Directive.FailDirective", ReplaceWith("directives"))
    val failMessage: String?
        get() = failDirective?.message

    @Deprecated("Use directives and Directive.CallDirective.autoAssert", ReplaceWith("directives"))
    val autoAssert: Boolean
        get() = call?.autoAssert ?: true

    @Deprecated("Use directives and Directive.CallDirective.autoTestConfig", ReplaceWith("directives"))
    val autoTestConfig: AutoTestConfig?
        get() = call?.autoTestConfig

    @Deprecated("Use directives and Directive.IncludeDirective", ReplaceWith("directives"))
    val fragmentName: String?
        get() = includeDirective?.fragmentName

    @Deprecated("Use directives and Directive.IncludeDirective", ReplaceWith("directives"))
    val includeParameters: Map<String, Any?>
        get() = includeDirective?.parameters ?: emptyMap()

    @Deprecated("Use directives and Directive.WebhookDirective", ReplaceWith("directives"))
    val webhookConfig: WebhookConfig?
        get() = webhookDirective?.config

    @Deprecated("Use Step(type, description, directives, sourceLocation)")
    @Suppress("LongParameterList")
    constructor(
        type: StepType,
        description: String,
        operationId: String? = null,
        rawRequest: RawRequest? = null,
        specName: String? = null,
        pathParams: Map<String, Any> = emptyMap(),
        queryParams: Map<String, Any> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        bodyProperties: Map<String, BodyProperty>? = null,
        bodyFile: String? = null,
        extractions: List<Extraction> = emptyList(),
        assertions: List<Assertion> = emptyList(),
        failMessage: String? = null,
        autoAssert: Boolean = true,
        autoTestConfig: AutoTestConfig? = null,
        fragmentName: String? = null,
        includeParameters: Map<String, Any?> = emptyMap(),
        sourceLocation: SourceLocation? = null,
        webhookConfig: WebhookConfig? = null,
    ) : this(
        type = type,
        description = description,
        directives =
            legacyDirectives(
                operationId,
                rawRequest,
                specName,
                pathParams,
                queryParams,
                headers,
                body,
                bodyProperties,
                bodyFile,
                extractions,
                assertions,
                failMessage,
                autoAssert,
                autoTestConfig,
                fragmentName,
                includeParameters,
                webhookConfig,
                sourceLocation,
            ),
        sourceLocation = sourceLocation,
    )
}

@Suppress("LongParameterList", "CyclomaticComplexMethod")
private fun legacyDirectives(
    operationId: String?,
    rawRequest: RawRequest?,
    specName: String?,
    pathParams: Map<String, Any>,
    queryParams: Map<String, Any>,
    headers: Map<String, String>,
    body: String?,
    bodyProperties: Map<String, BodyProperty>?,
    bodyFile: String?,
    extractions: List<Extraction>,
    assertions: List<Assertion>,
    failMessage: String?,
    autoAssert: Boolean,
    autoTestConfig: AutoTestConfig?,
    fragmentName: String?,
    includeParameters: Map<String, Any?>,
    webhookConfig: WebhookConfig?,
    sourceLocation: SourceLocation?,
): List<Directive> =
    buildList {
        val hasCall =
            operationId != null ||
                rawRequest != null ||
                specName != null ||
                pathParams.isNotEmpty() ||
                queryParams.isNotEmpty() ||
                headers.isNotEmpty() ||
                body != null ||
                bodyProperties != null ||
                bodyFile != null ||
                autoTestConfig != null ||
                !autoAssert

        if (hasCall) {
            add(
                CallDirective(
                    operationId = operationId,
                    rawRequest = rawRequest,
                    specName = specName,
                    pathParams = pathParams,
                    queryParams = queryParams,
                    headers = headers,
                    body = body,
                    bodyProperties = bodyProperties,
                    bodyFile = bodyFile,
                    autoAssert = autoAssert,
                    autoTestConfig = autoTestConfig,
                    sourceLocation = sourceLocation,
                ),
            )
        }

        extractions.forEach { extraction ->
            add(ExtractionDirective(extraction = extraction, sourceLocation = sourceLocation))
        }
        assertions.forEach { assertion ->
            when (assertion) {
                is Assertion.ConditionalAssertion -> {
                    add(ConditionalDirective(assertion = assertion, sourceLocation = assertion.sourceLocation ?: sourceLocation))
                }

                else -> {
                    add(AssertionDirective(assertion = assertion, sourceLocation = assertion.sourceLocation ?: sourceLocation))
                }
            }
        }
        failMessage?.let { message ->
            add(FailDirective(message = message, sourceLocation = sourceLocation))
        }
        fragmentName?.let { name ->
            add(IncludeDirective(fragmentName = name, parameters = includeParameters, sourceLocation = sourceLocation))
        }
        webhookConfig?.let { config ->
            add(WebhookDirective(config = config, sourceLocation = sourceLocation))
        }
    }

data class RawRequest(
    val method: String,
    val path: String,
)

/**
 * Configuration for a webhook mock server.
 *
 * @property name Identifier for the webhook server (used in variable interpolation)
 * @property port Port to listen on (0 = auto-assign random port)
 * @property hooks List of webhook operation IDs to expect
 * @property scope Cleanup scope (SCENARIO or FEATURE level)
 */
data class WebhookConfig(
    val name: String,
    val port: Int,
    val hooks: List<String>,
    val scope: WebhookScope = WebhookScope.SCENARIO,
)
