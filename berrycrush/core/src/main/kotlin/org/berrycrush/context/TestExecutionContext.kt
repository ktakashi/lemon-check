package org.berrycrush.context

import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Represents a request-response pair in the execution history.
 */
data class RequestResponsePair(
    val request: HttpRequest,
    val response: HttpResponse<String>,
)

/**
 * Interface representing the test execution context accessible from custom assertions
 * and conditional blocks in the DSL.
 *
 * This provides a read-write view of the current test state, allowing custom assertions
 * to inspect responses, access variables, and store extracted values for subsequent steps.
 *
 * ## Example usage in custom assertion:
 * ```kotlin
 * assert("response contains user ID") { ctx ->
 *     val userId = ctx.response?.body()?.let {
 *         // parse and extract user ID
 *     }
 *     require(userId != null) { "User ID not found in response" }
 *     ctx.extract("userId", userId)
 * }
 * ```
 */
interface TestExecutionContext {
    /**
     * The most recent HTTP response, or null if no request has been made yet.
     */
    val response: HttpResponse<String>?

    /**
     * The most recent HTTP request, or null if no request has been made yet.
     */
    val request: HttpRequest?

    /**
     * The history of all request-response pairs in this scenario.
     */
    val history: List<RequestResponsePair>

    /**
     * Read-only view of all variables in the context.
     */
    val variables: Map<String, Any?>

    /**
     * The response body as a string, or null if no response.
     */
    val responseBody: String?
        get() = response?.body()

    /**
     * The response status code, or null if no response.
     */
    val statusCode: Int?
        get() = response?.statusCode()

    /**
     * Get a previously extracted value by name.
     *
     * @param name The extraction variable name
     * @return The extracted value, or null if not found
     */
    fun extractedValue(name: String): Any?

    /**
     * Get a context value with type casting.
     *
     * @param T The expected type
     * @param key The variable key
     * @return The value cast to T, or null if not found or wrong type
     */
    fun <T> get(key: String): T?

    /**
     * Store a value that can be accessed by subsequent assertions/steps.
     *
     * This is useful for passing data between custom assertions and steps.
     *
     * @param T The value type
     * @param key The storage key
     * @param value The value to store
     */
    fun <T : Any> set(
        key: String,
        value: T,
    )

    /**
     * Store an extracted value with a specific name for parameter binding.
     *
     * This is similar to the `extractTo` DSL but for programmatic use in custom assertions.
     * Extracted values can be referenced in subsequent step parameters using `$name` syntax.
     *
     * @param name The extraction variable name
     * @param value The value to store
     */
    fun extract(
        name: String,
        value: Any,
    )
}

/**
 * Mutable implementation of TestExecutionContext backed by ExecutionContext.
 */
internal class MutableTestExecutionContext(
    private val executionContext: ExecutionContext,
) : TestExecutionContext {
    private val requestHistory = mutableListOf<RequestResponsePair>()

    override val response: HttpResponse<String>?
        get() = executionContext.lastResponse

    override val request: HttpRequest?
        get() = executionContext.lastResponse?.request()

    override val history: List<RequestResponsePair>
        get() = requestHistory.toList()

    override val variables: Map<String, Any?>
        get() = executionContext.allVariables()

    override fun extractedValue(name: String): Any? = executionContext[name]

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String): T? = executionContext[key]

    override fun <T : Any> set(
        key: String,
        value: T,
    ) {
        executionContext[key] = value
    }

    override fun extract(
        name: String,
        value: Any,
    ) {
        executionContext[name] = value
    }

    /**
     * Record a request-response pair in the history.
     */
    internal fun recordRequestResponse(
        request: HttpRequest,
        response: HttpResponse<String>,
    ) {
        requestHistory.add(RequestResponsePair(request, response))
        executionContext.updateLastResponse(response)
    }
}
