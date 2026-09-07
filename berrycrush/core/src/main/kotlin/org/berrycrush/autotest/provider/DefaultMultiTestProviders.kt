package org.berrycrush.autotest.provider

import org.berrycrush.autotest.MultiTestResult
import org.berrycrush.autotest.RequestResult
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Built-in multi-test providers for idempotency testing.
 *
 * These providers implement the two core multi-test modes:
 * - Sequential: Sends requests one after another
 * - Concurrent: Sends requests in parallel using a thread pool
 */
object DefaultMultiTestProviders {
    /**
     * All built-in multi-test providers.
     */
    val all: List<MultiTestProvider> =
        listOf(
            SequentialMultiTestProvider,
            ConcurrentMultiTestProvider,
        )
}

/**
 * Sequential multi-test provider.
 *
 * Executes requests one after another, waiting for each to complete
 * before starting the next. Useful for testing that repeated sequential
 * calls produce idempotent results.
 */
object SequentialMultiTestProvider : MultiTestProvider {
    override val mode: String = "sequential"
    override val displayName: String = "Sequential Idempotency"
    override val defaultCount: Int = 3

    override fun executeMultiTest(
        count: Int,
        executor: (requestIndex: Int) -> RequestResult,
    ): MultiTestResult {
        val startTime = Instant.now()
        val results =
            (0 until count).map { index ->
                executor(index)
            }
        val totalDuration = Duration.between(startTime, Instant.now())

        return buildResult(
            mode = mode,
            results = results,
            totalDuration = totalDuration,
        )
    }
}

/**
 * Concurrent multi-test provider.
 *
 * Executes all requests in parallel using a thread pool.
 * Useful for detecting race conditions and concurrent access issues.
 */
object ConcurrentMultiTestProvider : MultiTestProvider {
    override val mode: String = "concurrent"
    override val displayName: String = "Concurrent Idempotency"
    override val defaultCount: Int = 5

    override fun executeMultiTest(
        count: Int,
        executor: (requestIndex: Int) -> RequestResult,
    ): MultiTestResult {
        val startTime = Instant.now()
        val threadPool = Executors.newFixedThreadPool(count.coerceAtMost(MAX_THREADS))
        try {
            val futures =
                (0 until count).map { index ->
                    threadPool.submit(Callable { executor(index) })
                }
            val results = futures.map { it.get() }
            val totalDuration = Duration.between(startTime, Instant.now())

            return buildResult(
                mode = mode,
                results = results,
                totalDuration = totalDuration,
            )
        } finally {
            threadPool.shutdown()
        }
    }

    private const val MAX_THREADS = 20
}

/**
 * Build a MultiTestResult from the collected results.
 */
private fun buildResult(
    mode: String,
    results: List<RequestResult>,
    totalDuration: Duration,
): MultiTestResult {
    val passed = results.all { it.assertionResults.all { r -> r.passed } }
    val failureReason =
        if (!passed) {
            results
                .filter { it.assertionResults.any { r -> !r.passed } }
                .joinToString("\n") { result ->
                    val failedAssertions =
                        result.assertionResults.filter { !it.passed }
                    val assertionMessages =
                        failedAssertions
                            .filter { it.message != null }
                            .joinToString("; ") { it.message!! }
                    "Request ${result.requestIndex + 1}: $assertionMessages"
                }
        } else {
            null
        }

    return MultiTestResult(
        mode = mode,
        requestCount = results.size,
        results = results,
        totalDuration = totalDuration,
        passed = passed,
        failureReason = failureReason,
    )
}
