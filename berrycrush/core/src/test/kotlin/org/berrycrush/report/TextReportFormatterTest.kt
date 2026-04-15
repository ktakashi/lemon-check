package org.berrycrush.report

import org.berrycrush.plugin.ResultStatus
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for TextReportFormatter.
 */
class TextReportFormatterTest {
    private val timestamp = Instant.parse("2024-01-15T10:30:00Z")

    private fun createReport(
        scenarios: List<ScenarioReportEntry> = emptyList(),
    ): TestReport {
        val passed = scenarios.count { it.status == ResultStatus.PASSED }
        val failed = scenarios.count { it.status == ResultStatus.FAILED }
        val skipped = scenarios.count { it.status == ResultStatus.SKIPPED }
        val errors = scenarios.count { it.status == ResultStatus.ERROR }
        return TestReport(
            timestamp = timestamp,
            duration = Duration.ofMillis(1234),
            summary = TestSummary(
                total = scenarios.size,
                passed = passed,
                failed = failed,
                skipped = skipped,
                errors = errors,
            ),
            scenarios = scenarios,
        )
    }

    private fun createScenario(
        name: String = "Test Scenario",
        status: ResultStatus = ResultStatus.PASSED,
        steps: List<StepReportEntry> = emptyList(),
    ): ScenarioReportEntry =
        ScenarioReportEntry(
            name = name,
            status = status,
            duration = Duration.ofMillis(100),
            steps = steps,
        )

    private fun createStep(
        description: String = "test step",
        status: ResultStatus = ResultStatus.PASSED,
        isCustomStep: Boolean = false,
    ): StepReportEntry =
        StepReportEntry(
            description = description,
            status = status,
            duration = Duration.ofMillis(10),
            isCustomStep = isCustomStep,
        )

    @Test
    fun `plain formatter produces output without ANSI codes`() {
        val formatter = TextReportFormatter.plain()
        val report = createReport(
            listOf(createScenario(steps = listOf(createStep())))
        )

        val output = formatter.formatReport(report)

        assertFalse(output.contains("\u001B["), "Output should not contain ANSI escape codes")
    }

    @Test
    fun `colored formatter produces output with ANSI codes`() {
        val formatter = TextReportFormatter.colored()
        val report = createReport(
            listOf(createScenario(steps = listOf(createStep())))
        )

        val output = formatter.formatReport(report)

        assertTrue(output.contains("\u001B["), "Output should contain ANSI escape codes")
    }

    @Test
    fun `report includes header with separator`() {
        val formatter = TextReportFormatter.plain()
        val report = createReport()

        val output = formatter.formatReport(report)

        assertContains(output, "═══════")
        assertContains(output, "BerryCrush Test Report")
    }

    @Test
    fun `report includes execution date`() {
        val formatter = TextReportFormatter.plain()
        val report = createReport()

        val output = formatter.formatReport(report)

        assertContains(output, "Execution Date: 2024-01-15T10:30:00Z")
    }

    @Test
    fun `report includes duration`() {
        val formatter = TextReportFormatter.plain()
        val report = createReport()

        val output = formatter.formatReport(report)

        // Check for duration format (locale-independent check)
        assertContains(output, "Duration:")
        assertTrue(output.contains(Regex("""Duration: \d+[.,]\d+s""")), "Duration should be formatted as x.xxxs")
    }

    @Test
    fun `report includes scenario with status icon`() {
        val formatter = TextReportFormatter.plain()
        val report = createReport(
            listOf(createScenario(name = "Login Test", status = ResultStatus.PASSED))
        )

        val output = formatter.formatReport(report)

        assertContains(output, "scenario: Login Test ✓")
    }

    @Test
    fun `failed scenario shows X icon`() {
        val formatter = TextReportFormatter.plain()
        val report = createReport(
            listOf(createScenario(name = "Failing Test", status = ResultStatus.FAILED))
        )

        val output = formatter.formatReport(report)

        assertContains(output, "scenario: Failing Test ✗")
    }

    @Test
    fun `skipped scenario shows circle icon`() {
        val formatter = TextReportFormatter.plain()
        val report = createReport(
            listOf(createScenario(name = "Skipped Test", status = ResultStatus.SKIPPED))
        )

        val output = formatter.formatReport(report)

        assertContains(output, "scenario: Skipped Test ○")
    }

    @Test
    fun `report includes summary statistics`() {
        val formatter = TextReportFormatter.plain()
        val report = createReport(
            listOf(
                createScenario(status = ResultStatus.PASSED),
                createScenario(status = ResultStatus.FAILED),
            )
        )

        val output = formatter.formatReport(report)

        assertContains(output, "Summary: 1/2 scenarios passed")
        assertContains(output, "1 passed")
        assertContains(output, "1 failed")
    }

    @Test
    fun `step with dotted leaders shows pass status`() {
        val formatter = TextReportFormatter.plain()
        val report = createReport(
            listOf(createScenario(steps = listOf(createStep(description = "assert status 200"))))
        )

        val output = formatter.formatReport(report)

        assertContains(output, "assert status 200")
        assertContains(output, "..")
        assertContains(output, "pass")
    }

    @Test
    fun `custom step is highlighted in colored output`() {
        val formatter = TextReportFormatter.colored()
        val report = createReport(
            listOf(createScenario(steps = listOf(
                createStep(description = "custom assertion", isCustomStep = true)
            )))
        )

        val output = formatter.formatReport(report)

        // Custom steps should have BOLD and BRIGHT_CYAN
        assertContains(output, AnsiColors.BOLD)
        assertContains(output, AnsiColors.BRIGHT_CYAN)
        assertContains(output, "custom assertion")
    }

    @Test
    fun `plain formatter does not highlight custom steps`() {
        val formatter = TextReportFormatter.plain()
        val report = createReport(
            listOf(createScenario(steps = listOf(
                createStep(description = "custom assertion", isCustomStep = true)
            )))
        )

        val output = formatter.formatReport(report)

        assertFalse(output.contains("\u001B["), "Plain output should not contain ANSI codes")
        assertContains(output, "custom assertion")
    }

    @Test
    fun `failed scenarios are listed at the end`() {
        val formatter = TextReportFormatter.plain()
        val report = createReport(
            listOf(
                createScenario(name = "Passing Test", status = ResultStatus.PASSED),
                createScenario(name = "Failing Test", status = ResultStatus.FAILED),
            )
        )

        val output = formatter.formatReport(report)

        assertContains(output, "Failed Scenarios:")
        assertContains(output, "- Failing Test")
    }

    @Test
    fun `colored formatter with custom scheme uses custom colors`() {
        val customScheme = ColorScheme(
            passed = AnsiColors.BRIGHT_GREEN,
        )
        val formatter = TextReportFormatter.colored(customScheme)
        val report = createReport(
            listOf(createScenario(steps = listOf(createStep())))
        )

        val output = formatter.formatReport(report)

        assertContains(output, AnsiColors.BRIGHT_GREEN)
    }
}
