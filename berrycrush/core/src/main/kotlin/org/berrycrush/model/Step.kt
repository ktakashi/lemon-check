package org.berrycrush.model

import org.berrycrush.scenario.SourceLocation

/**
 * Represents a body property value, which can be either a simple value or a nested object.
 */
sealed class BodyProperty {
    /** A simple value (string, number, boolean, etc.) */
    data class Simple(
        val value: Any,
    ) : BodyProperty()

    /** A nested object with properties */
    data class Nested(
        val properties: Map<String, BodyProperty>,
    ) : BodyProperty()
}

/**
 * Represents a single BDD step within a scenario.
 *
 * @property type The BDD keyword type (GIVEN, WHEN, THEN, etc.)
 * @property description Human-readable description of the step
 * @property operationId OpenAPI operation ID to invoke
 * @property specName Name of the OpenAPI spec to use (for multi-spec scenarios)
 * @property pathParams Path parameters for the API call
 * @property queryParams Query parameters for the API call
 * @property headers HTTP headers to include
 * @property body Request body content (inline raw JSON)
 * @property bodyProperties Structured body properties to merge with schema defaults
 * @property bodyFile External file reference for request body.
 *                    Supports: classpath:path/to/file.json, file:./relative/path.json, or /absolute/path.json
 *                    Variables in the file content are interpolated at runtime.
 * @property extractions Values to extract from response
 * @property assertions Assertions to verify on response
 * @property customAssertions Custom assertions with programmatic logic
 * @property conditionals Conditional assertions (if/else if/else branches)
 * @property failMessage If set, fail with this message unconditionally
 * @property autoAssert Whether to generate assertions from OpenAPI spec
 * @property autoTestConfig Configuration for auto-generating invalid/security tests
 * @property fragmentName Name of fragment to include (for fragment steps)
 * @property sourceLocation Optional source location for error reporting
 */
data class Step(
    val type: StepType,
    val description: String,
    val operationId: String? = null,
    val specName: String? = null,
    val pathParams: Map<String, Any> = emptyMap(),
    val queryParams: Map<String, Any> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val bodyProperties: Map<String, BodyProperty>? = null,
    val bodyFile: String? = null,
    val extractions: List<Extraction> = emptyList(),
    val assertions: List<Assertion> = emptyList(),
    val customAssertions: List<CustomAssertionDefinition> = emptyList(),
    val conditionals: List<ConditionalAssertion> = emptyList(),
    val failMessage: String? = null,
    val autoAssert: Boolean = true,
    val autoTestConfig: AutoTestConfig? = null,
    val fragmentName: String? = null,
    val sourceLocation: SourceLocation? = null,
)
