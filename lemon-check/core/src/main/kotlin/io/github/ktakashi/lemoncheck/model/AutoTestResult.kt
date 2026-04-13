package io.github.ktakashi.lemoncheck.model

import io.github.ktakashi.lemoncheck.autotest.AutoTestCase
import java.time.Duration

/**
 * Result of executing a single auto-generated test case.
 *
 * @property testCase The test case that was executed
 * @property passed Whether the test passed
 * @property statusCode HTTP status code from response
 * @property responseBody Response body (truncated for large responses)
 * @property assertionResults Results from conditional assertions
 * @property duration Time taken to execute the test
 * @property error Error message if an exception occurred
 */
data class AutoTestResult(
    val testCase: AutoTestCase,
    val passed: Boolean,
    val statusCode: Int? = null,
    val responseBody: String? = null,
    val assertionResults: List<AssertionResult> = emptyList(),
    val duration: Duration = Duration.ZERO,
    val error: String? = null,
)
