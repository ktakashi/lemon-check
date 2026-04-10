package io.github.ktakashi.lemoncheck.logging

import io.github.ktakashi.lemoncheck.openapi.HttpMethod
import java.net.http.HttpResponse
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Default HttpLogger implementation using java.util.logging (JUL).
 *
 * Logs HTTP requests and responses at INFO level with a configurable format.
 * The logger name is "io.github.ktakashi.lemoncheck.http".
 *
 * @property formatter Optional formatter for customizing log output
 * @property logLevel Log level for messages (default: INFO)
 */
class JulHttpLogger(
    private val formatter: HttpLogFormatter = DefaultHttpLogFormatter(),
    private val logLevel: Level = Level.INFO,
) : HttpLogger {
    private val logger = Logger.getLogger("io.github.ktakashi.lemoncheck.http")

    override fun logRequest(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ) {
        if (!logger.isLoggable(logLevel)) return

        val message = formatter.formatRequest(method, url, headers, body)
        logger.log(logLevel, message)
    }

    override fun logResponse(
        method: HttpMethod,
        url: String,
        response: HttpResponse<String>,
        durationMs: Long,
    ) {
        if (!logger.isLoggable(logLevel)) return

        val message = formatter.formatResponse(method, url, response, durationMs)
        logger.log(logLevel, message)
    }
}
