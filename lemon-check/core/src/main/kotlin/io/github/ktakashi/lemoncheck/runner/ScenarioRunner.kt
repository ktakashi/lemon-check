package io.github.ktakashi.lemoncheck.runner

import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.context.ExecutionContext
import io.github.ktakashi.lemoncheck.executor.ScenarioExecutor
import io.github.ktakashi.lemoncheck.model.FragmentRegistry
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.ScenarioResult
import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
import io.github.ktakashi.lemoncheck.plugin.PluginRegistry
import java.time.Duration
import java.time.Instant

/**
 * Result of running multiple scenarios.
 */
data class RunResult(
    /**
     * Overall status of the test run.
     */
    val status: ResultStatus,
    /**
     * Total duration of all scenarios.
     */
    val duration: Duration,
    /**
     * Individual scenario results with their names.
     */
    val scenarioResults: List<Pair<Scenario, ScenarioResult>>,
    /**
     * Number of scenarios that passed.
     */
    val passed: Int,
    /**
     * Number of scenarios that failed.
     */
    val failed: Int,
    /**
     * Number of scenarios that were skipped.
     */
    val skipped: Int,
    /**
     * Number of scenarios that had errors.
     */
    val errors: Int,
)

/**
 * Reusable scenario runner that handles the full test lifecycle.
 *
 * This class encapsulates:
 * - Test execution start/end lifecycle hooks
 * - Scenario execution with plugin callbacks
 * - Result aggregation
 *
 * ## Usage
 *
 * ```kotlin
 * // Create runner with plugin registry
 * val plugins = PluginRegistry()
 * plugins.registerByName("report:text")
 *
 * val runner = ScenarioRunner(specRegistry, configuration, plugins)
 *
 * // Run scenarios
 * val result = runner.run(scenarios)
 *
 * // Check results
 * if (result.status != ResultStatus.PASSED) {
 *     println("${result.failed} of ${result.scenarioResults.size} scenarios failed")
 * }
 * ```
 *
 * ## Plugin Lifecycle
 *
 * The runner invokes plugins in this order:
 * 1. `onTestExecutionStart()` - before first scenario
 * 2. For each scenario:
 *    - `onScenarioStart()`
 *    - For each step: `onStepStart()`, `onStepEnd()`
 *    - `onScenarioEnd()`
 * 3. `onTestExecutionEnd()` - after last scenario (reports generated here)
 *
 * ## Error Handling
 *
 * - Scenario execution errors are captured in individual results
 * - Plugin lifecycle errors are logged but don't fail the run
 *
 * @param specRegistry OpenAPI specification registry
 * @param configuration Test configuration (base URL, timeouts, etc.)
 * @param pluginRegistry Optional plugin registry for lifecycle hooks
 * @param fragmentRegistry Optional registry for reusable fragments
 */
class ScenarioRunner(
    private val specRegistry: SpecRegistry,
    private val configuration: Configuration,
    private val pluginRegistry: PluginRegistry? = null,
    private val fragmentRegistry: FragmentRegistry? = null,
) {
    private val executor by lazy {
        ScenarioExecutor(specRegistry, configuration, pluginRegistry, fragmentRegistry)
    }

    /**
     * Shared execution context for cross-scenario variable sharing.
     * Only used when [Configuration.shareVariablesAcrossScenarios] is true.
     */
    private var sharedContext: ExecutionContext? = null

    /**
     * Begin test execution lifecycle.
     *
     * Call this before executing any scenarios. This invokes `onTestExecutionStart()`
     * on all plugins.
     */
    fun beginExecution() {
        // Initialize shared context if cross-scenario variable sharing is enabled
        if (configuration.shareVariablesAcrossScenarios) {
            sharedContext = ExecutionContext()
        }
        try {
            pluginRegistry?.dispatchTestExecutionStart()
        } catch (e: Exception) {
            System.err.println("Warning: Plugin test execution start hook failed: ${e.message}")
        }
    }

    /**
     * End test execution lifecycle.
     *
     * Call this after all scenarios have completed. This invokes `onTestExecutionEnd()`
     * on all plugins, which triggers report generation for report plugins.
     */
    fun endExecution() {
        try {
            pluginRegistry?.dispatchTestExecutionEnd()
        } catch (e: Exception) {
            System.err.println("Warning: Plugin test execution end hook failed: ${e.message}")
        }
        // Clear shared context
        sharedContext = null
    }

    /**
     * Execute a single scenario and return its result.
     *
     * Note: This does NOT invoke lifecycle start/end hooks. Use [beginExecution]
     * before first scenario and [endExecution] after last scenario.
     *
     * When cross-scenario variable sharing is enabled via [Configuration.shareVariablesAcrossScenarios],
     * variables extracted in this scenario will be available to subsequent scenarios.
     *
     * @param scenario Scenario to execute
     * @return Execution result for the scenario
     */
    fun executeScenario(scenario: Scenario): ScenarioResult = executor.execute(scenario, sharedContext)

    /**
     * Run all provided scenarios and return aggregated results.
     *
     * This is a convenience method that handles the full lifecycle:
     * - Calls [beginExecution]
     * - Executes all scenarios
     * - Calls [endExecution]
     *
     * @param scenarios List of scenarios to execute
     * @return Aggregated run result with all scenario outcomes
     */
    fun run(scenarios: List<Scenario>): RunResult {
        val startTime = Instant.now()
        val results = mutableListOf<Pair<Scenario, ScenarioResult>>()

        beginExecution()

        // Execute all scenarios
        for (scenario in scenarios) {
            val result = executeScenario(scenario)
            results.add(scenario to result)
        }

        endExecution()

        return buildRunResult(startTime, results)
    }

    /**
     * Run scenarios with a callback for each completed scenario.
     *
     * This allows callers to receive results as scenarios complete, useful for
     * progress reporting or early termination.
     *
     * @param scenarios List of scenarios to execute
     * @param onScenarioComplete Callback invoked after each scenario completes
     * @return Aggregated run result with all scenario outcomes
     */
    fun run(
        scenarios: List<Scenario>,
        onScenarioComplete: (Scenario, ScenarioResult) -> Unit,
    ): RunResult {
        val startTime = Instant.now()
        val results = mutableListOf<Pair<Scenario, ScenarioResult>>()

        beginExecution()

        // Execute all scenarios with callback
        for (scenario in scenarios) {
            val result = executeScenario(scenario)
            results.add(scenario to result)
            onScenarioComplete(scenario, result)
        }

        endExecution()

        return buildRunResult(startTime, results)
    }

    /**
     * Run a single scenario with full lifecycle.
     *
     * Note: This still invokes the full lifecycle (test start/end), so it's
     * suitable for running a standalone scenario with report generation.
     * For batch execution, use [run] with a list of scenarios.
     *
     * @param scenario Scenario to execute
     * @return Run result with single scenario outcome
     */
    fun run(scenario: Scenario): RunResult = run(listOf(scenario))

    /**
     * Run scenarios with file-level parameter overrides.
     *
     * This method applies the provided parameters to the configuration
     * only for this run, without affecting the base configuration.
     *
     * Supported parameters:
     * - `baseUrl` - Override the base URL
     * - `timeout` - Request timeout in seconds
     * - `environment` - Environment name
     * - `strictSchemaValidation` - true/false
     * - `followRedirects` - true/false
     * - `logRequests` - true/false
     * - `logResponses` - true/false
     * - `shareVariablesAcrossScenarios` - true/false
     * - `header.<name>` - Add/override a default header
     *
     * @param scenarios List of scenarios to execute
     * @param parameters File-level configuration parameters
     * @return Aggregated run result with all scenario outcomes
     */
    fun runWithParameters(
        scenarios: List<Scenario>,
        parameters: Map<String, Any>,
    ): RunResult {
        if (parameters.isEmpty()) {
            return run(scenarios)
        }

        val startTime = Instant.now()
        val results = mutableListOf<Pair<Scenario, ScenarioResult>>()

        // Create a modified configuration with parameters applied
        val modifiedConfig = configuration.withParameters(parameters)
        val modifiedExecutor = ScenarioExecutor(specRegistry, modifiedConfig, pluginRegistry, fragmentRegistry)

        // Initialize shared context for modified config if needed
        val localSharedContext =
            if (modifiedConfig.shareVariablesAcrossScenarios) {
                ExecutionContext()
            } else {
                null
            }

        try {
            pluginRegistry?.dispatchTestExecutionStart()
        } catch (e: Exception) {
            System.err.println("Warning: Plugin test execution start hook failed: ${e.message}")
        }

        // Execute all scenarios with modified executor
        for (scenario in scenarios) {
            val result = modifiedExecutor.execute(scenario, localSharedContext)
            results.add(scenario to result)
        }

        try {
            pluginRegistry?.dispatchTestExecutionEnd()
        } catch (e: Exception) {
            System.err.println("Warning: Plugin test execution end hook failed: ${e.message}")
        }

        return buildRunResult(startTime, results)
    }

    private fun buildRunResult(
        startTime: Instant,
        results: List<Pair<Scenario, ScenarioResult>>,
    ): RunResult {
        val duration = Duration.between(startTime, Instant.now())

        val passed = results.count { it.second.status == ResultStatus.PASSED }
        val failed = results.count { it.second.status == ResultStatus.FAILED }
        val skipped = results.count { it.second.status == ResultStatus.SKIPPED }
        val errors = results.count { it.second.status == ResultStatus.ERROR }

        val overallStatus =
            when {
                results.isEmpty() -> ResultStatus.PASSED // No scenarios to run = success
                errors > 0 -> ResultStatus.ERROR
                failed > 0 -> ResultStatus.FAILED
                skipped == results.size -> ResultStatus.SKIPPED
                else -> ResultStatus.PASSED
            }

        return RunResult(
            status = overallStatus,
            duration = duration,
            scenarioResults = results,
            passed = passed,
            failed = failed,
            skipped = skipped,
            errors = errors,
        )
    }
}
