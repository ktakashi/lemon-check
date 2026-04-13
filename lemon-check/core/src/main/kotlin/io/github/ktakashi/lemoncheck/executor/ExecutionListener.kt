package io.github.ktakashi.lemoncheck.executor

import io.github.ktakashi.lemoncheck.autotest.AutoTestCase
import io.github.ktakashi.lemoncheck.model.AutoTestResult
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.ScenarioResult
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepResult

/**
 * Listener for test execution events.
 *
 * This interface allows test frameworks (like JUnit) to receive real-time notifications
 * during test execution. It provides callbacks for:
 * - Scenario lifecycle (start/complete)
 * - Step lifecycle (start/complete)
 * - Auto-test lifecycle (start/complete)
 *
 * ## Framework Integration
 *
 * Test frameworks implement this interface to bridge LemonCheck execution events
 * to framework-specific reporting. For example, the JUnit adapter uses this to fire
 * `executionStarted` and `executionFinished` events on JUnit's `EngineExecutionListener`.
 *
 * ## Event Order
 *
 * Events are fired in this order for a scenario with auto-tests:
 * 1. `onScenarioStarting(scenario)` - before scenario execution begins
 * 2. For each step:
 *    - `onStepStarting(step)` - before step execution
 *    - For auto-test steps, per test:
 *      - `onAutoTestStarting(testCase)` - before auto-test
 *      - `onAutoTestCompleted(testCase, result)` - after auto-test
 *    - `onStepCompleted(step, result)` - after step execution
 * 3. `onScenarioCompleted(scenario, result)` - after scenario execution
 *
 * ## Usage
 *
 * ```kotlin
 * val listener = object : ExecutionListener {
 *     override fun onScenarioStarting(scenario: Scenario) {
 *         println("Starting: ${scenario.name}")
 *     }
 *     override fun onScenarioCompleted(scenario: Scenario, result: ScenarioResult) {
 *         println("Completed: ${scenario.name}, status=${result.status}")
 *     }
 *     // ... other callbacks
 * }
 *
 * scenarioExecutor.execute(scenario, executionListener = listener)
 * ```
 *
 * @see ScenarioExecutor
 */
interface ExecutionListener {
    /**
     * Called just before a scenario starts execution.
     *
     * @param scenario The scenario about to be executed
     */
    fun onScenarioStarting(scenario: Scenario) {
        // do nothing by default
    }

    /**
     * Called immediately after a scenario finishes execution.
     *
     * @param scenario The scenario that just finished
     * @param result The result of the scenario execution
     */
    fun onScenarioCompleted(
        scenario: Scenario,
        result: ScenarioResult,
    ) {
        // do nothing by default
    }

    /**
     * Called just before a step starts execution.
     *
     * @param step The step about to be executed
     */
    fun onStepStarting(step: Step) {
        // do nothing by default
    }

    /**
     * Called immediately after a step finishes execution.
     *
     * @param step The step that just finished
     * @param result The result of the step execution
     */
    fun onStepCompleted(
        step: Step,
        result: StepResult,
    ) {
        // do nothing by default
    }

    /**
     * Called just before an auto-test case starts execution.
     *
     * @param testCase The test case about to be executed
     */
    fun onAutoTestStarting(testCase: AutoTestCase) {
        // do nothing by default
    }

    /**
     * Called immediately after an auto-test case finishes execution.
     *
     * @param testCase The test case that just finished
     * @param result The result of the test execution
     */
    fun onAutoTestCompleted(
        testCase: AutoTestCase,
        result: AutoTestResult,
    ) {
        // do nothing by default
    }

    companion object {
        /**
         * A no-op listener that does nothing.
         * Use this when execution events are not needed.
         */
        val NOOP: ExecutionListener =
            object : ExecutionListener {}
    }
}
