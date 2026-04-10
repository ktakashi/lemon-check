package io.github.ktakashi.lemoncheck.logging

import io.github.ktakashi.lemoncheck.openapi.HttpMethod
import java.net.http.HttpResponse

/**
 * Interface for formatting HTTP request/response log messages.
 *
 * Implement this interface to customize the log output format.
 */
interface HttpLogFormatter {
    /**
     * Format an HTTP request for logging.
     *
     * @param method HTTP method
     * @param url Request URL
     * @param headers Request headers
     * @param body Request body (null for requests without body)
     * @return Formatted log message
     */
    fun formatRequest(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): String

    /**
     * Format an HTTP response for logging.
     *
     * @param method HTTP method of the request
     * @param url Request URL
     * @param response The HTTP response
     * @param durationMs Request duration in milliseconds
     * @return Formatted log message
     */
    fun formatResponse(
        method: HttpMethod,
        url: String,
        response: HttpResponse<String>,
        durationMs: Long,
    ): String
}

/**
 * Default log formatter with a human-readable multi-line format.
 *
 * Request format:
 * ```
 * ▶ HTTP Request
 *   POST https://api.example.com/users
 *   Headers: {Content-Type=application/json, Authorization=***}
 *   Body: {"name": "John"}
 * ```
 *
 * Response format:
 * ```
 * ◀ HTTP Response [200 OK] (125ms)
 *   POST https://api.example.com/users
 *   Headers: {Content-Type=application/json}
 *   Body: {"id": 123, "name": "John"}
 * ```
 *
 * @property includeHeaders Whether to include headers (default: true)
 * @property includeBody Whether to include request/response body (default: true)
 * @property maxBodyLength Maximum body length before truncation (default: 1000)
 * @property maskSensitiveHeaders Whether to mask sensitive headers like Authorization (default: true)
 */
class DefaultHttpLogFormatter(
    private val includeHeaders: Boolean = true,
    private val includeBody: Boolean = true,
    private val maxBodyLength: Int = 1000,
    private val maskSensitiveHeaders: Boolean = true,
) : HttpLogFormatter {
    private val sensitiveHeaders =
        setOf(
            "authorization",
            "x-api-key",
            "api-key",
            "cookie",
            "set-cookie",
            "x-auth-token",
        )

    override fun formatRequest(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("▶ HTTP Request")
        sb.appendLine("  $method $url")

        if (includeHeaders && headers.isNotEmpty()) {
            val maskedHeaders = maskHeaders(headers)
            sb.appendLine("  Headers: $maskedHeaders")
        }

        if (includeBody && body != null) {
            val truncatedBody = truncateBody(body)
            sb.append("  Body: $truncatedBody")
        }

        return sb.toString().trimEnd()
    }

    override fun formatResponse(
        method: HttpMethod,
        url: String,
        response: HttpResponse<String>,
        durationMs: Long,
    ): String {
        val sb = StringBuilder()
        val statusText = getStatusText(response.statusCode())
        sb.appendLine("◀ HTTP Response [${response.statusCode()} $statusText] (${durationMs}ms)")
        sb.appendLine("  $method $url")

        if (includeHeaders) {
            val responseHeaders = response.headers().map().mapValues { it.value.joinToString(", ") }
            if (responseHeaders.isNotEmpty()) {
                val maskedHeaders = maskHeaders(responseHeaders)
                sb.appendLine("  Headers: $maskedHeaders")
            }
        }

        if (includeBody && response.body().isNotEmpty()) {
            val truncatedBody = truncateBody(response.body())
            sb.append("  Body: $truncatedBody")
        }

        return sb.toString().trimEnd()
    }

    private fun maskHeaders(headers: Map<String, String>): Map<String, String> {
        if (!maskSensitiveHeaders) return headers
        return headers.mapValues { (key, value) ->
            if (sensitiveHeaders.contains(key.lowercase())) "***" else value
        }
    }

    private fun truncateBody(body: String): String =
        if (body.length > maxBodyLength) {
            body.take(maxBodyLength) + "... [truncated, ${body.length} total chars]"
        } else {
            body
        }

    private fun getStatusText(statusCode: Int): String =
        when (statusCode) {
            200 -> "OK"
            201 -> "Created"
            204 -> "No Content"
            301 -> "Moved Permanently"
            302 -> "Found"
            304 -> "Not Modified"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            409 -> "Conflict"
            422 -> "Unprocessable Entity"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> ""
        }
}

/**
 * Compact single-line log formatter.
 *
 * Request: `▶ POST /users {body: 50 chars}`
 * Response: `◀ 200 OK (125ms) {body: 100 chars}`
 */
class CompactHttpLogFormatter : HttpLogFormatter {
    override fun formatRequest(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): String {
        val bodyInfo = body?.let { " {body: ${it.length} chars}" } ?: ""
        return "▶ $method $url$bodyInfo"
    }

    override fun formatResponse(
        method: HttpMethod,
        url: String,
        response: HttpResponse<String>,
        durationMs: Long,
    ): String {
        val bodyInfo = response.body().takeIf { it.isNotEmpty() }?.let { " {body: ${it.length} chars}" } ?: ""
        return "◀ ${response.statusCode()} (${durationMs}ms) $method $url$bodyInfo"
    }
}
