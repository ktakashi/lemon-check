package org.berrycrush.assertion

/**
 * Result of a custom assertion execution.
 *
 * Custom assertion methods can return this type to indicate whether the assertion
 * passed or failed, along with an optional message.
 *
 * ## Usage
 *
 * ```kotlin
 * @Assertion("the price should be {float}")
 * fun assertPrice(expected: Float, context: AssertionContext): AssertionResult {
 *     val actual = extractPrice(context.lastResponse)
 *     return if (actual == expected) {
 *         AssertionResult.passed()
 *     } else {
 *         AssertionResult.failed("Expected price $expected but got $actual")
 *     }
 * }
 * ```
 *
 * @property passed Whether the assertion passed
 * @property message Optional message (typically used for failures)
 * @property actualValue Optional actual value for comparison reporting
 * @property expectedValue Optional expected value for comparison reporting
 */
data class AssertionResult(
    val passed: Boolean,
    val message: String? = null,
    val actualValue: Any? = null,
    val expectedValue: Any? = null,
) {
    companion object {
        /**
         * Create a passed assertion result.
         *
         * @param message Optional success message
         * @return A passed AssertionResult
         */
        fun passed(message: String? = null): AssertionResult =
            AssertionResult(passed = true, message = message)

        /**
         * Create a failed assertion result.
         *
         * @param message Failure message describing what went wrong
         * @param actualValue Optional actual value for comparison
         * @param expectedValue Optional expected value for comparison
         * @return A failed AssertionResult
         */
        fun failed(
            message: String,
            actualValue: Any? = null,
            expectedValue: Any? = null,
        ): AssertionResult = AssertionResult(
            passed = false,
            message = message,
            actualValue = actualValue,
            expectedValue = expectedValue,
        )
    }
}
