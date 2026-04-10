package io.github.ktakashi.lemoncheck.report

import io.github.ktakashi.lemoncheck.plugin.ResultStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.test.assertTrue

/**
 * Tests for JunitReportPlugin.
 */
class JunitReportPluginTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `generates valid JUnit XML with testsuites root element`() {
        val outputPath = tempDir.resolve("junit.xml")
        val plugin = JunitReportPlugin(outputPath, "TestSuite")

        val report =
            createTestReport(
                ScenarioReportEntry(
                    name = "Test Scenario",
                    status = ResultStatus.PASSED,
                    duration = Duration.ofMillis(123),
                    steps =
                        listOf(
                            StepReportEntry(
                                description = "Step 1",
                                status = ResultStatus.PASSED,
                                duration = Duration.ofMillis(123),
                            ),
                        ),
                    sourceFile = "test.scenario",
                ),
            )

        val content = plugin.formatReport(report)

        assertTrue(content.contains("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(content.contains("""<testsuites name="TestSuite""""))
        assertTrue(content.contains("</testsuites>"))
    }

    @Test
    fun `groups scenarios by source file as testsuites`() {
        val outputPath = tempDir.resolve("junit.xml")
        val plugin = JunitReportPlugin(outputPath, "LemonCheck")

        val report =
            createTestReport(
                ScenarioReportEntry(
                    name = "Scenario A1",
                    status = ResultStatus.PASSED,
                    duration = Duration.ofMillis(100),
                    steps =
                        listOf(
                            StepReportEntry(
                                description = "Step 1",
                                status = ResultStatus.PASSED,
                                duration = Duration.ofMillis(100),
                            ),
                        ),
                    sourceFile = "file-a.scenario",
                ),
                ScenarioReportEntry(
                    name = "Scenario A2",
                    status = ResultStatus.PASSED,
                    duration = Duration.ofMillis(150),
                    steps =
                        listOf(
                            StepReportEntry(
                                description = "Step 1",
                                status = ResultStatus.PASSED,
                                duration = Duration.ofMillis(150),
                            ),
                        ),
                    sourceFile = "file-a.scenario",
                ),
                ScenarioReportEntry(
                    name = "Scenario B1",
                    status = ResultStatus.PASSED,
                    duration = Duration.ofMillis(200),
                    steps =
                        listOf(
                            StepReportEntry(
                                description = "Step 1",
                                status = ResultStatus.PASSED,
                                duration = Duration.ofMillis(200),
                            ),
                        ),
                    sourceFile = "file-b.scenario",
                ),
            )

        val content = plugin.formatReport(report)

        // Should have two testsuites - one per file
        assertTrue(content.contains("""<testsuite name="file-a.scenario""""))
        assertTrue(content.contains("""<testsuite name="file-b.scenario""""))

        // file-a.scenario should have 2 testcases, file-b.scenario should have 1
        assertTrue(content.contains("""<testsuite name="file-a.scenario" tests="2""""))
        assertTrue(content.contains("""<testsuite name="file-b.scenario" tests="1""""))

        // Scenarios should appear as testcases
        assertTrue(content.contains("""<testcase name="Scenario A1""""))
        assertTrue(content.contains("""<testcase name="Scenario A2""""))
        assertTrue(content.contains("""<testcase name="Scenario B1""""))
    }

    @Test
    fun `includes failure details for failed scenarios`() {
        val outputPath = tempDir.resolve("junit.xml")
        val plugin = JunitReportPlugin(outputPath)

        val report =
            createTestReport(
                ScenarioReportEntry(
                    name = "Failing Scenario",
                    status = ResultStatus.FAILED,
                    duration = Duration.ofMillis(500),
                    steps =
                        listOf(
                            StepReportEntry(
                                description = "then status should be 201",
                                status = ResultStatus.FAILED,
                                duration = Duration.ofMillis(100),
                                failure =
                                    io.github.ktakashi.lemoncheck.plugin.AssertionFailure(
                                        message = "Status code mismatch",
                                        expected = 201,
                                        actual = 400,
                                        diff = null,
                                        stepDescription = "assert status 201",
                                        assertionType = "STATUS_CODE",
                                    ),
                            ),
                        ),
                    sourceFile = "failing.scenario",
                ),
            )

        val content = plugin.formatReport(report)

        assertTrue(content.contains("""<testsuite name="failing.scenario" tests="1" failures="1""""))
        assertTrue(content.contains("""<testcase name="Failing Scenario""""))
        assertTrue(content.contains("""<failure message="Status code mismatch""""))
        assertTrue(content.contains("Expected: 201"))
        assertTrue(content.contains("Actual: 400"))
    }

    @Test
    fun `handles scenarios without source file using unknown group`() {
        val outputPath = tempDir.resolve("junit.xml")
        val plugin = JunitReportPlugin(outputPath)

        val report =
            createTestReport(
                ScenarioReportEntry(
                    name = "Legacy Scenario",
                    status = ResultStatus.PASSED,
                    duration = Duration.ofMillis(100),
                    steps =
                        listOf(
                            StepReportEntry(
                                description = "Step 1",
                                status = ResultStatus.PASSED,
                                duration = Duration.ofMillis(100),
                            ),
                        ),
                    sourceFile = null,
                ),
            )

        val content = plugin.formatReport(report)

        // Should group under "unknown" when no sourceFile
        assertTrue(content.contains("""<testsuite name="unknown""""))
        assertTrue(content.contains("""<testcase name="Legacy Scenario""""))
    }

    @Test
    fun `includes skipped and error scenarios correctly`() {
        val outputPath = tempDir.resolve("junit.xml")
        val plugin = JunitReportPlugin(outputPath, "MixedSuite")

        val report =
            createTestReport(
                ScenarioReportEntry(
                    name = "Skipped Scenario",
                    status = ResultStatus.SKIPPED,
                    duration = Duration.ZERO,
                    steps = emptyList(),
                    sourceFile = "mixed.scenario",
                ),
                ScenarioReportEntry(
                    name = "Error Scenario",
                    status = ResultStatus.ERROR,
                    duration = Duration.ofMillis(50),
                    steps =
                        listOf(
                            StepReportEntry(
                                description = "error step",
                                status = ResultStatus.ERROR,
                                duration = Duration.ofMillis(50),
                                failure =
                                    io.github.ktakashi.lemoncheck.plugin.AssertionFailure(
                                        message = "Connection refused",
                                        expected = null,
                                        actual = null,
                                        diff = null,
                                        stepDescription = "call api",
                                        assertionType = "HTTP_ERROR",
                                    ),
                            ),
                        ),
                    sourceFile = "mixed.scenario",
                ),
            )

        val content = plugin.formatReport(report)

        assertTrue(content.contains("""<testsuite name="mixed.scenario" tests="2" failures="0" errors="1" skipped="1""""))
        assertTrue(content.contains("<skipped/>"))
        assertTrue(content.contains("""<error message="Connection refused""""))
    }

    @Test
    fun `escapes XML special characters`() {
        val outputPath = tempDir.resolve("junit.xml")
        val plugin = JunitReportPlugin(outputPath)

        val report =
            createTestReport(
                ScenarioReportEntry(
                    name = "Test <>&\"'",
                    status = ResultStatus.PASSED,
                    duration = Duration.ofMillis(100),
                    steps = emptyList(),
                    sourceFile = "special.scenario",
                ),
            )

        val content = plugin.formatReport(report)

        assertTrue(content.contains("&lt;"))
        assertTrue(content.contains("&gt;"))
        assertTrue(content.contains("&amp;"))
    }

    private fun createTestReport(vararg scenarios: ScenarioReportEntry): TestReport {
        val passed = scenarios.count { it.status == ResultStatus.PASSED }
        val failed = scenarios.count { it.status == ResultStatus.FAILED }
        val skipped = scenarios.count { it.status == ResultStatus.SKIPPED }
        val errors = scenarios.count { it.status == ResultStatus.ERROR }

        return TestReport(
            timestamp = Instant.parse("2026-04-09T10:15:30Z"),
            duration = scenarios.fold(Duration.ZERO) { acc, s -> acc.plus(s.duration) },
            summary =
                TestSummary(
                    total = scenarios.size,
                    passed = passed,
                    failed = failed,
                    skipped = skipped,
                    errors = errors,
                ),
            scenarios = scenarios.toList(),
        )
    }
}
