package io.github.ktakashi.lemoncheck.autotest.provider

import io.swagger.v3.oas.models.media.Schema

/**
 * Provider interface for generating invalid request test values.
 *
 * Implement this interface to add custom invalid value generation strategies.
 * Providers are discovered via ServiceLoader, allowing extensions without
 * modifying the core library.
 *
 * ## Example Implementation
 *
 * ```kotlin
 * class NumericOverflowProvider : InvalidTestProvider {
 *     override val testType: String = "numericOverflow"
 *
 *     override fun canHandle(schema: Schema<*>): Boolean =
 *         schema.type == "integer" || schema.type == "number"
 *
 *     override fun generateInvalidValues(
 *         fieldName: String,
 *         schema: Schema<*>,
 *     ): List<InvalidTestValue> = listOf(
 *         InvalidTestValue(
 *             value = Long.MAX_VALUE,
 *             description = "Numeric overflow value",
 *         )
 *     )
 * }
 * ```
 *
 * ## Registration
 *
 * Add to `META-INF/services/io.github.ktakashi.lemoncheck.autotest.provider.InvalidTestProvider`:
 * ```
 * com.example.NumericOverflowProvider
 * ```
 *
 * @see AutoTestProviderRegistry
 * @see InvalidTestValue
 */
interface InvalidTestProvider {
    /**
     * Unique identifier for this test type.
     *
     * Used for:
     * - Display name in test reports: `[Invalid request - {testType}]`
     * - Exclude configuration: `excludes: [{testType}]`
     * - User-provided providers override built-in ones with same testType
     */
    val testType: String

    /**
     * Check if this provider can handle the given schema.
     *
     * @param schema The OpenAPI schema to check
     * @return true if this provider can generate invalid values for the schema
     */
    fun canHandle(schema: Schema<*>): Boolean

    /**
     * Generate invalid test values for the given field and schema.
     *
     * @param fieldName The name of the field being tested
     * @param schema The OpenAPI schema of the field
     * @return List of invalid values with descriptions
     */
    fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue>

    /**
     * Priority of this provider. Higher values = higher priority.
     * User-provided providers default to 100, built-in providers default to 0.
     */
    val priority: Int get() = 0
}

/**
 * Represents an invalid test value with its description.
 */
data class InvalidTestValue(
    /** The invalid value to send */
    val value: Any?,
    /** Human-readable description for test reports */
    val description: String,
)
