package org.berrycrush.model

import java.time.Duration

/**
 * Result of executing a single step.
 *
 * @property step The step that was executed
 * @property status Result status
 * @property statusCode HTTP status code from response
 * @property responseBody Response body content
 * @property responseHeaders Response headers
 * @property duration Time taken to execute the step
 * @property extractedValues Values extracted from response
 * @property assertionResults Results of each assertion
 * @property error Exception if an error occurred
 * @property message Optional message providing additional context
 * @property autoTestResults Results from auto-generated tests (if autoTestConfig was used)
 * @property isCustomStep True if this step was executed via a custom @Step or @Assertion annotation
 */
data class StepResult(
    val step: Step,
    val status: ResultStatus,
    val statusCode: Int? = null,
    val responseBody: String? = null,
    val responseHeaders: Map<String, List<String>> = emptyMap(),
    val duration: Duration = Duration.ZERO,
    val extractedValues: Map<String, Any?> = emptyMap(),
    val assertionResults: List<AssertionResult> = emptyList(),
    val error: Throwable? = null,
    val message: String? = null,
    val autoTestResults: List<AutoTestResult> = emptyList(),
    val isCustomStep: Boolean = false,
)

/**
 * Result of a single assertion check.
 */
data class AssertionResult(
    val assertion: Assertion,
    val passed: Boolean,
    val message: String,
    val actual: Any? = null,
)
