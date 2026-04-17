package org.berrycrush.openapi

/**
 * Abstraction for an OpenAPI specification, supporting multiple versions (3.0.x, 3.1.x, etc.).
 *
 * This interface provides a unified API for accessing OpenAPI spec content
 * regardless of the underlying version, enabling forward compatibility.
 */
interface OpenApiSpec {
    /**
     * The detected major/minor version category.
     */
    val version: OpenApiVersion

    /**
     * The raw version string from the spec (e.g., "3.1.0").
     */
    val specVersion: String

    /**
     * Specification metadata.
     */
    val info: SpecInfo

    /**
     * Server URLs defined in the spec.
     */
    val servers: List<ServerInfo>

    /**
     * Path items (API endpoints).
     */
    val paths: Map<String, PathSpec>

    /**
     * Reusable components (schemas, parameters, etc.).
     */
    val components: ComponentsSpec?

    /**
     * Webhooks defined in the spec (3.1+ only).
     * Returns empty map for specs that don't support webhooks.
     */
    val webhooks: Map<String, PathSpec>

    /**
     * Check if this spec has webhooks defined.
     */
    fun hasWebhooks(): Boolean = webhooks.isNotEmpty()

    /**
     * Check if this spec has components defined.
     */
    fun hasComponents(): Boolean = components != null

    /**
     * Get an operation by its operationId.
     *
     * @param operationId The unique operation identifier
     * @return The operation spec, or null if not found
     */
    fun getOperation(operationId: String): OperationSpec?

    /**
     * Get all operations defined in this spec.
     */
    fun getAllOperations(): List<OperationSpec>

    /**
     * Access to the underlying raw model for advanced use cases.
     * Type depends on the parser implementation.
     */
    val rawModel: Any
}

/**
 * Specification metadata.
 */
data class SpecInfo(
    val title: String,
    val description: String?,
    val version: String,
    val contact: ContactInfo?,
    val license: LicenseInfo?,
)

/**
 * Contact information.
 */
data class ContactInfo(
    val name: String?,
    val url: String?,
    val email: String?,
)

/**
 * License information.
 */
data class LicenseInfo(
    val name: String,
    val url: String?,
    val identifier: String?,
)

/**
 * Server URL configuration.
 */
data class ServerInfo(
    val url: String,
    val description: String?,
    val variables: Map<String, ServerVariable>,
)

/**
 * Server variable for URL templating.
 */
data class ServerVariable(
    val default: String,
    val enum: List<String>?,
    val description: String?,
)
