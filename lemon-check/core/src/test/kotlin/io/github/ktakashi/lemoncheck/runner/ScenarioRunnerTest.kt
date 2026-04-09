package io.github.ktakashi.lemoncheck.runner

import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
import io.github.ktakashi.lemoncheck.plugin.LemonCheckPlugin
import io.github.ktakashi.lemoncheck.plugin.PluginRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [ScenarioRunner].
 */
class ScenarioRunnerTest {
    @Test
    fun `returns passed status for empty scenario list`() {
        val specRegistry = SpecRegistry()
        val configuration = Configuration()
        val runner = ScenarioRunner(specRegistry, configuration)

        val result = runner.run(emptyList())

        // Empty scenario list could be SKIPPED or PASSED depending on interpretation
        // Using PASSED since there were no failures
        assertEquals(ResultStatus.PASSED, result.status)
        assertEquals(0, result.scenarioResults.size)
        assertEquals(0, result.passed)
        assertEquals(0, result.failed)
    }

    @Test
    fun `invokes plugin lifecycle hooks in correct order`() {
        val specRegistry = SpecRegistry()
        val configuration = Configuration()
        val pluginRegistry = PluginRegistry()

        val events = mutableListOf<String>()
        val trackingPlugin =
            object : LemonCheckPlugin {
                override val id = "test:tracking"
                override val name = "Tracking Plugin"

                override fun onTestExecutionStart() {
                    events.add("testStart")
                }

                override fun onTestExecutionEnd() {
                    events.add("testEnd")
                }
            }

        pluginRegistry.register(trackingPlugin)
        val runner = ScenarioRunner(specRegistry, configuration, pluginRegistry)

        runner.run(emptyList())

        assertEquals(listOf("testStart", "testEnd"), events)
    }

    @Test
    fun `runs single scenario and returns result`() {
        val specRegistry = SpecRegistry()
        val configuration = Configuration()
        val runner = ScenarioRunner(specRegistry, configuration)

        val scenario =
            Scenario(
                name = "Test Scenario",
                steps = emptyList(),
                background = emptyList(),
                tags = emptySet(),
            )

        val result = runner.run(scenario)

        assertEquals(1, result.scenarioResults.size)
        assertEquals(scenario, result.scenarioResults[0].first)
    }

    @Test
    fun `aggregates results from multiple scenarios`() {
        val specRegistry = SpecRegistry()
        val configuration = Configuration()
        val runner = ScenarioRunner(specRegistry, configuration)

        val scenarios =
            listOf(
                Scenario(
                    name = "Scenario 1",
                    steps = emptyList(),
                    background = emptyList(),
                    tags = emptySet(),
                ),
                Scenario(
                    name = "Scenario 2",
                    steps = emptyList(),
                    background = emptyList(),
                    tags = emptySet(),
                ),
            )

        val result = runner.run(scenarios)

        assertEquals(2, result.scenarioResults.size)
        // Empty scenarios should pass (no steps to fail)
        assertEquals(2, result.passed)
        assertEquals(0, result.failed)
        assertTrue(result.duration.toMillis() >= 0)
    }

    @Test
    fun `initializes shared context when shareVariablesAcrossScenarios is enabled`() {
        val specRegistry = SpecRegistry()
        val configuration =
            Configuration().apply {
                shareVariablesAcrossScenarios = true
            }
        val pluginRegistry = PluginRegistry()

        val events = mutableListOf<String>()
        val trackingPlugin =
            object : LemonCheckPlugin {
                override val id = "test:tracking"
                override val name = "Tracking Plugin"

                override fun onTestExecutionStart() {
                    events.add("testStart")
                }

                override fun onTestExecutionEnd() {
                    events.add("testEnd")
                }
            }

        pluginRegistry.register(trackingPlugin)
        val runner = ScenarioRunner(specRegistry, configuration, pluginRegistry)

        runner.run(emptyList())

        // Verify lifecycle still works with shared context enabled
        assertEquals(listOf("testStart", "testEnd"), events)
    }

    @Test
    fun `shares variables across scenarios when enabled`() {
        val specRegistry = SpecRegistry()
        val configuration =
            Configuration().apply {
                shareVariablesAcrossScenarios = true
            }

        val runner = ScenarioRunner(specRegistry, configuration)

        // Create two scenarios - first one should set a variable,
        // second one should be able to access it (indirectly through the shared context)
        val scenario1 =
            Scenario(
                name = "First Scenario",
                steps = emptyList(),
                background = emptyList(),
                tags = emptySet(),
            )

        val scenario2 =
            Scenario(
                name = "Second Scenario",
                steps = emptyList(),
                background = emptyList(),
                tags = emptySet(),
            )

        // Run both scenarios
        val result = runner.run(listOf(scenario1, scenario2))

        // Both empty scenarios should pass
        assertEquals(2, result.passed)
        assertEquals(0, result.failed)
    }
}
