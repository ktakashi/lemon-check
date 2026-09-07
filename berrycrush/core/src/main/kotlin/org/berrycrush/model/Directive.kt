package org.berrycrush.model

/**
 * A single unit of behavior inside a [Step].
 */
sealed interface Directive {
    /** Source location of the directive, when it originates from a parsed file. */
    val sourceLocation: SourceLocation?

    /** Performs an HTTP call, either by OpenAPI operation or by raw method/path. */
    data class CallDirective(
        val operationId: String? = null,
        val rawRequest: RawRequest? = null,
        val specName: String? = null,
        val pathParams: Map<String, Any> = emptyMap(),
        val queryParams: Map<String, Any> = emptyMap(),
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val bodyProperties: Map<String, BodyProperty>? = null,
        val bodyFile: String? = null,
        val autoAssert: Boolean = true,
        val autoTestConfig: AutoTestConfig? = null,
        override val sourceLocation: SourceLocation? = null,
    ) : Directive

    /** Verifies a condition on the response. */
    data class AssertionDirective(
        val assertion: Assertion,
        override val sourceLocation: SourceLocation? = assertion.sourceLocation,
    ) : Directive

    /** Represents a conditional assertion branch structure. */
    data class ConditionalDirective(
        val assertion: Assertion.ConditionalAssertion,
        override val sourceLocation: SourceLocation? = assertion.sourceLocation,
    ) : Directive

    /** Extracts a value from the response into a variable. */
    data class ExtractionDirective(
        val extraction: Extraction,
        override val sourceLocation: SourceLocation? = null,
    ) : Directive

    /** Includes a reusable fragment, optionally with parameters. */
    data class IncludeDirective(
        val fragmentName: String,
        val parameters: Map<String, Any?> = emptyMap(),
        override val sourceLocation: SourceLocation? = null,
    ) : Directive

    /** Fails the step unconditionally with a message. */
    data class FailDirective(
        val message: String,
        override val sourceLocation: SourceLocation? = null,
    ) : Directive

    /** Starts a mock webhook server for the step. */
    data class WebhookDirective(
        val config: WebhookConfig,
        override val sourceLocation: SourceLocation? = null,
    ) : Directive
}

/** First directive of type [T], or `null`. */
inline fun <reified T : Directive> List<Directive>.firstDirectiveOrNull(): T? = firstNotNullOfOrNull { it as? T }

/** The call directive of this step, if the step performs an HTTP call. */
val Step.callDirective: Directive.CallDirective?
    get() = directives.firstDirectiveOrNull()

/** The fragment include directive of this step, if any. */
val Step.includeDirective: Directive.IncludeDirective?
    get() = directives.firstDirectiveOrNull()

/** The webhook directive of this step, if any. */
val Step.webhookDirective: Directive.WebhookDirective?
    get() = directives.firstDirectiveOrNull()

/** The fail directive of this step, if any. */
val Step.failDirective: Directive.FailDirective?
    get() = directives.firstDirectiveOrNull()

/** All assertions of this step, in declaration order. */
val Step.directiveAssertions: List<Assertion>
    get() =
        directives.flatMap { directive ->
            when (directive) {
                is Directive.AssertionDirective -> {
                    listOf(directive.assertion)
                }

                is Directive.ConditionalDirective -> {
                    listOf(directive.assertion)
                }

                else -> {
                    emptyList()
                }
            }
        }

/** All extractions of this step, in declaration order. */
val Step.directiveExtractions: List<Extraction>
    get() = directives.filterIsInstance<Directive.ExtractionDirective>().map { it.extraction }
