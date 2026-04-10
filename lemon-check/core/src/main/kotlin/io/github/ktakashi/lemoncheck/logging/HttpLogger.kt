package io.github.ktakashi.lemoncheck.logging

import io.github.ktakashi.lemoncheck.openapi.HttpMethod
import java.net.http.HttpResponse

/**
 * Interface for logging HTTP requests and responses.
 *
 * Implement this interface to customize how HTTP traffic is logged.
 * The default implementation uses java.util.logging (JUL).
 */
interface HttpLogger {
    /**
     * Log an HTTP request before it is sent.
     *
     * @param method HTTP method (GET, POST, etc.)
     * @param url Full URL being requested
     * @param headers HTTP headers
     * @param body Request body (null for GET/DELETE)
     */
    fun logRequest(
        method: HttpMethod,
        url: String,
        headers: Map<String, String>,
        body: String?,
    )

    /**
     * Log an HTTP response after it is received.
     *
     * @param method HTTP method of the request
     * @param url URL that was requested
     * @param response The HTTP response
     * @param durationMs Duration in milliseconds
     */
    fun logResponse(
        method: HttpMethod,
        url: String,
        response: HttpResponse<String>,
        durationMs: Long,
    )
}

/**
 * Factory for creating HttpLogger instances.
 *
 * Users can set a custom factory to provide their own logger implementation.
 * The default logger is ConsoleHttpLogger which prints to stdout.
 */
object HttpLoggerFactory {
    private var factory: () -> HttpLogger = { ConsoleHttpLogger() }

    /**
     * Set a custom logger factory.
     *
     * Example:
     * ```kotlin
     * HttpLoggerFactory.setFactory { MyCustomLogger() }
     * ```
     */
    fun setFactory(customFactory: () -> HttpLogger) {
        factory = customFactory
    }

    /**
     * Reset to the default console logger factory.
     */
    fun resetToDefault() {
        factory = { ConsoleHttpLogger() }
    }

    /**
     * Set factory to use JUL (java.util.logging) logger.
     *
     * @param formatter Optional custom formatter
     */
    fun useJulLogger(formatter: HttpLogFormatter? = null) {
        factory = {
            if (formatter != null) JulHttpLogger(formatter) else JulHttpLogger()
        }
    }

    /**
     * Create a new HttpLogger instance using the configured factory.
     */
    fun create(): HttpLogger = factory()
}
