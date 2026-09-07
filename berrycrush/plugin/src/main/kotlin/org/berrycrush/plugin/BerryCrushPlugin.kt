package org.berrycrush.plugin

/**
 * Service Provider Interface (SPI) for berrycrush plugins.
 *
 * Allows users to extend berrycrush with custom lifecycle hooks that execute at key points
 * during scenario execution. Plugins can perform setup, teardown, logging, reporting, or
 * any custom action needed before/after scenarios and steps.
 *
 * ## Plugin Lifecycle
 *
 * Plugins receive events in the following order for each scenario:
 * 1. `onScenarioStart(context)` - before scenario begins
 * 2. For each step:
 *    - `onStepStart(context)` - before step executes
 *    - `onStepEnd(context, result)` - after step completes
 * 3. `onScenarioEnd(context, result)` - after scenario completes
 *
 * ## Priority-Based Execution Order
 *
 * Plugins execute in priority order (lower values first):
 * - Negative priorities (e.g., -100): Setup/infrastructure plugins
 * - Zero (default): Standard plugins (reporting, etc.)
 * - Positive priorities (e.g., 100): Cleanup/finalization plugins
 *
 * Plugins with the same priority execute in registration order.
 *
 * ## Error Handling
 *
 * Any exception thrown by a plugin method will cause the entire test run to fail
 * with an error status. Built-in plugins should handle their own errors gracefully.
 *
 * ## Registration
 *
 * Plugins can be registered via:
 * - Name-based: `@BerryCrushConfiguration(plugins = ["report:json:output.json"])`
 * - Class-based: `@BerryCrushConfiguration(pluginClasses = [MyPlugin::class])`
 * - SPI Discovery: `META-INF/services/org.berrycrush.plugin.BerryCrushPlugin`
 *
 * @see org.berrycrush.plugin.ScenarioContext
 * @see org.berrycrush.plugin.StepContext
 * @see org.berrycrush.plugin.ScenarioResult
 * @see org.berrycrush.plugin.StepResult
 */
interface BerryCrushPlugin {
    /**
     * Unique identifier for this plugin.
     *
     * Must be unique within a test run. Defaults to the fully-qualified class name.
     *
     * @return Unique plugin ID
     */
    val id: String
        get() = this::class.qualifiedName ?: "unknown"

    /**
     * Plugin priority for execution ordering.
     *
     * Lower priority values execute first. Use negative values for setup plugins,
     * zero (default) for standard plugins, and positive values for cleanup plugins.
     *
     * Example priorities:
     * - `-100`: Early setup (e.g., test data initialization)
     * - `0`: Default (e.g., reporting, logging)
     * - `100`: Late cleanup (e.g., resource teardown)
     *
     * @return Priority value (default: 0)
     */
    val priority: Int
        get() = 0

    /**
     * Human-readable plugin name for logging and debugging.
     *
     * Defaults to the simple class name. Override to provide a custom name.
     *
     * @return Plugin display name
     */
    val name: String
        get() = this::class.simpleName ?: "Unknown Plugin"

    /**
     * Called once before the first scenario starts.
     *
     * Use this hook to perform one-time setup at the beginning of test execution,
     * such as initializing test data, starting external services, or setting up
     * shared resources.
     */
    fun onTestExecutionStart() {
        // Default: no-op
    }

    /**
     * Called once after all scenarios have completed.
     *
     * Use this hook to perform cleanup or finalization at the end of test execution,
     * such as generating reports, stopping services, or cleaning up resources.
     * This is called regardless of whether scenarios passed or failed.
     */
    fun onTestExecutionEnd() {
        // Default: no-op
    }

    /**
     * Called before each scenario starts.
     *
     * Use this hook to perform scenario-level setup, initialize resources,
     * or log scenario start events.
     *
     * @param context Scenario execution context with metadata and variables
     */
    fun onScenarioStart(context: ScenarioContext) {
        // Default: no-op
    }

    /**
     * Called after each scenario ends (regardless of pass/fail status).
     *
     * Use this hook to perform scenario-level cleanup, generate reports,
     * or log scenario completion events.
     *
     * @param context Scenario execution context
     * @param result Scenario execution result with status and step results
     */
    fun onScenarioEnd(
        context: ScenarioContext,
        result: ScenarioResult,
    ) {
        // Default: no-op
    }

    /**
     * Called before each step starts.
     *
     * Use this hook to perform step-level setup, log step start events,
     * or capture pre-execution state.
     *
     * @param context Step execution context with request details and parent scenario
     */
    fun onStepStart(context: StepContext) {
        // Default: no-op
    }

    /**
     * Called after each step ends (regardless of pass/fail status).
     *
     * Use this hook to perform step-level cleanup, log step completion,
     * or capture post-execution state.
     *
     * @param context Step execution context
     * @param result Step execution result with status and failure details
     */
    fun onStepEnd(
        context: StepContext,
        result: StepResult,
    ) {
        // Default: no-op
    }
}
