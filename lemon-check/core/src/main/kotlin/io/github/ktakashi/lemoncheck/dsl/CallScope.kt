package io.github.ktakashi.lemoncheck.dsl

import java.util.Base64

/**
 * DSL scope for configuring an API call.
 */
@LemonCheckDsl
class CallScope internal constructor() {
    internal val pathParams = mutableMapOf<String, Any>()
    internal val queryParams = mutableMapOf<String, Any>()
    internal val headers = mutableMapOf<String, String>()
    internal var body: String? = null
    internal var autoAssert: Boolean = true

    /**
     * Set a path parameter.
     */
    fun pathParam(
        name: String,
        value: Any,
    ) {
        pathParams[name] = value
    }

    /**
     * Set a query parameter.
     */
    fun queryParam(
        name: String,
        value: Any,
    ) {
        queryParams[name] = value
    }

    /**
     * Set a header.
     */
    fun header(
        name: String,
        value: String,
    ) {
        headers[name] = value
    }

    /**
     * Set the request body (string).
     */
    fun body(content: String) {
        body = content
    }

    /**
     * Set the request body from a map (will be serialized to JSON).
     */
    fun body(content: Map<String, Any?>) {
        body = mapToJson(content)
    }

    /**
     * Disable auto-assertions from OpenAPI spec for this call.
     */
    fun autoAssert(enabled: Boolean) {
        autoAssert = enabled
    }

    // ========== Authentication Shortcuts ==========

    /**
     * Add a Bearer token authentication header.
     */
    fun bearerToken(token: String) {
        headers["Authorization"] = "Bearer $token"
    }

    /**
     * Add Basic authentication header.
     */
    fun basicAuth(
        username: String,
        password: String,
    ) {
        val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        headers["Authorization"] = "Basic $credentials"
    }

    /**
     * Add an API key header.
     */
    fun apiKey(
        headerName: String,
        key: String,
    ) {
        headers[headerName] = key
    }

    /**
     * Add an API key with default header name.
     */
    fun apiKey(key: String) {
        apiKey("X-API-Key", key)
    }

    private fun mapToJson(map: Map<String, Any?>): String {
        // Simple JSON serialization for common types
        return buildString {
            append("{")
            map.entries.forEachIndexed { index, (key, value) ->
                if (index > 0) append(",")
                append("\"$key\":")
                append(valueToJson(value))
            }
            append("}")
        }
    }

    private fun valueToJson(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> "\"${escapeJson(value)}\""
            is Number -> value.toString()
            is Boolean -> value.toString()
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                mapToJson(value as Map<String, Any?>)
            }

            is List<*> -> {
                value.joinToString(",", "[", "]") { valueToJson(it) }
            }

            else -> "\"${escapeJson(value.toString())}\""
        }

    private fun escapeJson(s: String): String =
        s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
