package org.berrycrush.plugin

import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.executor.BerryCrushConfigurationProvider
import org.berrycrush.executor.BerryCrushScenarioExecutor
import org.berrycrush.model.Scenario
import org.berrycrush.openapi.SpecRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Integration tests for plugin lifecycle events.
 */
class PluginLifecycleTest {
    private lateinit var registry: PluginRegistry
    private lateinit var testPlugin: RecordingPlugin

    @BeforeEach
    fun setup() {
        registry = PluginRegistry()
        testPlugin = RecordingPlugin()
        registry.register(testPlugin)
    }

    @Test
    fun `plugin receives lifecycle events in correct order`() {
        // Given a scenario with no steps (simple case)
        val scenario = Scenario(name = "Test Scenario")

        // When executing the scenario with plugins
        val specRegistry = SpecRegistry()
        val config = BerryCrushConfigurationProvider.from(BerryCrushConfiguration())
        val executor = BerryCrushScenarioExecutor(specRegistry, config, registry)

        executor.execute(scenario)

        // Then plugin should have received events in order
        assertEquals(
            listOf("onScenarioStart", "onScenarioEnd"),
            testPlugin.events,
            "Plugin should receive scenario start before scenario end",
        )
    }

    @Test
    fun `plugin receives scenario context with correct name`() {
        val scenario = Scenario(name = "My Test Scenario", tags = setOf("api", "integration"))

        val specRegistry = SpecRegistry()
        val config = BerryCrushConfigurationProvider.from(BerryCrushConfiguration())
        val executor = BerryCrushScenarioExecutor(specRegistry, config, registry)

        executor.execute(scenario)

        // Verify scenario context was passed correctly
        assertEquals("My Test Scenario", testPlugin.lastScenarioName)
        assertEquals(true, testPlugin.lastScenarioTags?.containsAll(setOf("api", "integration")))
    }

    /**
     * Test plugin that records all lifecycle events for verification.
     */
    class RecordingPlugin : BerryCrushPlugin {
        val events = mutableListOf<String>()
        var lastScenarioName: String? = null
        var lastScenarioTags: Set<String>? = null

        override fun onScenarioStart(context: ScenarioContext) {
            events.add("onScenarioStart")
            lastScenarioName = context.scenarioName
            lastScenarioTags = context.tags
        }

        override fun onScenarioEnd(
            context: ScenarioContext,
            result: ScenarioResult,
        ) {
            events.add("onScenarioEnd")
        }

        override fun onStepStart(context: StepContext) {
            events.add("onStepStart")
        }

        override fun onStepEnd(
            context: StepContext,
            result: StepResult,
        ) {
            events.add("onStepEnd")
        }
    }
}
