package org.berrycrush.report

import java.nio.file.Path

/**
 * Report plugin that generates generic XML output.
 *
 * The XML report is a generic structure that includes all test execution data.
 */
class XmlReportPlugin(
    outputPath: Path = Path.of("berrycrush/report.xml"),
) : ReportPlugin(outputPath) {
    override val id: String = "report:xml"
    override val name: String = "XML Report Plugin"

    override fun formatReport(report: TestReport): String =
        buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<testReport xmlns="https://berrycrush.io/schema/report/1.0">""")

            // Metadata
            appendLine("""  <timestamp>${report.timestamp}</timestamp>""")
            appendLine("""  <duration>${report.duration.toMillis()}</duration>""")

            // Summary
            appendLine("  <summary>")
            appendLine("""    <total>${report.summary.total}</total>""")
            appendLine("""    <passed>${report.summary.passed}</passed>""")
            appendLine("""    <failed>${report.summary.failed}</failed>""")
            appendLine("""    <skipped>${report.summary.skipped}</skipped>""")
            appendLine("""    <errors>${report.summary.errors}</errors>""")
            appendLine("  </summary>")

            // Scenarios
            appendLine("  <scenarios>")
            for (scenario in report.scenarios) {
                with(scenario) {
                    appendLine(
                        """    <scenario name="${escapeXml(name)}" status="$status" duration="${duration.toMillis()}">""",
                    )

                    // Tags
                    if (tags.isNotEmpty()) {
                        appendLine("      <tags>")
                        for (tag in tags) {
                            appendLine("""        <tag>${escapeXml(tag)}</tag>""")
                        }
                        appendLine("      </tags>")
                    }

                    // Steps
                    appendLine("      <steps>")
                    for (step in steps) {
                        with(step) {
                            append(
                                """        <step description="${escapeXml(description)}"""",
                            )
                            appendLine(""" status="$status" duration="${duration.toMillis()}">""")

                            failure?.let { failure ->
                                appendLine("          <failure>")
                                appendLine("""            <message>${escapeXml(failure.message)}</message>""")
                                failure.expected?.let { appendLine("""            <expected>${escapeXml(it.toString())}</expected>""") }
                                failure.actual?.let { appendLine("""            <actual>${escapeXml(it.toString())}</actual>""") }
                                failure.diff?.let { appendLine("""            <diff>${escapeXml(it)}</diff>""") }
                                appendLine("          </failure>")
                            }

                            appendLine("        </step>")
                        }
                    }
                    appendLine("      </steps>")
                    appendLine("    </scenario>")
                }
                appendLine("  </scenarios>")

                // Environment
                appendLine("  <environment>")
                for ((key, value) in report.environment) {
                    appendLine("""    <property name="${escapeXml(key)}">${escapeXml(value)}</property>""")
                }
                appendLine("  </environment>")

                appendLine("</testReport>")
            }
        }

    private fun escapeXml(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
