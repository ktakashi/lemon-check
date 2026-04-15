package org.berrycrush.report

import org.berrycrush.plugin.ResultStatus
import java.nio.file.Path
import java.time.format.DateTimeFormatter

/**
 * Report plugin that generates human-readable text output in scenario format.
 *
 * The text report includes:
 * - Summary header with execution date, duration, and totals
 * - Per-scenario details styled like scenario files
 * - Step-by-step results with status indicators and dotted leaders
 * - Failure details with expected/actual values
 *
 * Example output:
 * ```
 * scenario: Get pet by ID
 *   when I request a specific pet
 *     call ^getPetById .................. 200 OK
 *   then I get the pet details
 *     assert status 200 ................. pass
 *     assert $.name equals "Max" ........ pass
 * ```
 */
class TextReportPlugin(
    outputPath: Path = Path.of("berrycrush/report.txt"),
) : ReportPlugin(outputPath) {
    override val id: String = "report:text"
    override val name: String = "Text Report Plugin"

    companion object {
        private const val SEPARATOR = "═══════════════════════════════════════════════════════════════════════════════"
        private const val LINE_WIDTH = 60
        private val TIMESTAMP_FORMAT = DateTimeFormatter.ISO_INSTANT
    }

    override fun formatReport(report: TestReport): String =
        buildString {
            // Header
            appendLine(SEPARATOR)
            appendLine("BerryCrush Test Report")
            appendLine(SEPARATOR)
            appendLine("Execution Date: ${TIMESTAMP_FORMAT.format(report.timestamp)}")
            appendLine("Duration: ${formatDuration(report.duration.toMillis())}s")
            appendLine()

            // Scenarios
            for (scenario in report.scenarios) {
                appendScenario(scenario)
                appendLine()
            }

            // Summary
            appendLine(SEPARATOR)
            val percentage =
                if (report.summary.total > 0) {
                    (report.summary.passed * 100.0 / report.summary.total)
                } else {
                    0.0
                }
            appendLine(
                "Summary: ${report.summary.passed}/${report.summary.total} scenarios passed " +
                    "(${String.format("%.1f", percentage)}%)",
            )
            appendLine(
                "  ${report.summary.passed} passed, ${report.summary.failed} failed, " +
                    "${report.summary.skipped} skipped, ${report.summary.errors} errors",
            )

            val failedScenarios = report.scenarios.filter { it.status == ResultStatus.FAILED }
            if (failedScenarios.isNotEmpty()) {
                appendLine()
                appendLine("Failed Scenarios:")
                for (scenario in failedScenarios) {
                    appendLine("  - ${scenario.name}")
                }
            }
            appendLine(SEPARATOR)
        }

    private fun StringBuilder.appendScenario(scenario: ScenarioReportEntry) {
        val statusIcon = statusIcon(scenario.status)
        appendLine("scenario: ${scenario.name} $statusIcon")

        for (step in scenario.steps) {
            appendStep(step)
        }
    }

    private fun StringBuilder.appendStep(step: StepReportEntry) {
        val description = step.description
        val status = step.status
        val response = step.response
        val failure = step.failure

        // Determine what to show on the right side
        val rightSide =
            when {
                // If there's a response, show status code and message
                response != null -> "${response.statusCode} ${response.statusMessage}"
                // If it's an assertion step (description starts with "assert" or "then")
                failure != null -> statusLabel(status)
                // For other steps, show status
                else -> statusLabel(status)
            }

        // Calculate dot padding
        val leftPart = "  $description "
        val dotsNeeded = (LINE_WIDTH - leftPart.length - rightSide.length).coerceAtLeast(2)
        val dots = ".".repeat(dotsNeeded)

        appendLine("$leftPart$dots $rightSide")

        // Show failure details if present
        if (failure != null && status == ResultStatus.FAILED) {
            appendLine("    expected: ${failure.expected}")
            appendLine("    actual: ${failure.actual}")
        }
    }

    private fun statusIcon(status: ResultStatus): String =
        when (status) {
            ResultStatus.PASSED -> "✓"
            ResultStatus.FAILED -> "✗"
            ResultStatus.SKIPPED -> "○"
            ResultStatus.ERROR -> "⚠"
        }

    private fun statusLabel(status: ResultStatus): String =
        when (status) {
            ResultStatus.PASSED -> "pass"
            ResultStatus.FAILED -> "FAIL"
            ResultStatus.SKIPPED -> "skip"
            ResultStatus.ERROR -> "ERROR"
        }

    private fun formatDuration(millis: Long): String = String.format("%.3f", millis / 1000.0)
}
