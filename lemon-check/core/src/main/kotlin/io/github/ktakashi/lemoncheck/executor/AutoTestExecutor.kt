package io.github.ktakashi.lemoncheck.executor

import io.github.ktakashi.lemoncheck.autotest.AutoTestCase
import io.github.ktakashi.lemoncheck.autotest.AutoTestGenerator
import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.context.ExecutionContext
import io.github.ktakashi.lemoncheck.model.Assertion
import io.github.ktakashi.lemoncheck.model.AssertionResult
import io.github.ktakashi.lemoncheck.model.AutoTestResult
import io.github.ktakashi.lemoncheck.model.BodyProperty
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepResult
import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
import io.github.ktakashi.lemoncheck.scenario.AutoTestType
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * Executes auto-generated invalid and security tests for API endpoints.
 *
 * This class is responsible for:
 * - Generating test cases based on OpenAPI schema constraints
 * - Executing each test case with modified parameters
 * - Setting context variables for conditional assertions
 * - Collecting and reporting test results
 *
 * Auto-tests are generated for:
 * - **Invalid tests**: Violate schema constraints (minLength, maxLength, pattern, required, enum, type)
 * - **Security tests**: Inject common attack payloads (SQL injection, XSS, path traversal, etc.)
 *
 * @property specRegistry Registry for OpenAPI specifications
 * @property configuration Execution configuration
 * @property httpBuilder HTTP request builder for executing requests
 * @property assertionRunner Function to run assertions against responses
 */
class AutoTestExecutor(
    private val specRegistry: SpecRegistry,
    private val configuration: Configuration,
    private val httpBuilder: HttpRequestBuilder,
    private val assertionRunner: (HttpResponse<String>, List<Assertion>, ExecutionContext) -> List<AssertionResult>,
    private val paramResolver: (Map<String, Any>, ExecutionContext) -> Map<String, Any>,
    private val requestLogger: (String, String, Map<String, String>, String?) -> Unit,
    private val responseLogger: (String, String, HttpResponse<String>, Long) -> Unit,
) {
    private val objectMapper = ObjectMapper()

    /**
     * Execute auto-generated tests for a step with autoTestConfig.
     *
     * This generates invalid and/or security test cases based on the OpenAPI schema
     * and executes each one, setting context variables for conditional assertions.
     *
     * @param step The step with auto-test configuration
     * @param context The execution context for variable interpolation
     * @param stepStartTime When the step started (for duration calculation)
     * @param listener Listener for execution events
     * @return StepResult containing all auto-test results
     */
    fun executeAutoTests(
        step: Step,
        context: ExecutionContext,
        stepStartTime: Instant,
        listener: ExecutionListener = ExecutionListener.NOOP,
    ): StepResult {
        val autoTestConfig = step.autoTestConfig!!
        val operationId = step.operationId!!

        // Resolve the operation to get the OpenAPI spec
        val (spec, _) = specRegistry.resolve(operationId, step.specName)

        // Create the auto-test generator
        val generator = AutoTestGenerator.fromSpec(spec)

        // Extract base body from step if present
        val baseBody = extractBaseBody(step, context)

        // Extract base path params from step
        val basePathParams =
            step.pathParams.mapValues { (_, v) ->
                when (v) {
                    is String -> context.interpolate(v)
                    else -> v
                }
            }

        // Extract base headers from step
        val baseHeaders = step.headers.mapValues { (_, v) -> context.interpolate(v) }

        // Generate test cases
        val allTestCases =
            generator.generateTestCases(
                operationId = operationId,
                testTypes = autoTestConfig.types,
                baseBody = baseBody,
                basePathParams = basePathParams,
                baseHeaders = baseHeaders,
            )

        // Filter out excluded tests
        val testCases = filterExcludedTests(allTestCases, autoTestConfig.excludes)

        if (testCases.isEmpty()) {
            // No test cases generated - just pass
            return StepResult(
                step = step,
                status = ResultStatus.PASSED,
                duration = Duration.between(stepStartTime, Instant.now()),
                message = "No auto-test cases generated (operation may not have parameters or constraints)",
            )
        }

        // Execute each test case and collect results
        val allResults = mutableListOf<AutoTestResult>()

        for (testCase in testCases) {
            // Notify listener that test is starting
            listener.onAutoTestStarting(testCase)

            val testResult = executeAutoTestCase(step, testCase, context)
            allResults.add(testResult)

            // Notify listener that test finished
            listener.onAutoTestCompleted(testCase, testResult)

            // Log the test case execution
            logAutoTestCase(testCase, testResult)
        }

        // Aggregate results
        val failedCount = allResults.count { !it.passed }
        val totalCount = allResults.size

        return StepResult(
            step = step,
            status = if (failedCount == 0) ResultStatus.PASSED else ResultStatus.FAILED,
            duration = Duration.between(stepStartTime, Instant.now()),
            message = "Auto-tests: $totalCount executed, $failedCount failed",
            autoTestResults = allResults,
        )
    }

    /**
     * Extract base body map from step for auto-test generation.
     *
     * @param step The step containing the body
     * @param context Execution context for variable interpolation
     * @return Map representation of the body, or null if no body specified
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractBaseBody(
        step: Step,
        context: ExecutionContext,
    ): Map<String, Any>? {
        // From inline body JSON
        step.body?.let { bodyStr ->
            val interpolated = context.interpolate(bodyStr)
            return try {
                objectMapper.readValue(interpolated, Map::class.java) as Map<String, Any>
            } catch (_: Exception) {
                null
            }
        }

        // From structured body properties
        step.bodyProperties?.let { props ->
            return flattenBodyProperties(props, context)
        }

        return null
    }

    /**
     * Flatten body properties to a simple map for auto-test base body.
     *
     * @param props The body properties to flatten
     * @param context Execution context for variable interpolation
     * @return Flattened map representation
     */
    private fun flattenBodyProperties(
        props: Map<String, BodyProperty>,
        context: ExecutionContext,
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        for ((key, value) in props) {
            when (value) {
                is BodyProperty.Simple -> {
                    val resolved =
                        when (val v = value.value) {
                            is String -> context.interpolate(v)
                            else -> v
                        }
                    result[key] = resolved
                }
                is BodyProperty.Nested -> {
                    result[key] = flattenBodyProperties(value.properties, context)
                }
            }
        }
        return result
    }

    /**
     * Execute a single auto-test case.
     *
     * Sets up context variables for conditional assertions, builds the request with
     * modified parameters, executes the request, and runs assertions.
     *
     * @param step The original step
     * @param testCase The test case to execute
     * @param context Execution context
     * @return AutoTestResult with pass/fail status and details
     */
    private fun executeAutoTestCase(
        step: Step,
        testCase: AutoTestCase,
        context: ExecutionContext,
    ): AutoTestResult {
        // Set context variables for conditional assertions
        context["test.type"] = testCase.type.name.lowercase()
        context["test.field"] = testCase.fieldName
        context["test.description"] = testCase.description
        context["test.value"] = testCase.invalidValue?.toString() ?: "null"
        context["test.location"] = testCase.location.name.lowercase()

        val testStartTime = Instant.now()

        return try {
            // Determine body, path params, and headers for this test case
            val testBody =
                if (testCase.body.isNotEmpty()) {
                    objectMapper.writeValueAsString(testCase.body)
                } else {
                    step.body
                }

            // Merge test case path params with step's path params
            val testPathParams = step.pathParams.toMutableMap()
            testCase.pathParams.forEach { (k, v) -> testPathParams[k] = v ?: "" }

            // Merge test case headers with step's headers
            val testHeaders = step.headers.toMutableMap()
            testCase.headers.forEach { (k, v) -> testHeaders[k] = v }

            // Execute the HTTP request
            val (spec, resolvedOp) = specRegistry.resolve(step.operationId!!, step.specName)
            val baseUrl = configuration.baseUrl ?: spec.baseUrl
            val url =
                httpBuilder.buildUrl(
                    baseUrl = baseUrl,
                    path = resolvedOp.path,
                    pathParams = paramResolver(testPathParams, context),
                    queryParams = paramResolver(step.queryParams, context),
                )

            val headers = configuration.defaultHeaders + spec.defaultHeaders + testHeaders

            // Log request if enabled
            requestLogger(resolvedOp.method.name, url, headers, testBody)

            val requestStartTime = System.currentTimeMillis()
            val response =
                httpBuilder.execute(
                    method = resolvedOp.method,
                    url = url,
                    headers = headers,
                    body = testBody,
                )

            // Log response if enabled
            responseLogger(resolvedOp.method.name, url, response, requestStartTime)

            // Update context with response for conditional assertions
            context.updateLastResponse(response)

            // Run assertions (which may include conditionals checking test.type)
            val assertionResults = assertionRunner(response, step.assertions, context)
            val allPassed = assertionResults.all { it.passed }

            AutoTestResult(
                testCase = testCase,
                passed = allPassed,
                statusCode = response.statusCode(),
                responseBody = response.body()?.take(500),
                assertionResults = assertionResults,
                duration = Duration.between(testStartTime, Instant.now()),
            )
        } catch (e: Exception) {
            // For security tests, an exception (e.g., invalid URL) means the attack was blocked
            // at the infrastructure level, which is a good thing
            val isSecurityTest = testCase.type == AutoTestType.SECURITY
            val isUrlError =
                e.message?.contains("Illegal character") == true ||
                    e.message?.contains("Invalid URL") == true

            AutoTestResult(
                testCase = testCase,
                passed = isSecurityTest && isUrlError, // Security test blocked at URL level = pass
                error = e.message ?: e.javaClass.simpleName,
                duration = Duration.between(testStartTime, Instant.now()),
            )
        }
    }

    /**
     * Log auto-test case execution details.
     *
     * @param testCase The test case that was executed
     * @param result The result of the test execution
     */
    private fun logAutoTestCase(
        testCase: AutoTestCase,
        result: AutoTestResult,
    ) {
        if (configuration.logRequests) {
            val status = if (result.passed) "PASS" else "FAIL"
            val message =
                buildString {
                    append("  [AUTO-TEST] [$status] ")
                    append("[${testCase.tag}] ")
                    append("${testCase.description} ")
                    append("(field=${testCase.fieldName}, status=${result.statusCode ?: "N/A"})")
                }
            println(message)
        }
    }

    /**
     * Filter out test cases that match any of the exclude patterns.
     *
     * Excludes can match:
     * - Security test categories (e.g., "SQLInjection", "XSS", "PathTraversal")
     * - Invalid test types (e.g., "minLength", "maxLength", "required", "pattern", "enum", "type")
     * - Test description keywords (case-insensitive partial match)
     *
     * @param testCases The generated test cases
     * @param excludes Set of exclude patterns
     * @return Filtered list of test cases
     */
    private fun filterExcludedTests(
        testCases: List<AutoTestCase>,
        excludes: Set<String>,
    ): List<AutoTestCase> {
        if (excludes.isEmpty()) {
            return testCases
        }

        // Normalize exclude patterns for case-insensitive matching
        val normalizedExcludes = excludes.map { it.lowercase().replace(" ", "") }

        return testCases.filter { testCase ->
            val description = testCase.description.lowercase().replace(" ", "")
            val tag = testCase.tag.lowercase().replace(" ", "")

            // Check if any exclude pattern matches
            !normalizedExcludes.any { exclude ->
                description.contains(exclude) ||
                    tag.contains(exclude) ||
                    // Also match common category name formats
                    matchesCategoryPattern(description, exclude)
            }
        }
    }

    /**
     * Check if a description matches a category pattern.
     *
     * Supports various naming conventions:
     * - "SQLInjection" matches "sql injection", "SQL Injection", "SQL_Injection"
     * - "maxLength" matches "maxlength", "max_length", "max length", "Maximum length"
     */
    private fun matchesCategoryPattern(
        description: String,
        pattern: String,
    ): Boolean {
        // Map common pattern aliases
        val patternAliases =
            mapOf(
                "sqlinjection" to listOf("sql", "injection", "union", "select", "drop"),
                "xss" to listOf("script", "alert", "onerror", "javascript"),
                "pathtraversal" to listOf("path", "traversal", "../", "..\\"),
                "commandinjection" to listOf("command", "injection", "exec", "system"),
                "ldapinjection" to listOf("ldap", "filter"),
                "xxe" to listOf("xxe", "entity", "doctype"),
                "xmlinjection" to listOf("xml", "cdata"),
                "headerinjection" to listOf("header", "crlf", "injection"),
                "minlength" to listOf("minimum", "minlength", "too short", "below minimum"),
                "maxlength" to listOf("maximum", "maxlength", "too long", "exceeds maximum"),
                "required" to listOf("required", "missing"),
                "pattern" to listOf("pattern", "format", "invalid format"),
                "enum" to listOf("enum", "invalid value", "not in allowed"),
                "type" to listOf("type", "invalid type", "wrong type"),
            )

        val aliases = patternAliases[pattern] ?: return false
        return aliases.any { alias -> description.contains(alias) }
    }
}
