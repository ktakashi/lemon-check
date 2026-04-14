package org.berrycrush.report

import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.nio.file.Path

/**
 * Report plugin that generates JSON output conforming to JSON Schema 2020-12.
 *
 * The JSON report is machine-parseable and includes:
 * - Full test execution metadata
 * - Detailed scenario and step results
 * - HTTP request/response snapshots for failures
 * - Environment information
 */
class JsonReportPlugin(
    outputPath: Path = Path.of("berrycrush/report.json"),
    /**
     * Whether to pretty-print the JSON output.
     */
    prettyPrint: Boolean = true,
) : ReportPlugin(outputPath) {
    override val id: String = "report:json"
    override val name: String = "JSON Report Plugin"

    private val mapper: JsonMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .configure(SerializationFeature.INDENT_OUTPUT, prettyPrint)
            .build()

    override fun formatReport(report: TestReport): String {
        val jsonReport = toJsonStructure(report)
        return mapper.writeValueAsString(jsonReport)
    }

    private fun toJsonStructure(report: TestReport): Map<String, Any?> =
        mapOf(
            "timestamp" to report.timestamp.toString(),
            "duration" to report.duration.toMillis(),
            "summary" to
                mapOf(
                    "total" to report.summary.total,
                    "passed" to report.summary.passed,
                    "failed" to report.summary.failed,
                    "skipped" to report.summary.skipped,
                    "errors" to report.summary.errors,
                ),
            "scenarios" to report.scenarios.map { toScenarioJson(it) },
            "environment" to report.environment,
        )

    private fun toScenarioJson(scenario: ScenarioReportEntry): Map<String, Any?> =
        mapOf(
            "name" to scenario.name,
            "status" to scenario.status.name,
            "duration" to scenario.duration.toMillis(),
            "tags" to scenario.tags.toList(),
            "steps" to scenario.steps.map { toStepJson(it) },
        )

    private fun toStepJson(step: StepReportEntry): Map<String, Any?> =
        mapOf(
            "description" to step.description,
            "status" to step.status.name,
            "duration" to step.duration.toMillis(),
            "failure" to step.failure?.let { toFailureJson(it) },
            "request" to step.request?.let { toRequestJson(it) },
            "response" to step.response?.let { toResponseJson(it) },
        )

    private fun toFailureJson(failure: org.berrycrush.plugin.AssertionFailure): Map<String, Any?> =
        mapOf(
            "message" to failure.message,
            "expected" to failure.expected?.toString(),
            "actual" to failure.actual?.toString(),
            "diff" to failure.diff,
            "assertionType" to failure.assertionType,
        )

    private fun toRequestJson(request: org.berrycrush.plugin.HttpRequest): Map<String, Any?> =
        mapOf(
            "method" to request.method,
            "url" to request.url,
            "headers" to request.headers,
            "body" to request.body,
            "timestamp" to request.timestamp.toString(),
        )

    private fun toResponseJson(response: org.berrycrush.plugin.HttpResponse): Map<String, Any?> =
        mapOf(
            "statusCode" to response.statusCode,
            "statusMessage" to response.statusMessage,
            "headers" to response.headers,
            "body" to response.body,
            "duration" to response.duration.toMillis(),
            "timestamp" to response.timestamp.toString(),
        )
}
