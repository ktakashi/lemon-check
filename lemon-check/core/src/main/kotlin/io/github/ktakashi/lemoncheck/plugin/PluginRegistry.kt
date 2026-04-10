package io.github.ktakashi.lemoncheck.plugin

import kotlin.reflect.KClass

/**
 * Registry for managing plugin lifecycle and execution.
 *
 * Handles plugin registration (name-based and class-based), maintains execution order
 * based on plugin priorities, and dispatches lifecycle events to all registered plugins.
 *
 * ## Priority-Based Ordering
 *
 * Plugins are sorted by priority (lower values execute first). Plugins with the same
 * priority execute in registration order.
 *
 * ## Error Handling
 *
 * Plugin errors are fail-fast: any exception thrown by a plugin method will propagate
 * immediately, failing the entire test run.
 */
class PluginRegistry {
    private val plugins = mutableListOf<LemonCheckPlugin>()
    private val sortedPlugins: List<LemonCheckPlugin>
        get() = plugins.sortedBy { it.priority }

    /**
     * Register a plugin instance directly.
     *
     * @param plugin Plugin instance to register
     * @throws IllegalArgumentException if plugin with same ID already registered
     */
    fun register(plugin: LemonCheckPlugin) {
        require(plugins.none { it.id == plugin.id }) {
            "Plugin with ID '${plugin.id}' is already registered"
        }
        plugins.add(plugin)
    }

    /**
     * Register a plugin by class.
     *
     * Creates a new instance using the no-arg constructor.
     *
     * @param pluginClass Plugin class to instantiate and register
     * @throws IllegalArgumentException if plugin with same ID already registered
     * @throws IllegalStateException if class has no no-arg constructor
     */
    fun register(pluginClass: KClass<out LemonCheckPlugin>) {
        val plugin = pluginClass.java.getDeclaredConstructor().newInstance()
        register(plugin)
    }

    /**
     * Register a plugin by name with optional parameters.
     *
     * Resolves built-in plugin names (e.g., "report:json:path.json") to plugin instances.
     * Custom plugin names are resolved via the plugin's `name` property.
     *
     * Name format: `<plugin-name>[:<param1>[:<param2>]]`
     *
     * Examples:
     * - `"report:json"` - JSON report with default path
     * - `"report:json:custom/output.json"` - JSON report with custom path
     * - `"logging"` - Simple plugin with no parameters
     *
     * @param pluginName Plugin name with optional colon-separated parameters
     * @throws IllegalArgumentException if plugin name is not recognized
     */
    fun registerByName(pluginName: String) {
        val plugin = PluginNameResolver.resolve(pluginName)
        register(plugin)
    }

    /**
     * Replace an existing plugin with a new one.
     *
     * If a plugin with the same ID exists, it will be removed and replaced.
     * If no plugin with the ID exists, the new plugin is simply registered.
     *
     * This is useful for resetting plugin state between test batches without
     * modifying the plugin's internal state.
     *
     * @param plugin Plugin instance to register (replacing any existing with same ID)
     */
    fun replace(plugin: LemonCheckPlugin) {
        plugins.removeIf { it.id == plugin.id }
        plugins.add(plugin)
    }

    /**
     * Dispatch onTestExecutionStart to all plugins in priority order.
     *
     * Call this once before the first scenario starts.
     *
     * @throws Any exception thrown by a plugin will propagate immediately
     */
    fun dispatchTestExecutionStart() {
        sortedPlugins.forEach { it.onTestExecutionStart() }
    }

    /**
     * Dispatch onTestExecutionEnd to all plugins in priority order.
     *
     * Call this once after all scenarios have completed.
     *
     * @throws Any exception thrown by a plugin will propagate immediately
     */
    fun dispatchTestExecutionEnd() {
        sortedPlugins.forEach { it.onTestExecutionEnd() }
    }

    /**
     * Dispatch onScenarioStart to all plugins in priority order.
     *
     * @param context Scenario execution context
     * @throws Any exception thrown by a plugin will propagate immediately
     */
    fun dispatchScenarioStart(context: ScenarioContext) {
        sortedPlugins.forEach { it.onScenarioStart(context) }
    }

    /**
     * Dispatch onScenarioEnd to all plugins in priority order.
     *
     * @param context Scenario execution context
     * @param result Scenario execution result
     * @throws Any exception thrown by a plugin will propagate immediately
     */
    fun dispatchScenarioEnd(
        context: ScenarioContext,
        result: ScenarioResult,
    ) {
        sortedPlugins.forEach { it.onScenarioEnd(context, result) }
    }

    /**
     * Dispatch onStepStart to all plugins in priority order.
     *
     * @param context Step execution context
     * @throws Any exception thrown by a plugin will propagate immediately
     */
    fun dispatchStepStart(context: StepContext) {
        sortedPlugins.forEach { it.onStepStart(context) }
    }

    /**
     * Dispatch onStepEnd to all plugins in priority order.
     *
     * @param context Step execution context
     * @param result Step execution result
     */
    fun dispatchStepEnd(
        context: StepContext,
        result: StepResult,
    ) {
        sortedPlugins.forEach { it.onStepEnd(context, result) }
    }

    /**
     * Get all registered plugins in priority order.
     *
     * @return List of plugins sorted by priority (lower values first)
     */
    fun getPlugins(): List<LemonCheckPlugin> = sortedPlugins

    /**
     * Clear all registered plugins.
     *
     * Useful for testing or reinitializing the registry.
     */
    fun clear() {
        plugins.clear()
    }
}
