package io.github.ktakashi.lemoncheck.model

/**
 * Represents an assertion to verify on an API response.
 *
 * @property type Type of assertion to perform
 * @property expected Expected value (for comparison assertions)
 * @property jsonPath JSONPath for body assertions
 * @property headerName Header name for header assertions
 * @property pattern Regex pattern for matching assertions
 * @property negate When true, the assertion result is inverted (NOT)
 */
data class Assertion(
    val type: AssertionType,
    val expected: Any? = null,
    val jsonPath: String? = null,
    val headerName: String? = null,
    val pattern: String? = null,
    val negate: Boolean = false,
)

/**
 * Types of assertions supported.
 */
enum class AssertionType {
    /** Assert response status code equals expected */
    STATUS_CODE,

    /** Assert response body contains string */
    BODY_CONTAINS,

    /** Assert JSONPath value equals expected */
    BODY_EQUALS,

    /** Assert JSONPath value matches regex pattern */
    BODY_MATCHES,

    /** Assert response body array has expected size */
    BODY_ARRAY_SIZE,

    /** Assert response body array is not empty */
    BODY_ARRAY_NOT_EMPTY,

    /** Assert header exists */
    HEADER_EXISTS,

    /** Assert header equals expected value */
    HEADER_EQUALS,

    /** Assert response matches OpenAPI schema */
    MATCHES_SCHEMA,

    /** Assert response time is under threshold (ms) */
    RESPONSE_TIME,
}
