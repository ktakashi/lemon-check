package io.github.ktakashi.lemoncheck.autotest.provider

import io.github.ktakashi.lemoncheck.autotest.ParameterLocation

/**
 * Provider interface for generating security test payloads.
 *
 * Implement this interface to add custom security test payloads for
 * attack vector testing. Providers are discovered via ServiceLoader,
 * allowing extensions without modifying the core library.
 *
 * ## Example Implementation
 *
 * ```kotlin
 * class NoSqlInjectionProvider : SecurityTestProvider {
 *     override val testType: String = "NoSQLInjection"
 *
 *     override fun applicableLocations(): Set<ParameterLocation> =
 *         setOf(ParameterLocation.BODY, ParameterLocation.QUERY)
 *
 *     override fun generatePayloads(): List<SecurityPayload> = listOf(
 *         SecurityPayload(
 *             name = "MongoDB injection",
 *             payload = "{\"\$ne\": null}",
 *         ),
 *         SecurityPayload(
 *             name = "MongoDB $where",
 *             payload = "{\"\$where\": \"sleep(5000)\"}",
 *         )
 *     )
 * }
 * ```
 *
 * ## Registration
 *
 * Add to `META-INF/services/io.github.ktakashi.lemoncheck.autotest.provider.SecurityTestProvider`:
 * ```
 * com.example.NoSqlInjectionProvider
 * ```
 *
 * @see AutoTestProviderRegistry
 * @see SecurityPayload
 */
interface SecurityTestProvider {
    /**
     * Unique identifier for this security test type.
     *
     * Used for:
     * - Exclude configuration: `excludes: [{testType}]`
     * - User-provided providers override built-in ones with same testType
     */
    val testType: String

    /**
     * Human-readable display name for test reports.
     *
     * Defaults to [testType] if not overridden.
     * Used in: `[security] {displayName}: {payload.name}`
     */
    val displayName: String get() = testType

    /**
     * Parameter locations where this security test applies.
     *
     * For example:
     * - SQL injection typically applies to body and query parameters
     * - Path traversal typically applies to path parameters
     * - Header injection applies to headers
     */
    fun applicableLocations(): Set<ParameterLocation>

    /**
     * Generate security test payloads.
     *
     * @return List of security payloads to test
     */
    fun generatePayloads(): List<SecurityPayload>

    /**
     * Priority of this provider. Higher values = higher priority.
     * User-provided providers default to 100, built-in providers default to 0.
     */
    val priority: Int get() = 0
}

/**
 * Represents a security test payload.
 */
data class SecurityPayload(
    /** Name for test reporting (e.g., "Single quote", "Script tag") */
    val name: String,
    /** The actual payload to inject */
    val payload: String,
)
