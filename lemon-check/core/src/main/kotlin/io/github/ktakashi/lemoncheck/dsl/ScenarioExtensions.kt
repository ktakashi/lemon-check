package io.github.ktakashi.lemoncheck.dsl

import io.github.ktakashi.lemoncheck.executor.ScenarioExecutor
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.ScenarioResult
import io.github.ktakashi.lemoncheck.report.ConsoleReporter
import io.github.ktakashi.lemoncheck.report.TestReporter

/**
 * Extension functions for executing scenarios.
 *
 * Execute this scenario within the given suite context.
 *
 * @param suite The LemonCheck suite containing spec and configuration
 * @param reporter Optional test reporter for output
 * @return The scenario execution result
 */
fun Scenario.run(
    suite: LemonCheckSuite,
    reporter: TestReporter = ConsoleReporter(),
): ScenarioResult {
    val executor = ScenarioExecutor(suite.specRegistry, suite.configuration)

    reporter.onScenarioStart(this.name)
    val result = executor.execute(this)

    result.stepResults.forEach { stepResult ->
        reporter.onStepComplete(stepResult)
    }

    reporter.onScenarioComplete(result)
    return result
}

/**
 * Execute all scenarios in this suite.
 *
 * @param reporter Optional test reporter for output
 * @return List of all scenario execution results
 */
fun LemonCheckSuite.runAll(reporter: TestReporter = ConsoleReporter()): List<ScenarioResult> {
    val executor = ScenarioExecutor(specRegistry, configuration)
    val results = mutableListOf<ScenarioResult>()

    for (scenario in scenarios) {
        reporter.onScenarioStart(scenario.name)
        val result = executor.execute(scenario)

        result.stepResults.forEach { stepResult ->
            reporter.onStepComplete(stepResult)
        }

        reporter.onScenarioComplete(result)
        results.add(result)
    }

    reporter.onSuiteComplete(results)
    return results
}

/**
 * Execute scenarios matching a filter.
 *
 * @param filter Predicate to select which scenarios to run
 * @param reporter Optional test reporter for output
 * @return List of execution results for matching scenarios
 */
fun LemonCheckSuite.runFiltered(
    filter: (Scenario) -> Boolean,
    reporter: TestReporter = ConsoleReporter(),
): List<ScenarioResult> {
    val executor = ScenarioExecutor(specRegistry, configuration)
    val results = mutableListOf<ScenarioResult>()

    for (scenario in scenarios.filter(filter)) {
        reporter.onScenarioStart(scenario.name)
        val result = executor.execute(scenario)

        result.stepResults.forEach { stepResult ->
            reporter.onStepComplete(stepResult)
        }

        reporter.onScenarioComplete(result)
        results.add(result)
    }

    reporter.onSuiteComplete(results)
    return results
}

/**
 * Execute scenarios with specific tags.
 *
 * @param tags Tags to match (scenario must have at least one)
 * @param reporter Optional test reporter for output
 * @return List of execution results for matching scenarios
 */
fun LemonCheckSuite.runByTags(
    vararg tags: String,
    reporter: TestReporter = ConsoleReporter(),
): List<ScenarioResult> {
    val tagSet = tags.toSet()
    return runFiltered({ scenario ->
        scenario.tags.any { it in tagSet }
    }, reporter)
}
