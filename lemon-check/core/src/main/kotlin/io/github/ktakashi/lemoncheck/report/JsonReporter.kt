package io.github.ktakashi.lemoncheck.report

import io.github.ktakashi.lemoncheck.model.ScenarioResult
import io.github.ktakashi.lemoncheck.model.StepResult
import io.github.ktakashi.lemoncheck.model.TestReport
import java.io.File
import java.io.Writer
import java.time.format.DateTimeFormatter

/**
 * JSON format reporter for test results.
 *
 * Generates JSON reports suitable for CI/CD integration and automated processing.
 */
class JsonReporter : TestReporter {
    override fun onScenarioStart(scenarioName: String) {
        // JSON reporter doesn't output on start
    }

    override fun onStepComplete(stepResult: StepResult) {
        // JSON reporter doesn't output per step
    }

    override fun onScenarioComplete(result: ScenarioResult) {
        println(toJson(result))
    }

    override fun onSuiteComplete(results: List<ScenarioResult>) {
        val report = TestReport.fromResults("Test Suite", results)
        println(toJson(report))
    }

    /**
     * Report a single scenario result to a writer.
     */
    fun report(
        result: ScenarioResult,
        writer: Writer,
    ) {
        writer.write(toJson(result))
        writer.flush()
    }

    /**
     * Generate a full test report and write to a file.
     */
    fun report(
        report: TestReport,
        file: File,
    ) {
        file.writeText(toJson(report))
    }

    /**
     * Generate a full test report as JSON string.
     */
    fun report(report: TestReport): String = toJson(report)

    /**
     * Convert a scenario result to JSON.
     */
    fun toJson(result: ScenarioResult): String =
        buildString {
            append("{\n")
            append("  \"scenarioName\": ${jsonString(result.scenario.name)},\n")
            append("  \"status\": ${jsonString(result.status.name)},\n")
            append("  \"durationMs\": ${result.duration.toMillis()},\n")
            append("  \"stepResults\": [\n")

            result.stepResults.forEachIndexed { index, step ->
                append("    {\n")
                append("      \"stepDescription\": ${jsonString(step.step.description)},\n")
                append("      \"status\": ${jsonString(step.status.name)},\n")
                append("      \"durationMs\": ${step.duration.toMillis()}")
                if (step.error != null) {
                    append(",\n      \"errorMessage\": ${jsonString(step.error.message ?: "Unknown error")}")
                    append(",\n      \"stackTrace\": ${jsonString(step.error.stackTraceToString())}")
                }
                append("\n    }")
                if (index < result.stepResults.size - 1) append(",")
                append("\n")
            }

            append("  ]\n")
            append("}")
        }

    /**
     * Convert a test report to JSON.
     */
    fun toJson(report: TestReport): String =
        buildString {
            append("{\n")
            append("  \"title\": ${jsonString(report.title)},\n")
            append("  \"timestamp\": ${jsonString(DateTimeFormatter.ISO_INSTANT.format(report.timestamp))},\n")
            append("  \"summary\": {\n")
            append("    \"totalScenarios\": ${report.totalScenarios},\n")
            append("    \"passed\": ${report.passed},\n")
            append("    \"failed\": ${report.failed},\n")
            append("    \"skipped\": ${report.skipped},\n")
            append("    \"successRate\": ${report.successRate},\n")
            append("    \"totalDurationMs\": ${report.totalDurationMs}\n")
            append("  },\n")

            if (report.environment.isNotEmpty()) {
                append("  \"environment\": {\n")
                report.environment.entries.forEachIndexed { index, (key, value) ->
                    append("    ${jsonString(key)}: ${jsonString(value)}")
                    if (index < report.environment.size - 1) append(",")
                    append("\n")
                }
                append("  },\n")
            }

            append("  \"scenarios\": [\n")
            report.scenarioResults.forEachIndexed { index, result ->
                val lines = toJson(result).lines()
                lines.forEachIndexed { lineIndex, line ->
                    append("    $line")
                    if (lineIndex < lines.size - 1) append("\n")
                }
                if (index < report.scenarioResults.size - 1) append(",")
                append("\n")
            }
            append("  ]\n")
            append("}")
        }

    private fun jsonString(value: String?): String {
        if (value == null) return "null"
        return "\"${
            value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }\""
    }
}
