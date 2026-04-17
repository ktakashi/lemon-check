package org.berrycrush.report

import org.berrycrush.plugin.ResultStatus
import java.time.format.DateTimeFormatter

/**
 * Formats test reports in human-readable text format.
 *
 * This formatter handles all text layout and structure, delegating colorization
 * to a [ColorScheme]. Use [ColorScheme.NONE] for plain text output without colors.
 *
 * ## Example Output
 *
 * ```
 * ═══════════════════════════════════════════════════════════════════════════════
 * BerryCrush Test Report
 * ═══════════════════════════════════════════════════════════════════════════════
 * Execution Date: 2024-01-15T10:30:00Z
 * Duration: 1.234s
 *
 * scenario: Get pet by ID ✓
 *   when I request a specific pet
 *     call ^getPetById .................. 200 OK
 *   then I get the pet details
 *     assert status 200 ................. pass
 *     assert $.name equals "Max" ........ pass
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * Summary: 1/1 scenarios passed (100.0%)
 *   1 passed, 0 failed, 0 skipped, 0 errors
 * ═══════════════════════════════════════════════════════════════════════════════
 * ```
 *
 * @property colorScheme Color scheme for terminal output. Use [ColorScheme.NONE] for plain text.
 */
class TextReportFormatter(
    private val colorScheme: ColorScheme = ColorScheme.NONE,
) {
    companion object {
        private const val SEPARATOR = "═══════════════════════════════════════════════════════════════════════════════"
        private const val LINE_WIDTH = 60
        private val TIMESTAMP_FORMAT = DateTimeFormatter.ISO_INSTANT

        /**
         * Create a plain text formatter with no colors.
         */
        fun plain(): TextReportFormatter = TextReportFormatter(ColorScheme.NONE)

        /**
         * Create a colored formatter with the default color scheme.
         */
        fun colored(): TextReportFormatter = TextReportFormatter(ColorScheme.DEFAULT)

        /**
         * Create a colored formatter with a custom color scheme.
         */
        fun colored(scheme: ColorScheme): TextReportFormatter = TextReportFormatter(scheme)
    }

    /**
     * Format a complete test report.
     *
     * @param report The test report data
     * @return Formatted report string
     */
    fun formatReport(report: TestReport): String =
        buildString {
            // Header
            appendLine(colorScheme.headerStyle(SEPARATOR))
            appendLine(colorScheme.headerStyle("BerryCrush Test Report"))
            appendLine(colorScheme.headerStyle(SEPARATOR))
            appendLine("Execution Date: ${TIMESTAMP_FORMAT.format(report.timestamp)}")
            appendLine("Duration: ${formatDuration(report.duration.toMillis())}s")
            appendLine()

            // Scenarios
            for (scenario in report.scenarios) {
                appendScenario(scenario)
                appendLine()
            }

            // Summary
            appendLine(colorScheme.headerStyle(SEPARATOR))
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
                "  ${colorScheme.colorize("${report.summary.passed} passed", ResultStatus.PASSED)}, " +
                    "${colorScheme.colorize("${report.summary.failed} failed", ResultStatus.FAILED)}, " +
                    "${colorScheme.colorize("${report.summary.skipped} skipped", ResultStatus.SKIPPED)}, " +
                    "${colorScheme.colorize("${report.summary.errors} errors", ResultStatus.ERROR)}",
            )

            val failedScenarios = report.scenarios.filter { it.status == ResultStatus.FAILED }
            if (failedScenarios.isNotEmpty()) {
                appendLine()
                appendLine("Failed Scenarios:")
                for (scenario in failedScenarios) {
                    appendLine("  - ${colorScheme.colorize(scenario.name, ResultStatus.FAILED)}")
                }
            }
            appendLine(colorScheme.headerStyle(SEPARATOR))
        }

    private fun StringBuilder.appendScenario(scenario: ScenarioReportEntry) {
        val statusIcon = statusIcon(scenario.status)
        val coloredIcon = colorScheme.colorize(statusIcon, scenario.status)
        appendLine("scenario: ${scenario.name} $coloredIcon")

        for (step in scenario.steps) {
            appendStep(step)
        }
    }

    private fun StringBuilder.appendStep(step: StepReportEntry) {
        val description = step.description
        val status = step.status
        val response = step.response
        val failure = step.failure
        val isCustom = step.isCustomStep

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

        // Apply coloring
        val coloredRightSide = colorScheme.colorize(rightSide, status)
        val displayDescription =
            if (isCustom) {
                colorScheme.highlight(description)
            } else {
                description
            }

        appendLine("  $displayDescription $dots $coloredRightSide")

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

    private fun formatDuration(millis: Long): String = String.format(java.util.Locale.US, "%.3f", millis / 1000.0)
}
