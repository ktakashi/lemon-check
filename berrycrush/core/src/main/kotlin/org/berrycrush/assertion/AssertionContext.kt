package org.berrycrush.assertion

import org.berrycrush.config.BerryCrushConfiguration
import java.net.http.HttpResponse

/**
 * Context provided to custom assertion implementations.
 *
 * Provides access to test variables, last HTTP response, and configuration.
 * Custom assertion methods can receive this interface as a parameter to evaluate
 * assertion conditions.
 *
 * ## Usage Example
 *
 * ```kotlin
 * @Assertion("the response should contain {int} pets")
 * fun assertPetCount(expected: Int, context: AssertionContext): AssertionResult {
 *     val response = context.lastResponse
 *         ?: return AssertionResult.failed("No HTTP response available")
 *
 *     val pets = JsonPath.read<List<*>>(response.body(), "$.pets")
 *     val actual = pets.size
 *
 *     return if (actual == expected) {
 *         AssertionResult.passed()
 *     } else {
 *         AssertionResult.failed(
 *             message = "Expected $expected pets but got $actual",
 *             expectedValue = expected,
 *             actualValue = actual
 *         )
 *     }
 * }
 * ```
 */
interface AssertionContext {
    /**
     * Get a variable by name.
     *
     * @param name The variable name
     * @return The variable value, or null if not found
     */
    fun variable(name: String): Any?

    /**
     * Get a typed variable by name.
     *
     * @param T The expected type
     * @param name The variable name
     * @return The variable value cast to T, or null if not found or wrong type
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> variable(name: String, type: Class<T>): T? = variable(name) as? T

    /**
     * Get all current variables.
     *
     * @return Immutable map of all variables
     */
    fun allVariables(): Map<String, Any?>

    /**
     * The last HTTP response received during scenario execution.
     *
     * May be null if no HTTP request has been made yet in the scenario,
     * or if the assertion is evaluated before any API calls.
     */
    val lastResponse: HttpResponse<String>?

    /**
     * The current execution configuration.
     *
     * Provides read-only access to the configuration settings like
     * base URL, timeout, and other execution parameters.
     */
    val configuration: BerryCrushConfiguration
}
