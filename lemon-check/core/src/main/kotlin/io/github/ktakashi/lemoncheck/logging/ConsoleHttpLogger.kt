package io.github.ktakashi.lemoncheck.logging

import io.github.ktakashi.lemoncheck.openapi.HttpMethod
import java.net.http.HttpResponse

/**
 * Simple console-based HttpLogger that prints to stdout.
 *
 * This logger is useful for debugging and local development.
 * It prints HTTP request/response details directly to System.out.
 *
 * @property formatter Log formatter (default: DefaultHttpLogFormatter)
 */
class ConsoleHttpLogger(
    private val formatter: HttpLogFormatter = DefaultHttpLogFormatter(),
) : HttpLogger {
    override fun logRequest(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ) {
        val message = formatter.formatRequest(method, url, headers, body)
        println(message)
    }

    override fun logResponse(
        method: HttpMethod,
        url: String,
        response: HttpResponse<String>,
        durationMs: Long,
    ) {
        val message = formatter.formatResponse(method, url, response, durationMs)
        println(message)
    }
}
