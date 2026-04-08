package io.github.ktakashi.lemoncheck.junit.engine

import io.github.ktakashi.lemoncheck.junit.discovery.ScenarioDiscovery
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for LemonCheckTestEngine.
 */
class LemonCheckTestEngineTest {
    @Test
    fun `engine has correct ID`() {
        val engine = LemonCheckTestEngine()
        assertEquals("lemoncheck", engine.id)
    }

    @Test
    fun `scenario discovery finds scenario files`() {
        val classLoader = Thread.currentThread().contextClassLoader
        val scenarios =
            ScenarioDiscovery.discoverScenarios(
                classLoader,
                arrayOf("scenarios/*.scenario"),
            )

        assertTrue(scenarios.isNotEmpty(), "Should discover at least one scenario file")
        assertTrue(
            scenarios.any { it.name == "simple.scenario" },
            "Should find simple.scenario",
        )
    }
}
