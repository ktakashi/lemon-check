package io.github.ktakashi.lemoncheck.plugin

import io.github.ktakashi.lemoncheck.exception.ConfigurationException
import io.github.ktakashi.lemoncheck.report.JsonReportPlugin
import io.github.ktakashi.lemoncheck.report.JunitReportPlugin
import io.github.ktakashi.lemoncheck.report.TextReportPlugin
import io.github.ktakashi.lemoncheck.report.XmlReportPlugin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [PluginNameResolver].
 *
 * These tests verify ServiceLoader-based plugin discovery and resolution.
 * The built-in report plugins are registered via META-INF/services.
 */
class PluginNameResolverTest {
    @Test
    fun `resolves text report plugin by id`() {
        val plugin = PluginNameResolver.resolve("report:text")

        assertIs<TextReportPlugin>(plugin)
        assertEquals("report:text", plugin.id)
    }

    @Test
    fun `resolves text report plugin with custom path`() {
        val plugin = PluginNameResolver.resolve("report:text:custom/output.txt")

        assertIs<TextReportPlugin>(plugin)
        assertEquals(Path.of("custom/output.txt"), plugin.outputPath)
    }

    @Test
    fun `resolves json report plugin by id`() {
        val plugin = PluginNameResolver.resolve("report:json")

        assertIs<JsonReportPlugin>(plugin)
        assertEquals("report:json", plugin.id)
    }

    @Test
    fun `resolves json report plugin with custom path`() {
        val plugin = PluginNameResolver.resolve("report:json:build/reports/custom.json")

        assertIs<JsonReportPlugin>(plugin)
        assertEquals(Path.of("build/reports/custom.json"), plugin.outputPath)
    }

    @Test
    fun `resolves xml report plugin by id`() {
        val plugin = PluginNameResolver.resolve("report:xml")

        assertIs<XmlReportPlugin>(plugin)
        assertEquals("report:xml", plugin.id)
    }

    @Test
    fun `resolves junit report plugin by id`() {
        val plugin = PluginNameResolver.resolve("report:junit")

        assertIs<JunitReportPlugin>(plugin)
        assertEquals("report:junit", plugin.id)
    }

    @Test
    fun `resolves plugin by name`() {
        val plugin = PluginNameResolver.resolve("Text Report Plugin")

        assertIs<TextReportPlugin>(plugin)
    }

    @Test
    fun `throws exception for unknown plugin`() {
        val exception =
            assertThrows<ConfigurationException> {
                PluginNameResolver.resolve("unknown:plugin")
            }

        assertTrue(exception.message!!.contains("Unknown plugin"))
        assertTrue(exception.message!!.contains("unknown:plugin"))
    }

    @Test
    fun `throws exception for unknown plugin id`() {
        val exception =
            assertThrows<ConfigurationException> {
                PluginNameResolver.resolve("nonexistent")
            }

        assertTrue(exception.message!!.contains("Unknown plugin"))
        assertTrue(exception.message!!.contains("nonexistent"))
    }

    @Test
    fun `error message includes available plugin ids`() {
        val exception =
            assertThrows<ConfigurationException> {
                PluginNameResolver.resolve("nonexistent")
            }

        // Should list available plugin IDs
        assertTrue(exception.message!!.contains("report:text"))
        assertTrue(exception.message!!.contains("report:json"))
    }

    @Test
    fun `creates fresh instance for each resolution`() {
        val plugin1 = PluginNameResolver.resolve("report:text")
        val plugin2 = PluginNameResolver.resolve("report:text")

        // Should be different instances
        assertTrue(plugin1 !== plugin2)
    }
}
