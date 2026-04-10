package io.github.ktakashi.lemoncheck.dsl

import io.github.ktakashi.lemoncheck.context.ExecutionContext
import io.github.ktakashi.lemoncheck.model.Assertion
import io.github.ktakashi.lemoncheck.model.AssertionType
import io.github.ktakashi.lemoncheck.model.Extraction
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepType

/**
 * DSL scope for defining a single step.
 */
@LemonCheckDsl
class StepScope internal constructor(
    private val type: StepType,
    private val description: String,
    private val suite: LemonCheckSuite,
) {
    private var operationId: String? = null
    private var specName: String? = null
    private val pathParams = mutableMapOf<String, Any>()
    private val queryParams = mutableMapOf<String, Any>()
    private val headers = mutableMapOf<String, String>()
    private var body: String? = null
    private val extractions = mutableListOf<Extraction>()
    private val assertions = mutableListOf<Assertion>()
    private var autoAssert: Boolean = true

    /**
     * Access to the execution context for variable substitution.
     */
    val context: ExecutionContext
        get() = ExecutionContext() // Placeholder - real context provided at runtime

    /**
     * Switch to a specific OpenAPI spec (for multi-spec scenarios).
     */
    fun using(specName: String) {
        this.specName = specName
    }

    /**
     * Call an API operation.
     */
    fun call(
        operationId: String,
        block: CallScope.() -> Unit = {},
    ) {
        this.operationId = operationId
        val callScope = CallScope()
        block(callScope)
        pathParams.putAll(callScope.pathParams)
        queryParams.putAll(callScope.queryParams)
        headers.putAll(callScope.headers)
        body = callScope.body
        if (!callScope.autoAssert) {
            autoAssert = false
        }
    }

    /**
     * Extract a value from the response.
     */
    fun extractTo(
        variableName: String,
        jsonPath: String,
    ) {
        extractions.add(Extraction(variableName, jsonPath))
    }

    // ========== Assertions ==========

    /**
     * Assert exact status code.
     */
    fun statusCode(expected: Int) {
        assertions.add(
            Assertion(
                type = AssertionType.STATUS_CODE,
                expected = expected,
            ),
        )
    }

    /**
     * Assert status code in range.
     */
    fun statusCode(range: IntRange) {
        assertions.add(
            Assertion(
                type = AssertionType.STATUS_CODE,
                expected = range,
            ),
        )
    }

    /**
     * Assert response body contains a string.
     */
    fun bodyContains(substring: String) {
        assertions.add(
            Assertion(
                type = AssertionType.BODY_CONTAINS,
                expected = substring,
            ),
        )
    }

    /**
     * Assert JSONPath value equals expected.
     */
    fun bodyEquals(
        jsonPath: String,
        expected: Any,
    ) {
        assertions.add(
            Assertion(
                type = AssertionType.BODY_EQUALS,
                jsonPath = jsonPath,
                expected = expected,
            ),
        )
    }

    /**
     * Assert JSONPath value matches regex.
     */
    fun bodyMatches(
        jsonPath: String,
        pattern: String,
    ) {
        assertions.add(
            Assertion(
                type = AssertionType.BODY_MATCHES,
                jsonPath = jsonPath,
                pattern = pattern,
            ),
        )
    }

    /**
     * Assert body array has expected size.
     */
    fun bodyArraySize(
        jsonPath: String,
        expected: Int,
    ) {
        assertions.add(
            Assertion(
                type = AssertionType.BODY_ARRAY_SIZE,
                jsonPath = jsonPath,
                expected = expected,
            ),
        )
    }

    /**
     * Assert body array is not empty.
     */
    fun bodyArrayNotEmpty(jsonPath: String) {
        assertions.add(
            Assertion(
                type = AssertionType.BODY_ARRAY_NOT_EMPTY,
                jsonPath = jsonPath,
            ),
        )
    }

    /**
     * Assert header exists.
     */
    fun headerExists(name: String) {
        assertions.add(
            Assertion(
                type = AssertionType.HEADER_EXISTS,
                headerName = name,
            ),
        )
    }

    /**
     * Assert header equals expected value.
     */
    fun headerEquals(
        name: String,
        expected: String,
    ) {
        assertions.add(
            Assertion(
                type = AssertionType.HEADER_EQUALS,
                headerName = name,
                expected = expected,
            ),
        )
    }

    /**
     * Assert response matches OpenAPI schema.
     */
    fun matchesSchema() {
        assertions.add(
            Assertion(type = AssertionType.MATCHES_SCHEMA),
        )
    }

    /**
     * Assert response time is under threshold (milliseconds).
     */
    fun responseTime(maxMillis: Long) {
        assertions.add(
            Assertion(
                type = AssertionType.RESPONSE_TIME,
                expected = maxMillis,
            ),
        )
    }

    internal fun build(): Step =
        Step(
            type = type,
            description = description,
            operationId = operationId,
            specName = specName,
            pathParams = pathParams.toMap(),
            queryParams = queryParams.toMap(),
            headers = headers.toMap(),
            body = body,
            extractions = extractions.toList(),
            assertions = assertions.toList(),
            autoAssert = autoAssert,
        )
}
