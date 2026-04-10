package io.github.ktakashi.lemoncheck.model

import io.github.ktakashi.lemoncheck.scenario.SourceLocation

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
 * @property body Request body content (inline)
 * @property bodyFile External file reference for request body.
 *                    Supports: classpath:path/to/file.json, file:./relative/path.json, or /absolute/path.json
 *                    Variables in the file content are interpolated at runtime.
 * @property extractions Values to extract from response
 * @property assertions Assertions to verify on response
 * @property autoAssert Whether to generate assertions from OpenAPI spec
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
    val bodyFile: String? = null,
    val extractions: List<Extraction> = emptyList(),
    val assertions: List<Assertion> = emptyList(),
    val autoAssert: Boolean = true,
    val fragmentName: String? = null,
    val sourceLocation: SourceLocation? = null,
)
