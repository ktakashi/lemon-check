package org.berrycrush.openapi

import io.swagger.v3.oas.models.OpenAPI
import org.berrycrush.config.SpecConfiguration
import org.berrycrush.exception.ConfigurationException
import org.berrycrush.exception.OperationNotFoundException
import org.berrycrush.openapi.impl.SwaggerParserAdapter

/**
 * Registry for managing multiple OpenAPI specifications.
 *
 * Supports both single-spec and multi-spec scenarios.
 */
class SpecRegistry {
    private val specs = mutableMapOf<String, LoadedSpec>()
    private val parser: OpenApiParser = SwaggerParserAdapter()
    private var defaultSpec: String? = null

    /**
     * Register an OpenAPI specification.
     *
     * @param name Unique identifier for this spec
     * @param path Path to the OpenAPI spec file
     * @param config Optional configuration for this spec
     */
    fun register(
        name: String,
        path: String,
        config: SpecConfiguration.() -> Unit = {},
    ) {
        val specConfig = SpecConfiguration(name, path).apply(config)
        val openApiSpec = parser.parse(path)

        specs[name] =
            LoadedSpec(
                name = name,
                path = path,
                spec = openApiSpec,
                baseUrl = specConfig.baseUrl ?: extractBaseUrl(openApiSpec),
                defaultHeaders = specConfig.defaultHeaders.toMap(),
            )

        // First registered spec becomes default
        if (defaultSpec == null) {
            defaultSpec = name
        }
    }

    /**
     * Register a single spec as the default (simplified API).
     */
    fun registerDefault(
        path: String,
        config: SpecConfiguration.() -> Unit = {},
    ) {
        register("default", path, config)
    }

    /**
     * Get a loaded spec by name.
     */
    fun get(name: String): LoadedSpec =
        specs[name]
            ?: throw ConfigurationException("Spec '$name' not found. Available: ${specs.keys}")

    /**
     * Get the default spec.
     */
    fun getDefault(): LoadedSpec {
        val name = defaultSpec ?: throw ConfigurationException("No specs registered")
        return get(name)
    }

    /**
     * Resolve an operation, optionally specifying which spec to use.
     *
     * If specName is null and operationId is unique across all specs, auto-resolves.
     *
     * @param operationId The operation ID to resolve
     * @param specName Optional spec name to search in
     * @return Pair of spec name and resolved operation
     * @throws OperationNotFoundException if not found
     * @throws AmbiguousOperationException if found in multiple specs without specName
     */
    fun resolve(
        operationId: String,
        specName: String? = null,
    ): Pair<LoadedSpec, ResolvedOperation> {
        if (specName != null) {
            val spec = get(specName)
            val operation =
                spec.spec.getOperation(operationId)
                    ?: throw OperationNotFoundException(operationId, spec.spec.getAllOperations().mapNotNull { it.operationId })
            return spec to operation.toResolvedOperation()
        }

        // Auto-resolve: find all specs containing this operationId
        val matches = specs.values.filter { it.spec.getOperation(operationId) != null }

        return when {
            matches.isEmpty() -> {
                val allOps = specs.values.flatMap { it.spec.getAllOperations().mapNotNull { op -> op.operationId } }
                throw OperationNotFoundException(operationId, allOps)
            }
            matches.size == 1 -> {
                val spec = matches.single()
                val operation = spec.spec.getOperation(operationId)!!
                spec to operation.toResolvedOperation()
            }
            else -> {
                throw AmbiguousOperationException(
                    operationId,
                    matches.map { it.name },
                )
            }
        }
    }

    /**
     * Check if any spec is registered.
     */
    fun hasSpecs(): Boolean = specs.isNotEmpty()

    /**
     * Get all registered spec names.
     */
    fun specNames(): Set<String> = specs.keys.toSet()

    /**
     * Update the base URL for a registered spec.
     *
     * This allows overriding the base URL after spec registration,
     * useful for file-level parameters or runtime configuration.
     *
     * @param name The spec name to update
     * @param newBaseUrl The new base URL
     * @throws IllegalArgumentException if spec is not found
     */
    fun updateBaseUrl(
        name: String,
        newBaseUrl: String,
    ) {
        val existing = specs[name] ?: throw IllegalArgumentException("Spec '$name' not found. Available: ${specs.keys}")
        specs[name] =
            existing.copy(baseUrl = newBaseUrl)
    }

    private fun extractBaseUrl(spec: OpenApiSpec): String = spec.servers.firstOrNull()?.url ?: "http://localhost"
}

/**
 * A loaded OpenAPI specification with resolver and configuration.
 */
data class LoadedSpec(
    val name: String,
    val path: String,
    val spec: OpenApiSpec,
    val baseUrl: String,
    val defaultHeaders: Map<String, String>,
) {
    /**
     * Access the raw swagger OpenAPI model for backward compatibility.
     * Prefer using the spec abstraction when possible.
     */
    val openApi: OpenAPI
        get() = spec.rawModel as OpenAPI

    /**
     * Get an operation by ID.
     */
    fun getOperation(operationId: String): OperationSpec? = spec.getOperation(operationId)

    /**
     * Check if this spec contains the given operation ID.
     */
    fun hasOperation(operationId: String): Boolean = spec.getOperation(operationId) != null

    /**
     * Get all operation IDs in this spec.
     */
    fun allOperationIds(): List<String> = spec.getAllOperations().mapNotNull { it.operationId }
}

/**
 * Convert OperationSpec to ResolvedOperation for backward compatibility.
 */
private fun OperationSpec.toResolvedOperation(): ResolvedOperation =
    ResolvedOperation(
        operationId = this.operationId ?: "",
        path = this.path,
        method = this.method,
        operation = this,
    )

/**
 * Exception thrown when an operationId exists in multiple specs.
 */
class AmbiguousOperationException(
    operationId: String,
    specs: List<String>,
) : RuntimeException(
        "Operation '$operationId' found in multiple specs: ${specs.joinToString(", ")}. " +
            "Use 'using(\"specName\")' to specify which spec to use.",
    )
