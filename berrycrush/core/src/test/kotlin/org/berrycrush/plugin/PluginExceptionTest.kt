package org.berrycrush.plugin

import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.executor.BerryCrushConfigurationProvider
import org.berrycrush.executor.BerryCrushScenarioExecutor
import org.berrycrush.model.Scenario
import org.berrycrush.openapi.SpecRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * Tests for plugin exception handling (fail-fast behavior).
 */
class PluginExceptionTest {
    private lateinit var registry: PluginRegistry

    @BeforeEach
    fun setup() {
        registry = PluginRegistry()
    }

    @Test
    fun `exception in onScenarioStart propagates immediately`() {
        // Given a plugin that throws on scenario start
        val errorPlugin =
            object : BerryCrushPlugin {
                override val name: String = "Error Plugin"
                override val id: String = "error-plugin"

                @Suppress("TooGenericExceptionThrown")
                override fun onScenarioStart(context: ScenarioContext): Unit = throw RuntimeException("Plugin initialization failed")
            }
        registry.register(errorPlugin)

        // When executing a scenario
        val scenario = Scenario(name = "Test")
        val executor = BerryCrushScenarioExecutor(SpecRegistry(), BerryCrushConfigurationProvider.from(BerryCrushConfiguration()), registry)

        // Then exception should propagate
        val exception =
            assertThrows<RuntimeException> {
                executor.execute(scenario)
            }
        assertEquals("Plugin initialization failed", exception.message)
    }

    @Test
    fun `exception in onScenarioEnd propagates immediately`() {
        val errorPlugin =
            object : BerryCrushPlugin {
                override val name: String = "Error Plugin"
                override val id: String = "error-plugin"

                @Suppress("TooGenericExceptionThrown")
                override fun onScenarioEnd(
                    context: ScenarioContext,
                    result: ScenarioResult,
                ): Unit = throw RuntimeException("Plugin cleanup failed")
            }
        registry.register(errorPlugin)

        val scenario = Scenario(name = "Test")
        val executor = BerryCrushScenarioExecutor(SpecRegistry(), BerryCrushConfigurationProvider.from(BerryCrushConfiguration()), registry)

        val exception =
            assertThrows<RuntimeException> {
                executor.execute(scenario)
            }
        assertEquals("Plugin cleanup failed", exception.message)
    }

    @Test
    fun `duplicate plugin registration is rejected`() {
        val plugin1 =
            object : BerryCrushPlugin {
                override val id: String = "duplicate-id"
                override val name: String = "Plugin 1"
            }
        val plugin2 =
            object : BerryCrushPlugin {
                override val id: String = "duplicate-id"
                override val name: String = "Plugin 2"
            }

        registry.register(plugin1)

        val exception =
            assertThrows<IllegalArgumentException> {
                registry.register(plugin2)
            }
        assertEquals(true, exception.message?.contains("duplicate-id"))
    }

    @Test
    fun `clear removes all plugins`() {
        registry.register(
            object : BerryCrushPlugin {
                override val id: String = "plugin-1"
            },
        )
        registry.register(
            object : BerryCrushPlugin {
                override val id: String = "plugin-2"
            },
        )

        assertEquals(2, registry.getPlugins().size)

        registry.clear()

        assertEquals(0, registry.getPlugins().size)
    }
}
