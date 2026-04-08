package io.github.ktakashi.lemoncheck.executor

import java.net.http.HttpResponse

/**
 * Handles HTTP response processing.
 */
class ResponseHandler {
    /**
     * Extract the response body as a string.
     */
    fun getBody(response: HttpResponse<String>): String = response.body() ?: ""

    /**
     * Get response status code.
     */
    fun getStatusCode(response: HttpResponse<String>): Int = response.statusCode()

    /**
     * Get response headers as a map.
     */
    fun getHeaders(response: HttpResponse<String>): Map<String, List<String>> = response.headers().map()

    /**
     * Get a specific header value (first value if multiple).
     */
    fun getHeader(
        response: HttpResponse<String>,
        name: String,
    ): String? = response.headers().firstValue(name).orElse(null)

    /**
     * Get all values for a specific header.
     */
    fun getHeaderValues(
        response: HttpResponse<String>,
        name: String,
    ): List<String> = response.headers().allValues(name)

    /**
     * Check if Content-Type is JSON.
     */
    fun isJsonResponse(response: HttpResponse<String>): Boolean {
        val contentType = getHeader(response, "Content-Type") ?: return false
        return contentType.contains("application/json", ignoreCase = true)
    }

    /**
     * Get Content-Type header.
     */
    fun getContentType(response: HttpResponse<String>): String? = getHeader(response, "Content-Type")
}
