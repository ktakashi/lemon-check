package org.berrycrush.junit.plugin

import org.berrycrush.exception.ErrorContextConfig
import org.berrycrush.formatter.ErrorContextFormatter
import org.berrycrush.model.HttpRequest
import org.berrycrush.model.HttpResponse
import org.berrycrush.plugin.BerryCrushPlugin
import org.berrycrush.plugin.ResultStatus
import org.berrycrush.plugin.ScenarioContext
import org.berrycrush.plugin.ScenarioResult
import org.berrycrush.plugin.StepContext
import org.berrycrush.plugin.StepResult
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import java.io.PrintStream

/**
 * Plugin that prints scenario and step results to the console for JUnit visibility.
 *
 * This plugin:
 * - Collects step information during execution
 * - Prints formatted results when each scenario completes
 * - Tracks pass/fail status for JUnit result reporting
 * - Fires JUnit EngineExecutionListener events for IDE integration
 *
 * ## Usage
 *
 * Create a new plugin instance per scenarioDescriptor (scenario file) and replace
 * in the registry:
 *
 * ```kotlin
 * val consolePlugin = ConsoleOutputPlugin(
 *     listener = engineExecutionListener,
 *     scenarioDescriptor = scenarioDescriptor
 * )
 * pluginRegistry.replace(consolePlugin)
 * // ... execute scenarios ...
 * consolePlugin.reportResult() // fires JUnit executionFinished
 * ```
 *
 * @property output PrintStream for output (defaults to System.out)
 * @property listener JUnit EngineExecutionListener for IDE events
 * @property scenarioDescriptor TestDescriptor for the scenario file
 */
class ConsoleOutputPlugin(
    private val output: PrintStream = System.out,
    private val listener: EngineExecutionListener? = null,
    private val scenarioDescriptor: TestDescriptor? = null,
) : BerryCrushPlugin {
    override val id: String = PLUGIN_ID
    override val name: String = "Console Output"
    override val priority: Int = -50 // Run early, before report plugins

    // Step info collected during scenario execution
    private data class StepInfo(
        val description: String,
        val status: ResultStatus,
        val statusCode: Int?,
        val errorMessage: String?,
        val failureMessage: String?,
        val request: HttpRequest?,
        val response: HttpResponse?,
    )

    // Collected step info per scenario
    private val scenarioSteps = mutableMapOf<String, MutableList<StepInfo>>()

    // Result tracking for JUnit integration
    private var allPassed = true
    private var firstFailureReason: Throwable? = null

    override fun onScenarioStart(context: ScenarioContext) {
        // Initialize step collection for this scenario
        scenarioSteps[context.scenarioName] = mutableListOf()
    }

    override fun onStepEnd(
        context: StepContext,
        result: StepResult,
    ) {
        // Collect step info for printing during onScenarioEnd
        val stepInfo =
            StepInfo(
                description = context.stepDescription,
                status = result.status,
                statusCode = extractStatusCode(context),
                errorMessage = result.error?.message,
                failureMessage = result.failure?.message,
                request = context.request,
                response = context.response,
            )
        scenarioSteps[context.scenarioContext.scenarioName]?.add(stepInfo)
    }

    override fun onScenarioEnd(
        context: ScenarioContext,
        result: ScenarioResult,
    ) {
        // Print scenario header
        output.println()
        output.println("=== Scenario: ${context.scenarioName} ===")

        // Print step results
        val steps = scenarioSteps[context.scenarioName] ?: emptyList()
        for (step in steps) {
            with(step) {
                val statusIcon = getStatusIcon(status)
                output.println("  $statusIcon $description: $status")

                // Show HTTP status if available
                statusCode?.let { status ->
                    output.println("    HTTP Status: $status")
                }

                // Show failure details if present
                failureMessage?.let { message ->
                    output.println("    \u2717 $message")
                }

                // Show error if present
                errorMessage?.let { error ->
                    output.println("    Error: $error")
                }
            }
        }

        // Print scenario summary
        output.println("  Result: ${result.status} (${result.duration.toMillis()}ms)")
        output.println()

        // Track failures for result reporting
        if (result.status != ResultStatus.PASSED && firstFailureReason == null) {
            allPassed = false
            firstFailureReason = buildFailureException(context.scenarioName)
        }

        // Cleanup step collection for this scenario
        scenarioSteps.remove(context.scenarioName)
    }

    /**
     * Report the final result to JUnit listener.
     *
     * Call this after all scenarios in the scenarioDescriptor have been executed.
     * This fires executionFinished on the JUnit listener.
     */
    fun reportResult() {
        if (listener == null || scenarioDescriptor == null) return

        val testResult =
            if (allPassed) {
                TestExecutionResult.successful()
            } else {
                TestExecutionResult.failed(firstFailureReason!!)
            }
        listener.executionFinished(scenarioDescriptor, testResult)
    }

    /**
     * Check if all executed scenarios passed.
     *
     * @return true if all scenarios passed, false if any failed
     */
    fun isAllPassed(): Boolean = allPassed

    /**
     * Get the failure reason for the first failed scenario.
     *
     * @return AssertionError with failure details, or null if all passed
     */
    fun getFailureReason(): Throwable? = firstFailureReason

    /**
     * Extract HTTP status code from step context if available.
     */
    private fun extractStatusCode(context: StepContext): Int? = context.response?.statusCode

    /**
     * Get icon for status.
     */
    private fun getStatusIcon(status: ResultStatus): String =
        when (status) {
            ResultStatus.PASSED -> "\u2713"
            ResultStatus.FAILED -> "\u2717"
            ResultStatus.ERROR -> "!"
            ResultStatus.SKIPPED -> "-"
        }

    /**
     * Build failure exception from collected step info.
     */
    private fun buildFailureException(scenarioName: String): Throwable {
        val steps = scenarioSteps[scenarioName] ?: emptyList()
        val formatter = ErrorContextFormatter.plain()
        val errorConfig = ErrorContextConfig(maxBodySize = DEFAULT_ERROR_BODY_SIZE)

        val failedSteps =
            steps
                .filter { it.status != ResultStatus.PASSED }
                .joinToString("\n\n") { step ->
                    buildString {
                        append("  Step: ${step.description}")
                        append("\n    Status: ${step.status}")

                        step.failureMessage?.let { msg ->
                            append("\n    $FAILURE_ICON $msg")
                        }
                        step.errorMessage?.let { err ->
                            append("\n    Error: $err")
                        }

                        // Include HTTP context for failed steps
                        if (step.status == ResultStatus.FAILED && step.response != null) {
                            step.request?.let { req ->
                                append("\n")
                                append(formatter.formatRequest(req, errorConfig).prependIndent("    "))
                            }
                            step.response.let { resp ->
                                append("\n")
                                append(formatter.formatResponse(resp, errorConfig).prependIndent("    "))
                            }
                        }
                    }
                }
        return AssertionError("Scenario '$scenarioName' failed:\n$failedSteps")
    }

    companion object {
        /**
         * Plugin ID for console output plugin.
         * Uses a fixed ID to enable replacement via PluginRegistry.replace().
         */
        const val PLUGIN_ID = "org.berrycrush.junit.console-output"

        private const val FAILURE_ICON = "\u2717"
        private const val DEFAULT_ERROR_BODY_SIZE = 2048
    }
}
