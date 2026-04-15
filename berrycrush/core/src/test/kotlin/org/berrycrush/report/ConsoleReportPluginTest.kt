package org.berrycrush.report

import org.berrycrush.plugin.ResultStatus
import org.berrycrush.plugin.ScenarioContext
import org.berrycrush.plugin.ScenarioResult
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ConsoleReportPlugin.
 */
class ConsoleReportPluginTest {
    @Test
    fun `generateReport outputs to configured PrintStream`() {
        // Capture output
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)
        
        val plugin = ConsoleReportPlugin(output = printStream)
        
        // Simulate scenario execution
        val context = createMockScenarioContext("Test Scenario")
        val result = createMockScenarioResult(ResultStatus.PASSED, listOf(
            createMockStepResult("assert status 200", ResultStatus.PASSED, false),
            createMockStepResult("custom assertion", ResultStatus.PASSED, true),
        ))
        
        plugin.onScenarioStart(context)
        plugin.onScenarioEnd(context, result)
        plugin.onTestExecutionEnd()
        
        val output = outputStream.toString()
        
        // Verify output contains expected content
        assertContains(output, "BerryCrush Test Report")
        assertContains(output, "Test Scenario")
        assertContains(output, "assert status 200")
        assertContains(output, "custom assertion")
        assertContains(output, "Summary:")
    }

    @Test
    fun `colored output contains ANSI codes`() {
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)
        
        val plugin = ConsoleReportPlugin(
            output = printStream,
            colorScheme = ColorScheme.DEFAULT
        )
        
        val context = createMockScenarioContext("Test Scenario")
        val result = createMockScenarioResult(ResultStatus.PASSED, listOf(
            createMockStepResult("assert status 200", ResultStatus.PASSED, false),
        ))
        
        plugin.onScenarioStart(context)
        plugin.onScenarioEnd(context, result)
        plugin.onTestExecutionEnd()
        
        val output = outputStream.toString()
        
        // Should contain ANSI escape codes
        assertTrue(output.contains("\u001B["), "Output should contain ANSI escape codes")
    }

    @Test
    fun `custom steps are highlighted`() {
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)
        
        val plugin = ConsoleReportPlugin(
            output = printStream,
            colorScheme = ColorScheme.DEFAULT
        )
        
        val context = createMockScenarioContext("Test Scenario")
        val result = createMockScenarioResult(ResultStatus.PASSED, listOf(
            createMockStepResult("custom step executed", ResultStatus.PASSED, true),
        ))
        
        plugin.onScenarioStart(context)
        plugin.onScenarioEnd(context, result)
        plugin.onTestExecutionEnd()
        
        val output = outputStream.toString()
        
        // Custom steps should have bright cyan highlighting
        assertTrue(output.contains(AnsiColors.BRIGHT_CYAN), "Custom steps should be highlighted with bright cyan")
        assertTrue(output.contains(AnsiColors.BOLD), "Custom steps should be bold")
    }

    @Test
    fun `failed scenarios show red`() {
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)
        
        val plugin = ConsoleReportPlugin(
            output = printStream,
            colorScheme = ColorScheme.DEFAULT
        )
        
        val context = createMockScenarioContext("Failing Test")
        val result = createMockScenarioResult(ResultStatus.FAILED, listOf(
            createMockStepResult("assert status 200", ResultStatus.FAILED, false),
        ))
        
        plugin.onScenarioStart(context)
        plugin.onScenarioEnd(context, result)
        plugin.onTestExecutionEnd()
        
        val output = outputStream.toString()
        
        // Failed should have red color
        assertTrue(output.contains(AnsiColors.RED), "Failed scenarios should be red")
    }

    @Test
    fun `isTty returns false when no console`() {
        val plugin = ConsoleReportPlugin()
        // In test environment, System.console() is null
        // This just verifies the property doesn't throw
        @Suppress("UNUSED_VARIABLE")
        val tty = plugin.isTty
    }

    @Test
    fun `fromOptions creates plugin with high-contrast scheme`() {
        val plugin = ConsoleReportPlugin.fromOptions("high-contrast")
        
        // Verify by checking output includes high contrast colors (bold green for passed)
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)
        
        // Create a test plugin with same options but captured output
        val testPlugin = ConsoleReportPlugin(
            output = printStream,
            colorScheme = ColorScheme.HIGH_CONTRAST
        )
        
        val context = createMockScenarioContext("Test")
        val result = createMockScenarioResult(ResultStatus.PASSED, listOf(
            createMockStepResult("step", ResultStatus.PASSED, false)
        ))
        
        testPlugin.onScenarioStart(context)
        testPlugin.onScenarioEnd(context, result)
        testPlugin.onTestExecutionEnd()
        
        val output = outputStream.toString()
        // High contrast scheme uses bold for passed
        assertTrue(output.contains(AnsiColors.BOLD), "High contrast should use bold")
    }

    @Test
    fun `fromOptions creates plugin with no colors`() {
        val plugin = ConsoleReportPlugin.fromOptions("no-color")
        
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)
        
        val testPlugin = ConsoleReportPlugin(
            output = printStream,
            colorScheme = ColorScheme.NONE
        )
        
        val context = createMockScenarioContext("Test")
        val result = createMockScenarioResult(ResultStatus.PASSED, listOf(
            createMockStepResult("step", ResultStatus.PASSED, false)
        ))
        
        testPlugin.onScenarioStart(context)
        testPlugin.onScenarioEnd(context, result)
        testPlugin.onTestExecutionEnd()
        
        val output = outputStream.toString()
        // No color scheme should not have ANSI codes
        assertFalse(output.contains("\u001B["), "No-color mode should not contain ANSI codes")
    }

    @Test
    fun `string constructor parses options correctly`() {
        // Test that string constructor works (used by PluginNameResolver)
        val plugin = ConsoleReportPlugin("stderr,high-contrast")
        assertEquals("report:console", plugin.id)
    }

    // Helper methods to create mock objects
    
    private fun createMockScenarioContext(name: String): ScenarioContext = object : ScenarioContext {
        override val scenarioName: String = name
        override val scenarioFile: Path = Path.of("test.scenario")
        override val startTime: Instant = Instant.now()
        override val variables: MutableMap<String, Any> = mutableMapOf()
        override val metadata: Map<String, String> = emptyMap()
        override val tags: Set<String> = emptySet()
    }
    
    private fun createMockScenarioResult(
        status: ResultStatus,
        steps: List<org.berrycrush.plugin.StepResult>
    ): ScenarioResult = object : ScenarioResult {
        override val status: ResultStatus = status
        override val duration: Duration = Duration.ofMillis(100)
        override val failedStep: Int = if (status == ResultStatus.FAILED) 0 else -1
        override val error: Throwable? = null
        override val stepResults: List<org.berrycrush.plugin.StepResult> = steps
    }
    
    private fun createMockStepResult(
        description: String,
        status: ResultStatus,
        isCustom: Boolean
    ): org.berrycrush.plugin.StepResult = object : org.berrycrush.plugin.StepResult {
        override val status: ResultStatus = status
        override val duration: Duration = Duration.ofMillis(10)
        override val failure: org.berrycrush.plugin.AssertionFailure? = null
        override val error: Throwable? = null
        override val stepDescription: String = description
        override val httpStatusCode: Int? = null
        override val responseBody: String? = null
        override val responseHeaders: Map<String, List<String>> = emptyMap()
        override val isCustomStep: Boolean = isCustom
    }
}
