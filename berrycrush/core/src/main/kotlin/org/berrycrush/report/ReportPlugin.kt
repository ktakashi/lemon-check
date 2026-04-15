package org.berrycrush.report

import org.berrycrush.plugin.BerryCrushPlugin
import org.berrycrush.plugin.ResultStatus
import org.berrycrush.plugin.ScenarioContext
import org.berrycrush.plugin.ScenarioResult
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Base class for report plugins that generate output at the end of test execution.
 *
 * Report plugins collect scenario results during execution and write a final report
 * when all scenarios have completed and generateReport() is called.
 */
abstract class ReportPlugin(
    /**
     * Output path for the report file.
     */
    val outputPath: Path,
) : BerryCrushPlugin {
    protected val scenarioReports = mutableListOf<ScenarioReportEntry>()
    protected var startTime: Instant? = null
    protected var endTime: Instant? = null

    /**
     * The test environment name (e.g., "staging", "production").
     * Set via [configureEnvironment] to include in reports.
     */
    protected var environmentName: String? = null

    override val priority: Int = 100 // Run after other plugins

    /**
     * Configure the environment name for this report.
     *
     * @param name The environment name (e.g., "staging", "production")
     */
    fun configureEnvironment(name: String?) {
        environmentName = name
    }

    override fun onScenarioStart(context: ScenarioContext) {
        if (startTime == null) {
            startTime = context.startTime
        }
    }

    override fun onScenarioEnd(
        context: ScenarioContext,
        result: ScenarioResult,
    ) {
        endTime = Instant.now()
        val entry =
            ScenarioReportEntry(
                name = context.scenarioName,
                status = mapStatus(result.status),
                duration = result.duration,
                steps =
                    result.stepResults.map { stepResult ->
                        StepReportEntry(
                            description = stepResult.stepDescription,
                            status = mapStatus(stepResult.status),
                            duration = stepResult.duration,
                            request = null, // HTTP snapshots can be added later
                            response =
                                if (stepResult.httpStatusCode != null) {
                                    org.berrycrush.plugin.HttpResponse(
                                        statusCode = stepResult.httpStatusCode!!,
                                        statusMessage = httpStatusMessage(stepResult.httpStatusCode!!),
                                        headers = stepResult.responseHeaders,
                                        body = stepResult.responseBody,
                                        duration = stepResult.duration,
                                        timestamp = Instant.now(),
                                    )
                                } else {
                                    null
                                },
                            failure = stepResult.failure,
                        )
                    },
                tags = context.tags,
                metadata = context.metadata,
                sourceFile = context.scenarioFile.fileName?.toString(),
            )
        scenarioReports.add(entry)
    }

    /**
     * Get HTTP status message for common status codes.
     */
    private fun httpStatusMessage(statusCode: Int): String =
        when (statusCode) {
            200 -> "OK"
            201 -> "Created"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            409 -> "Conflict"
            422 -> "Unprocessable Entity"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> ""
        }

    /**
     * Called at the end of test execution to generate the report.
     *
     * This hook is automatically invoked after all scenarios have completed,
     * triggering report generation and file output.
     */
    override fun onTestExecutionEnd() {
        generateReport()
    }

    /**
     * Generate the final report and write to output path.
     *
     * Can also be called manually if needed, but typically
     * invoked automatically via onTestExecutionEnd().
     */
    fun generateReport() {
        val report = buildReport()
        val content = formatReport(report)
        writeReport(content)
    }

    /**
     * Build the TestReport from collected scenario data.
     */
    protected fun buildReport(): TestReport {
        val summary = TestSummaryBuilder.fromPluginStatus(scenarioReports) { it.status }

        val totalDuration =
            scenarioReports.fold(Duration.ZERO) { acc, entry ->
                acc.plus(entry.duration)
            }

        return TestReport(
            timestamp = startTime ?: Instant.now(),
            duration = totalDuration,
            summary = summary,
            scenarios = scenarioReports.toList(),
            environment = collectEnvironment(),
        )
    }

    /**
     * Format the report to a string representation.
     *
     * Subclasses must implement this to produce the output format.
     */
    abstract fun formatReport(report: TestReport): String

    /**
     * Write the report content to the output file.
     */
    protected open fun writeReport(content: String) {
        Files.createDirectories(outputPath.parent)
        Files.writeString(outputPath, content)
    }

    /**
     * Collect environment information for the report.
     */
    protected fun collectEnvironment(): Map<String, String> =
        buildMap {
            put("java.version", System.getProperty("java.version", "unknown"))
            put("os.name", System.getProperty("os.name", "unknown"))
            put("os.version", System.getProperty("os.version", "unknown"))
            environmentName?.let { put("environment", it) }
        }

    protected fun mapStatus(status: ResultStatus): ResultStatus =
        when (status) {
            ResultStatus.PASSED -> ResultStatus.PASSED
            ResultStatus.FAILED -> ResultStatus.FAILED
            ResultStatus.SKIPPED -> ResultStatus.SKIPPED
            ResultStatus.ERROR -> ResultStatus.ERROR
        }
}
