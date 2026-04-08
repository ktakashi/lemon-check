package io.github.ktakashi.lemoncheck.openapi

import io.github.ktakashi.lemoncheck.config.SpecConfiguration
import io.github.ktakashi.lemoncheck.exception.OperationNotFoundException
import io.swagger.v3.oas.models.OpenAPI

/**
 * Registry for managing multiple OpenAPI specifications.
 *
 * Supports both single-spec and multi-spec scenarios.
 */
class SpecRegistry {
    private val specs = mutableMapOf<String, LoadedSpec>()
    private val loader = OpenApiLoader()
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
        val openApi = loader.load(path)
        val resolver = OperationResolver(openApi)

        specs[name] =
            LoadedSpec(
                name = name,
                path = path,
                openApi = openApi,
                resolver = resolver,
                baseUrl = specConfig.baseUrl ?: extractBaseUrl(openApi),
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
    fun get(name: String): LoadedSpec = specs[name] ?: throw IllegalArgumentException("Spec '$name' not found. Available: ${specs.keys}")

    /**
     * Get the default spec.
     */
    fun getDefault(): LoadedSpec {
        val name = defaultSpec ?: throw IllegalStateException("No specs registered")
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
            return spec to spec.resolver.resolve(operationId)
        }

        // Auto-resolve: find all specs containing this operationId
        val matches = specs.values.filter { it.resolver.hasOperation(operationId) }

        return when {
            matches.isEmpty() -> {
                val allOps = specs.values.flatMap { it.resolver.allOperationIds() }
                throw OperationNotFoundException(operationId, allOps)
            }
            matches.size == 1 -> {
                val spec = matches.single()
                spec to spec.resolver.resolve(operationId)
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

    private fun extractBaseUrl(openApi: OpenAPI): String = openApi.servers?.firstOrNull()?.url ?: "http://localhost"
}

/**
 * A loaded OpenAPI specification with resolver and configuration.
 */
data class LoadedSpec(
    val name: String,
    val path: String,
    val openApi: OpenAPI,
    val resolver: OperationResolver,
    val baseUrl: String,
    val defaultHeaders: Map<String, String>,
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
