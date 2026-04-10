package io.github.ktakashi.lemoncheck.config

import io.github.ktakashi.lemoncheck.logging.HttpLogFormatter
import io.github.ktakashi.lemoncheck.logging.HttpLogger
import io.github.ktakashi.lemoncheck.logging.HttpLoggerFactory
import java.time.Duration

/**
 * Configuration for LemonCheck test execution.
 *
 * @property baseUrl Base URL for API requests (overrides spec server URL)
 * @property timeout HTTP request timeout
 * @property defaultHeaders Headers to include in all requests
 * @property environment Environment name (e.g., "staging", "production")
 * @property autoAssertions Configuration for auto-generated assertions
 * @property strictSchemaValidation Whether to fail on schema validation warnings
 * @property followRedirects Whether to follow HTTP redirects
 * @property logRequests Whether to log HTTP requests
 * @property logResponses Whether to log HTTP responses
 * @property httpLogger Custom HTTP logger (default: JUL-based logger)
 * @property logFormatter Custom log formatter (default: multi-line human-readable format)
 */
data class Configuration(
    var baseUrl: String? = null,
    var timeout: Duration = Duration.ofSeconds(30),
    val defaultHeaders: MutableMap<String, String> = mutableMapOf(),
    var environment: String? = null,
    var autoAssertions: AutoAssertionConfig = AutoAssertionConfig(),
    var strictSchemaValidation: Boolean = false,
    var followRedirects: Boolean = true,
    var logRequests: Boolean = false,
    var logResponses: Boolean = false,
    /**
     * Custom HTTP logger for request/response logging.
     * Set to null to use the default logger from HttpLoggerFactory.
     */
    var httpLogger: HttpLogger? = null,
    /**
     * Custom log formatter for formatting log messages.
     * Only used if httpLogger is null (using the default logger).
     */
    var logFormatter: HttpLogFormatter? = null,
    /**
     * Whether to share variables across scenarios.
     *
     * When enabled, variables extracted in one scenario are available in subsequent
     * scenarios. This allows for chained scenarios like:
     * - Scenario 1: Create a resource, extract its ID
     * - Scenario 2: Use the extracted ID to fetch or update the resource
     *
     * Default is false (each scenario has isolated variable scope).
     */
    var shareVariablesAcrossScenarios: Boolean = false,
) {
    /**
     * Get the effective HTTP logger.
     * Returns the custom logger if set, otherwise creates one from the factory.
     */
    fun getEffectiveHttpLogger(): HttpLogger = httpLogger ?: HttpLoggerFactory.create()

    /**
     * DSL helper to set timeout in seconds.
     */
    fun timeout(seconds: Long) {
        timeout = Duration.ofSeconds(seconds)
    }

    /**
     * DSL helper to add default header.
     */
    fun header(
        name: String,
        value: String,
    ) {
        defaultHeaders[name] = value
    }

    /**
     * Create a copy of this configuration with parameters applied.
     *
     * Supports the following parameter names:
     * - `baseUrl` - Override the base URL
     * - `timeout` - Request timeout in seconds (number)
     * - `environment` - Environment name
     * - `strictSchemaValidation` - true/false
     * - `followRedirects` - true/false
     * - `logRequests` - true/false
     * - `logResponses` - true/false
     * - `shareVariablesAcrossScenarios` - true/false
     * - `header.<name>` - Add/override a default header
     *
     * @param parameters Map of parameter names to values
     * @return A new Configuration with parameters applied
     */
    fun withParameters(parameters: Map<String, Any>): Configuration {
        val copy =
            this.copy(
                defaultHeaders = this.defaultHeaders.toMutableMap(),
                autoAssertions = this.autoAssertions.copy(),
            )

        for ((key, value) in parameters) {
            when {
                key == "baseUrl" -> copy.baseUrl = value.toString()
                key == "timeout" ->
                    copy.timeout =
                        when (value) {
                            is Number -> Duration.ofSeconds(value.toLong())
                            is String -> Duration.ofSeconds(value.toLong())
                            else -> copy.timeout
                        }
                key == "environment" -> copy.environment = value.toString()
                key == "strictSchemaValidation" -> copy.strictSchemaValidation = value.toString().toBoolean()
                key == "followRedirects" -> copy.followRedirects = value.toString().toBoolean()
                key == "logRequests" -> copy.logRequests = value.toString().toBoolean()
                key == "logResponses" -> copy.logResponses = value.toString().toBoolean()
                key == "shareVariablesAcrossScenarios" -> copy.shareVariablesAcrossScenarios = value.toString().toBoolean()
                key.startsWith("header.") -> {
                    val headerName = key.removePrefix("header.")
                    copy.defaultHeaders[headerName] = value.toString()
                }
                // Auto assertion parameters
                key == "autoAssertions.enabled" -> copy.autoAssertions.enabled = value.toString().toBoolean()
                key == "autoAssertions.statusCode" -> copy.autoAssertions.statusCode = value.toString().toBoolean()
                key == "autoAssertions.contentType" -> copy.autoAssertions.contentType = value.toString().toBoolean()
                key == "autoAssertions.schema" -> copy.autoAssertions.schema = value.toString().toBoolean()
            }
        }

        return copy
    }
}

/**
 * Configuration for automatic assertion generation from OpenAPI spec.
 *
 * @property enabled Whether auto-assertions are enabled globally
 * @property statusCode Auto-assert correct status code
 * @property contentType Auto-assert Content-Type header
 * @property schema Auto-assert response matches schema
 */
data class AutoAssertionConfig(
    var enabled: Boolean = true,
    var statusCode: Boolean = true,
    var contentType: Boolean = true,
    var schema: Boolean = true,
)

/**
 * Configuration for a single OpenAPI specification.
 *
 * @property name Unique identifier for this spec
 * @property path Path to the OpenAPI spec file
 * @property baseUrl Base URL override for this spec
 * @property defaultHeaders Headers specific to this spec
 */
data class SpecConfiguration(
    val name: String,
    val path: String,
    var baseUrl: String? = null,
    val defaultHeaders: MutableMap<String, String> = mutableMapOf(),
) {
    /**
     * DSL helper to add default header for this spec.
     */
    fun header(
        name: String,
        value: String,
    ) {
        defaultHeaders[name] = value
    }
}
