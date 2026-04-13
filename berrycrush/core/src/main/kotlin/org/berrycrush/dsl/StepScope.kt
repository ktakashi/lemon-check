package org.berrycrush.dsl

import org.berrycrush.context.ExecutionContext
import org.berrycrush.model.Assertion
import org.berrycrush.model.Condition
import org.berrycrush.model.ConditionOperator
import org.berrycrush.model.Extraction
import org.berrycrush.model.Step
import org.berrycrush.model.StepType

/**
 * DSL scope for defining a single step.
 */
@BerryCrushDsl
class StepScope internal constructor(
    private val type: StepType,
    private val description: String,
    private val suite: BerryCrushSuite,
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
                condition = Condition.Status(expected),
                description = "status $expected",
            ),
        )
    }

    /**
     * Assert status code in range.
     */
    fun statusCode(range: IntRange) {
        assertions.add(
            Assertion(
                condition = Condition.Status(range),
                description = "status ${range.first}-${range.last}",
            ),
        )
    }

    /**
     * Assert response body contains a string.
     */
    fun bodyContains(substring: String) {
        assertions.add(
            Assertion(
                condition = Condition.BodyContains(substring),
                description = "body contains \"$substring\"",
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
                condition =
                    Condition.JsonPath(
                        path = jsonPath,
                        operator = ConditionOperator.EQUALS,
                        expected = expected,
                    ),
                description = "$jsonPath equals $expected",
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
                condition =
                    Condition.JsonPath(
                        path = jsonPath,
                        operator = ConditionOperator.MATCHES,
                        expected = pattern,
                    ),
                description = "$jsonPath matches $pattern",
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
                condition =
                    Condition.JsonPath(
                        path = jsonPath,
                        operator = ConditionOperator.HAS_SIZE,
                        expected = expected,
                    ),
                description = "$jsonPath hasSize $expected",
            ),
        )
    }

    /**
     * Assert body array is not empty.
     */
    fun bodyArrayNotEmpty(jsonPath: String) {
        assertions.add(
            Assertion(
                condition =
                    Condition.JsonPath(
                        path = jsonPath,
                        operator = ConditionOperator.NOT_EMPTY,
                        expected = null,
                    ),
                description = "$jsonPath notEmpty",
            ),
        )
    }

    /**
     * Assert header exists.
     */
    fun headerExists(name: String) {
        assertions.add(
            Assertion(
                condition =
                    Condition.Header(
                        name = name,
                        operator = ConditionOperator.EXISTS,
                        expected = null,
                    ),
                description = "header $name exists",
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
                condition =
                    Condition.Header(
                        name = name,
                        operator = ConditionOperator.EQUALS,
                        expected = expected,
                    ),
                description = "header $name equals \"$expected\"",
            ),
        )
    }

    /**
     * Assert response matches OpenAPI schema.
     */
    fun matchesSchema() {
        assertions.add(
            Assertion(
                condition = Condition.Schema,
                description = "matches schema",
            ),
        )
    }

    /**
     * Assert response time is under threshold (milliseconds).
     */
    fun responseTime(maxMillis: Long) {
        assertions.add(
            Assertion(
                condition = Condition.ResponseTime(maxMillis),
                description = "responseTime < $maxMillis ms",
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
