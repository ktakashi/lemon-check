package org.berrycrush.assertion

/**
 * Marks a method as a custom assertion definition.
 *
 * Apply this annotation to methods in assertion definition classes to define custom assertions.
 * The annotated method will be called when an assertion matches the specified pattern.
 *
 * ## Pattern Syntax
 *
 * Patterns support placeholder extraction:
 * - `{int}` - matches an integer value
 * - `{string}` - matches a quoted string ("..." or '...')
 * - `{word}` - matches a single word (alphanumeric + underscore)
 * - `{float}` - matches a floating-point number
 * - `{any}` - matches any text until end or next placeholder
 *
 * ## Return Value
 *
 * Custom assertion methods should return an [AssertionResult] to indicate success or failure.
 * If the method returns void/Unit, success is assumed (unless an exception is thrown).
 * If the method throws an [AssertionError], it's treated as a failed assertion.
 * Other exceptions are treated as errors.
 *
 * ## Example
 *
 * ```kotlin
 * class MyAssertions {
 *     @Assertion("the {word} should have status {string}")
 *     fun assertStatus(entityType: String, expectedStatus: String, context: AssertionContext): AssertionResult {
 *         val actual = context.lastResponse?.let { parseStatus(it, entityType) }
 *         return if (actual == expectedStatus) {
 *             AssertionResult.passed()
 *         } else {
 *             AssertionResult.failed("Expected status '$expectedStatus' but got '$actual'")
 *         }
 *     }
 *
 *     @Assertion("the response should contain {int} items")
 *     fun assertItemCount(expected: Int, context: AssertionContext) {
 *         val actual = context.lastResponse?.let { countItems(it) } ?: 0
 *         assert(actual == expected) { "Expected $expected items but got $actual" }
 *     }
 * }
 * ```
 *
 * @property pattern The assertion pattern with optional placeholders
 * @property description Optional description for documentation
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class Assertion(
    val pattern: String,
    val description: String = "",
)
