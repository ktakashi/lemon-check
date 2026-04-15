package org.berrycrush.step

import org.berrycrush.config.BerryCrushConfiguration
import java.net.http.HttpResponse

/**
 * Context provided to custom step implementations.
 *
 * Provides access to test variables, last HTTP response, and configuration.
 * Custom step methods can receive this interface as a parameter to interact
 * with the test execution context.
 *
 * ## Variable Scopes
 *
 * Variables can be set with two different scopes:
 * - **Scenario-scoped** (default): Variables are isolated to the current scenario
 * - **Suite-scoped**: Variables are shared across scenarios when sharing is enabled
 *
 * ## Usage Example
 *
 * ```kotlin
 * @Step("I set up test data for {string}")
 * fun setupTestData(name: String, context: StepContext) {
 *     // Read existing variable
 *     val baseUrl = context.variable<String>("baseUrl")
 *
 *     // Set scenario-scoped variable (not shared across scenarios)
 *     context.setVariable("testName", name)
 *
 *     // Set suite-scoped variable (shared when enabled)
 *     context.setSharedVariable("createdId", "12345")
 *
 *     // Access last HTTP response
 *     val lastStatus = context.lastResponse?.statusCode()
 * }
 * ```
 */
interface StepContext {
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
     * Set a scenario-scoped variable.
     *
     * This variable will only be visible within the current scenario.
     * It will NOT be shared with other scenarios, even if variable sharing is enabled.
     *
     * @param name The variable name
     * @param value The variable value
     */
    fun setVariable(name: String, value: Any?)

    /**
     * Set a suite-scoped (shared) variable.
     *
     * This variable will be shared across scenarios when variable sharing is enabled
     * in the suite configuration. If sharing is disabled, behaves like [setVariable].
     *
     * @param name The variable name
     * @param value The variable value
     */
    fun setSharedVariable(name: String, value: Any?)

    /**
     * Get all current variables.
     *
     * @return Immutable map of all variables (both scenario and shared scopes)
     */
    fun allVariables(): Map<String, Any?>

    /**
     * The last HTTP response received during scenario execution.
     *
     * May be null if no HTTP request has been made yet in the scenario,
     * or if the step is executed before any API calls.
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

/**
 * Extension function to get typed variables using reified type.
 */
inline fun <reified T> StepContext.variable(name: String): T? = variable(name, T::class.java)
