package io.github.ktakashi.lemoncheck.junit

import io.github.ktakashi.lemoncheck.config.Configuration

/**
 * Interface for providing runtime bindings to scenario execution.
 *
 * Implement this interface to provide custom variable bindings and configuration
 * that will be available during scenario execution. Common use cases include:
 * - Providing a dynamic base URL (e.g., from Spring's @LocalServerPort)
 * - Injecting authentication tokens
 * - Setting up test-specific configuration
 *
 * Example:
 * ```java
 * public class PetstoreBindings implements LemonCheckBindings {
 *     @LocalServerPort
 *     private int port;
 *
 *     @Override
 *     public Map<String, Object> getBindings() {
 *         return Map.of("baseUrl", "http://localhost:" + port + "/api/v1");
 *     }
 * }
 * ```
 */
interface LemonCheckBindings {
    /**
     * Returns variable bindings available to scenarios.
     *
     * The returned map contains name-value pairs that can be referenced
     * in scenario files using the `${name}` syntax.
     *
     * Common bindings include:
     * - `baseUrl`: The base URL for API requests
     * - `authToken`: Authentication token for secured endpoints
     *
     * @return Map of binding names to their values
     */
    fun getBindings(): Map<String, Any>

    /**
     * Optional: Override the OpenAPI spec path.
     *
     * Return a classpath path to the OpenAPI specification file.
     * If null, the path from @LemonCheckConfiguration or @LemonCheckSpec will be used.
     *
     * @return OpenAPI spec path, or null to use default
     */
    fun getOpenApiSpec(): String? = null

    /**
     * Optional: Register additional named OpenAPI specs.
     *
     * Returns a map of spec names to their classpath paths.
     * These specs will be registered in addition to the default spec.
     * Operations can reference these specs using the `specName:operationId` syntax.
     *
     * Example:
     * ```java
     * @Override
     * public Map<String, String> getAdditionalSpecs() {
     *     return Map.of("auth", "auth.yaml", "admin", "admin.yaml");
     * }
     * ```
     *
     * @return Map of spec names to their paths, or empty map if none
     */
    fun getAdditionalSpecs(): Map<String, String> = emptyMap()

    /**
     * Optional: Provide per-spec base URLs for multi-host API testing.
     *
     * Returns a map of spec names to their base URLs. This allows different
     * API specifications to target different hosts or ports.
     *
     * Example:
     * ```java
     * @Override
     * public Map<String, String> getSpecBaseUrls() {
     *     return Map.of(
     *         "default", "http://localhost:" + petstorePort + "/api",
     *         "auth", "http://localhost:" + authPort + "/auth",
     *         "inventory", "http://localhost:" + inventoryPort + "/api"
     *     );
     * }
     * ```
     *
     * @return Map of spec names to their base URLs, or empty map to use defaults
     */
    fun getSpecBaseUrls(): Map<String, String> = emptyMap()

    /**
     * Optional: Configure the execution context.
     *
     * Called before scenario execution begins. Use this to perform
     * additional configuration on the execution context.
     *
     * @param config The configuration to modify
     */
    fun configure(config: Configuration) {}
}
