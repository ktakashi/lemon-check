package org.berrycrush.report

import org.berrycrush.plugin.BerryCrushPlugin
import org.berrycrush.plugin.ResultStatus
import org.berrycrush.plugin.ScenarioContext
import org.berrycrush.plugin.ScenarioResult
import java.io.PrintStream
import java.time.Duration
import java.time.Instant

/**
 * Report plugin that outputs colored test results to the console.
 *
 * This plugin generates ANSI-colored output for terminal display, making it
 * easy to visually identify passed, failed, and custom steps at a glance.
 *
 * ## Features
 *
 * - **Color-coded results**: Green for passed, red for failed, gray for skipped
 * - **Custom step highlighting**: Bold/bright colors for custom steps and assertions
 * - **TTY detection**: Detects if output is a terminal (though ANSI codes are always emitted)
 * - **Configurable colors**: Customize colors via [ColorScheme]
 * - **Streaming output**: Can output to stdout or stderr
 *
 * ## Usage
 *
 * ```kotlin
 * @BerryCrushConfiguration(
 *     pluginClasses = [ConsoleReportPlugin::class]
 * )
 * class MyTest { ... }
 * ```
 *
 * Or with custom configuration:
 *
 * ```kotlin
 * val plugin = ConsoleReportPlugin(
 *     output = System.err,
 *     colorScheme = ColorScheme.HIGH_CONTRAST
 * )
 * ```
 *
 * ## Example Output
 *
 * ```
 * ═══════════════════════════════════════════════════════════════════════════════
 * BerryCrush Test Report
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * scenario: Get pet by ID ✓  (green checkmark)
 *   call ^getPetById .................. 200 OK
 *   assert status 200 ................. pass  (green)
 *   verify custom assertion ........... pass  (bold cyan + green)
 *
 * scenario: Invalid pet ID ✗  (red X)
 *   assert status 404 ................. FAIL  (red)
 * ```
 *
 * @property output PrintStream for output (default: System.out)
 * @property colorScheme Color configuration for terminal output
 * @see TextReportPlugin for plain text file output
 */
class ConsoleReportPlugin(
    private val output: PrintStream = System.out,
    private val colorScheme: ColorScheme = ColorScheme.DEFAULT,
) : BerryCrushPlugin {
    override val id: String = "report:console"
    override val name: String = "Console Report Plugin"
    override val priority: Int = 100 // Run after other plugins

    private val scenarioReports = mutableListOf<ScenarioReportEntry>()
    private var startTime: Instant? = null
    private var endTime: Instant? = null

    private val formatter = TextReportFormatter.colored(colorScheme)

    /**
     * Check if the output is a TTY (terminal).
     *
     * This information can be used for diagnostics. Note that ANSI codes
     * are always emitted regardless of TTY status per requirements.
     */
    val isTty: Boolean
        get() = System.console() != null

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
        val entry = buildScenarioEntry(context, result)
        scenarioReports.add(entry)
    }

    /**
     * Called at the end of test execution to generate the colored report.
     *
     * This hook is automatically invoked after all scenarios have completed.
     */
    override fun onTestExecutionEnd() {
        generateReport()
    }

    /**
     * Generate and print the report.
     *
     * Can be called manually if needed, but typically invoked automatically
     * via onTestExecutionEnd().
     */
    fun generateReport() {
        val report = buildReport()
        val formatted = formatter.formatReport(report)
        output.println(formatted)
    }

    /**
     * Build a TestReport from collected scenario results.
     */
    private fun buildReport(): TestReport {
        val duration =
            if (startTime != null && endTime != null) {
                Duration.between(startTime, endTime)
            } else {
                Duration.ZERO
            }

        return TestReport(
            scenarios = scenarioReports,
            summary =
                TestSummary(
                    total = scenarioReports.size,
                    passed = scenarioReports.count { it.status == ResultStatus.PASSED },
                    failed = scenarioReports.count { it.status == ResultStatus.FAILED },
                    skipped = scenarioReports.count { it.status == ResultStatus.SKIPPED },
                    errors = scenarioReports.count { it.status == ResultStatus.ERROR },
                ),
            timestamp = startTime ?: Instant.now(),
            duration = duration,
        )
    }

    /**
     * Build a ScenarioReportEntry from context and result.
     */
    private fun buildScenarioEntry(
        context: ScenarioContext,
        result: ScenarioResult,
    ): ScenarioReportEntry =
        ScenarioReportEntry(
            name = context.scenarioName,
            status = result.status,
            duration = result.duration,
            steps =
                result.stepResults.map { stepResult ->
                    StepReportEntry(
                        description = stepResult.stepDescription,
                        status = stepResult.status,
                        duration = stepResult.duration,
                        request = null,
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
                        isCustomStep = stepResult.isCustomStep,
                    )
                },
            tags = context.tags,
            metadata = context.metadata,
            sourceFile = context.scenarioFile.fileName?.toString(),
        )

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
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> ""
        }

    companion object {
        /**
         * Create a console report plugin with default settings (stdout, default colors).
         */
        fun default(): ConsoleReportPlugin = ConsoleReportPlugin()

        /**
         * Create a console report plugin that writes to stderr.
         */
        fun stderr(): ConsoleReportPlugin = ConsoleReportPlugin(output = System.err)

        /**
         * Create a console report plugin with high contrast colors.
         */
        fun highContrast(): ConsoleReportPlugin =
            ConsoleReportPlugin(
                colorScheme = ColorScheme.HIGH_CONTRAST,
            )

        /**
         * Create a console report plugin from configuration string.
         *
         * Supported options (can be combined with comma):
         * - `stderr` - Output to stderr instead of stdout
         * - `stdout` - Output to stdout (default)
         * - `high-contrast` - Use high contrast color scheme
         * - `monochrome` - Use monochrome (styles only, no colors)
         * - `no-color` - Disable all colors/styles
         *
         * ## Examples
         *
         * ```kotlin
         * // In @BerryCrushConfiguration
         * plugins = ["report:console:stderr,high-contrast"]
         * plugins = ["report:console:monochrome"]
         * plugins = ["report:console:stderr"]
         * ```
         *
         * @param options Comma-separated configuration options
         */
        fun fromOptions(options: String): ConsoleReportPlugin {
            val optionList = options.lowercase().split(",").map { it.trim() }

            // Determine output stream
            val output =
                when {
                    "stderr" in optionList -> System.err
                    else -> System.out
                }

            // Determine color scheme
            val colorScheme =
                when {
                    "high-contrast" in optionList || "highcontrast" in optionList -> ColorScheme.HIGH_CONTRAST
                    "monochrome" in optionList || "mono" in optionList -> ColorScheme.MONOCHROME
                    "no-color" in optionList || "nocolor" in optionList || "none" in optionList -> ColorScheme.NONE
                    else -> ColorScheme.DEFAULT
                }

            return ConsoleReportPlugin(output = output, colorScheme = colorScheme)
        }
    }

    /**
     * Secondary constructor for ServiceLoader/reflection instantiation with options.
     *
     * This constructor is used by PluginNameResolver when a configuration string
     * is provided, e.g., `report:console:stderr,high-contrast`.
     *
     * @param options Comma-separated configuration string
     */
    constructor(options: String) : this(
        output = if (options.lowercase().contains("stderr")) System.err else System.out,
        colorScheme =
            when {
                options.lowercase().let { it.contains("high-contrast") || it.contains("highcontrast") } -> ColorScheme.HIGH_CONTRAST
                options.lowercase().let { it.contains("monochrome") || it.contains("mono") } -> ColorScheme.MONOCHROME
                options.lowercase().let { it.contains("no-color") || it.contains("nocolor") || it.contains("none") } -> ColorScheme.NONE
                else -> ColorScheme.DEFAULT
            },
    )
}
