package org.berrycrush.report

import java.nio.file.Path

/**
 * Report plugin that generates human-readable text output in scenario format.
 *
 * The text report includes:
 * - Summary header with execution date, duration, and totals
 * - Per-scenario details styled like scenario files
 * - Step-by-step results with status indicators and dotted leaders
 * - Failure details with expected/actual values
 *
 * This plugin delegates formatting to [TextReportFormatter] with no colors,
 * ensuring output is clean text without ANSI escape codes.
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
 *
 * @see ConsoleReportPlugin for colored console output
 */
class TextReportPlugin(
    outputPath: Path = Path.of("berrycrush/report.txt"),
) : ReportPlugin(outputPath) {
    override val id: String = "report:text"
    override val name: String = "Text Report Plugin"

    private val formatter = TextReportFormatter.plain()

    override fun formatReport(report: TestReport): String = formatter.formatReport(report)
}
