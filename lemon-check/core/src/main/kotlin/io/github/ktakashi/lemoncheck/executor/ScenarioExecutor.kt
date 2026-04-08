package io.github.ktakashi.lemoncheck.executor

import com.jayway.jsonpath.JsonPath
import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.context.ExecutionContext
import io.github.ktakashi.lemoncheck.model.Assertion
import io.github.ktakashi.lemoncheck.model.AssertionResult
import io.github.ktakashi.lemoncheck.model.AssertionType
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.ScenarioResult
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepResult
import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * Executes BDD scenarios against API endpoints.
 */
class ScenarioExecutor(
    private val specRegistry: SpecRegistry,
    private val configuration: Configuration,
) {
    private val httpBuilder = HttpRequestBuilder()
    private val responseHandler = ResponseHandler()

    /**
     * Execute a single scenario.
     */
    fun execute(scenario: Scenario): ScenarioResult {
        val startTime = Instant.now()
        val context = ExecutionContext()
        val stepResults = mutableListOf<StepResult>()
        var overallStatus = ResultStatus.PASSED
        var continueExecution = true

        // Execute background steps first
        for (step in scenario.background) {
            if (!continueExecution) break
            val result = executeStep(step, context)
            stepResults.add(result)
            if (result.status != ResultStatus.PASSED) {
                overallStatus = result.status
                continueExecution = false
            }
        }

        // Execute scenario steps
        for (step in scenario.steps) {
            if (!continueExecution) {
                stepResults.add(
                    StepResult(
                        step = step,
                        status = ResultStatus.SKIPPED,
                    ),
                )
                continue
            }

            val result = executeStep(step, context)
            stepResults.add(result)

            if (result.status != ResultStatus.PASSED) {
                overallStatus = result.status
                continueExecution = false
            }
        }

        val duration = Duration.between(startTime, Instant.now())

        return ScenarioResult(
            scenario = scenario,
            status = overallStatus,
            stepResults = stepResults,
            startTime = startTime,
            duration = duration,
        )
    }

    private fun executeStep(
        step: Step,
        context: ExecutionContext,
    ): StepResult {
        val stepStartTime = Instant.now()

        // If no operation to call, just pass
        if (step.operationId == null) {
            return StepResult(
                step = step,
                status = ResultStatus.PASSED,
                duration = Duration.between(stepStartTime, Instant.now()),
            )
        }

        try {
            // Resolve the operation
            val (spec, resolvedOp) = specRegistry.resolve(step.operationId, step.specName)

            // Build the URL
            val baseUrl = configuration.baseUrl ?: spec.baseUrl
            val url =
                httpBuilder.buildUrl(
                    baseUrl = baseUrl,
                    path = resolvedOp.path,
                    pathParams = resolveParams(step.pathParams, context),
                    queryParams = resolveParams(step.queryParams, context),
                )

            // Merge headers
            val headers = mutableMapOf<String, String>()
            headers.putAll(configuration.defaultHeaders)
            headers.putAll(spec.defaultHeaders)
            headers.putAll(step.headers)

            // Resolve body with variable interpolation
            val body = step.body?.let { context.interpolate(it) }

            // Execute the HTTP request
            val response =
                httpBuilder.execute(
                    method = resolvedOp.method,
                    url = url,
                    headers = headers,
                    body = body,
                )

            // Update context with response
            context.updateLastResponse(response)

            // Extract values
            val extractedValues = extractValues(response, step, context)

            // Run assertions
            val assertionResults = runAssertions(response, step.assertions)

            val allPassed = assertionResults.all { it.passed }
            val stepDuration = Duration.between(stepStartTime, Instant.now())

            return StepResult(
                step = step,
                status = if (allPassed) ResultStatus.PASSED else ResultStatus.FAILED,
                statusCode = response.statusCode(),
                responseBody = response.body(),
                responseHeaders = response.headers().map(),
                duration = stepDuration,
                extractedValues = extractedValues,
                assertionResults = assertionResults,
            )
        } catch (e: Exception) {
            return StepResult(
                step = step,
                status = ResultStatus.ERROR,
                duration = Duration.between(stepStartTime, Instant.now()),
                error = e,
            )
        }
    }

    private fun resolveParams(
        params: Map<String, Any>,
        context: ExecutionContext,
    ): Map<String, Any> =
        params.mapValues { (_, value) ->
            when (value) {
                is String -> context.interpolate(value)
                else -> value
            }
        }

    private fun extractValues(
        response: HttpResponse<String>,
        step: Step,
        context: ExecutionContext,
    ): Map<String, Any?> {
        val extracted = mutableMapOf<String, Any?>()

        for (extraction in step.extractions) {
            try {
                val body = response.body() ?: ""
                val value = JsonPath.read<Any>(body, extraction.jsonPath)
                context[extraction.variableName] = value
                extracted[extraction.variableName] = value
            } catch (_: Exception) {
                // Extraction failed - store null
                extracted[extraction.variableName] = null
            }
        }

        return extracted
    }

    private fun runAssertions(
        response: HttpResponse<String>,
        assertions: List<Assertion>,
    ): List<AssertionResult> = assertions.map { assertion -> runAssertion(response, assertion) }

    private fun runAssertion(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult =
        when (assertion.type) {
            AssertionType.STATUS_CODE -> assertStatusCode(response, assertion)
            AssertionType.BODY_CONTAINS -> assertBodyContains(response, assertion)
            AssertionType.BODY_EQUALS -> assertBodyEquals(response, assertion)
            AssertionType.BODY_MATCHES -> assertBodyMatches(response, assertion)
            AssertionType.BODY_ARRAY_SIZE -> assertBodyArraySize(response, assertion)
            AssertionType.BODY_ARRAY_NOT_EMPTY -> assertBodyArrayNotEmpty(response, assertion)
            AssertionType.HEADER_EXISTS -> assertHeaderExists(response, assertion)
            AssertionType.HEADER_EQUALS -> assertHeaderEquals(response, assertion)
            AssertionType.MATCHES_SCHEMA -> AssertionResult(assertion, true, "Schema validation not implemented yet")
            AssertionType.RESPONSE_TIME -> AssertionResult(assertion, true, "Response time assertion not implemented yet")
        }

    private fun assertStatusCode(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val actual = response.statusCode()
        val expected = assertion.expected

        val passed =
            when (expected) {
                is Int -> actual == expected
                is IntRange -> actual in expected
                else -> false
            }

        return AssertionResult(
            assertion = assertion,
            passed = passed,
            message = if (passed) "Status code is $actual" else "Expected status $expected but got $actual",
            actual = actual,
        )
    }

    private fun assertBodyContains(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val substring = assertion.expected as? String ?: ""
        val passed = body.contains(substring)

        return AssertionResult(
            assertion = assertion,
            passed = passed,
            message = if (passed) "Body contains '$substring'" else "Body does not contain '$substring'",
            actual = body.take(200),
        )
    }

    private fun assertBodyEquals(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"
        val expected = assertion.expected

        return try {
            val actual = JsonPath.read<Any>(body, jsonPath)
            val passed = actual == expected

            AssertionResult(
                assertion = assertion,
                passed = passed,
                message = if (passed) "$jsonPath equals $expected" else "Expected $expected at $jsonPath but got $actual",
                actual = actual,
            )
        } catch (e: Exception) {
            AssertionResult(
                assertion = assertion,
                passed = false,
                message = "Failed to evaluate JSONPath '$jsonPath': ${e.message}",
                actual = null,
            )
        }
    }

    private fun assertBodyMatches(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"
        val pattern = assertion.pattern ?: ""

        return try {
            val actual = JsonPath.read<Any>(body, jsonPath)?.toString() ?: ""
            val regex = Regex(pattern)
            val passed = regex.containsMatchIn(actual)

            AssertionResult(
                assertion = assertion,
                passed = passed,
                message = if (passed) "$jsonPath matches pattern" else "Value at $jsonPath does not match pattern '$pattern'",
                actual = actual,
            )
        } catch (e: Exception) {
            AssertionResult(
                assertion = assertion,
                passed = false,
                message = "Failed to evaluate: ${e.message}",
                actual = null,
            )
        }
    }

    private fun assertBodyArraySize(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"
        val expected = assertion.expected as? Int ?: 0

        return try {
            val array = JsonPath.read<List<*>>(body, jsonPath)
            val actual = array.size
            val passed = actual == expected

            AssertionResult(
                assertion = assertion,
                passed = passed,
                message = if (passed) "Array size is $actual" else "Expected array size $expected but got $actual",
                actual = actual,
            )
        } catch (e: Exception) {
            AssertionResult(
                assertion = assertion,
                passed = false,
                message = "Failed to evaluate array at '$jsonPath': ${e.message}",
                actual = null,
            )
        }
    }

    private fun assertBodyArrayNotEmpty(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val body = response.body() ?: ""
        val jsonPath = assertion.jsonPath ?: "$"

        return try {
            val array = JsonPath.read<List<*>>(body, jsonPath)
            val passed = array.isNotEmpty()

            AssertionResult(
                assertion = assertion,
                passed = passed,
                message = if (passed) "Array is not empty (size: ${array.size})" else "Array at $jsonPath is empty",
                actual = array.size,
            )
        } catch (e: Exception) {
            AssertionResult(
                assertion = assertion,
                passed = false,
                message = "Failed to evaluate array at '$jsonPath': ${e.message}",
                actual = null,
            )
        }
    }

    private fun assertHeaderExists(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val headerName = assertion.headerName ?: ""
        val exists = response.headers().firstValue(headerName).isPresent

        return AssertionResult(
            assertion = assertion,
            passed = exists,
            message = if (exists) "Header '$headerName' exists" else "Header '$headerName' not found",
            actual = exists,
        )
    }

    private fun assertHeaderEquals(
        response: HttpResponse<String>,
        assertion: Assertion,
    ): AssertionResult {
        val headerName = assertion.headerName ?: ""
        val expected = assertion.expected as? String ?: ""
        val actual = response.headers().firstValue(headerName).orElse(null)
        val passed = actual == expected

        val passedMessage = "Header '$headerName' equals '$expected'"
        val failedMessage = "Expected header '$headerName' to be '$expected' but got '$actual'"
        return AssertionResult(
            assertion = assertion,
            passed = passed,
            message = if (passed) passedMessage else failedMessage,
            actual = actual,
        )
    }
}
