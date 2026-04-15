package org.berrycrush.plugin.adapter

import org.berrycrush.model.Condition
import org.berrycrush.plugin.AssertionFailure
import org.berrycrush.plugin.ResultStatus
import org.berrycrush.plugin.StepResult
import java.time.Duration
import org.berrycrush.model.StepResult as ModelStepResult

/**
 * Adapter that bridges model [ModelStepResult] with plugin [StepResult] interface.
 */
class StepResultAdapter(
    private val modelResult: ModelStepResult,
) : StepResult {
    override val status: ResultStatus
        get() = modelResult.status.mapTo()

    override val duration: Duration
        get() = modelResult.duration

    override val stepDescription: String
        get() = modelResult.step.description

    override val httpStatusCode: Int?
        get() = modelResult.statusCode

    override val responseBody: String?
        get() = modelResult.responseBody

    override val responseHeaders: Map<String, List<String>>
        get() = modelResult.responseHeaders

    override val failure: AssertionFailure?
        get() =
            modelResult.assertionResults
                .firstOrNull { !it.passed }
                ?.let { failedAssertion ->
                    val condition = failedAssertion.assertion.condition
                    AssertionFailure(
                        message = failedAssertion.message,
                        expected = getExpectedFromCondition(condition),
                        actual = failedAssertion.actual,
                        diff = null,
                        stepDescription = modelResult.step.description,
                        assertionType = getConditionTypeName(condition),
                        requestSnapshot = null,
                        responseSnapshot = null,
                    )
                }

    override val error: Throwable?
        get() = modelResult.error

    override val isCustomStep: Boolean
        get() = modelResult.isCustomStep

    /**
     * Extract expected value from a Condition for error reporting.
     */
    private fun getExpectedFromCondition(condition: Condition): Any? =
        when (condition) {
            is Condition.Status -> condition.expected
            is Condition.JsonPath -> condition.expected
            is Condition.Header -> condition.expected
            is Condition.BodyContains -> condition.text
            is Condition.ResponseTime -> condition.maxMs
            is Condition.Variable -> condition.expected
            is Condition.Negated -> getExpectedFromCondition(condition.condition)
            is Condition.Compound -> null
            is Condition.Schema -> "schema"
            is Condition.CustomAssertion -> condition.pattern
        }

    /**
     * Get a human-readable type name for a Condition.
     */
    private fun getConditionTypeName(condition: Condition): String =
        when (condition) {
            is Condition.Status -> "STATUS_CODE"
            is Condition.JsonPath -> "JSON_PATH_${condition.operator.name}"
            is Condition.Header -> "HEADER_${condition.operator.name}"
            is Condition.BodyContains -> "BODY_CONTAINS"
            is Condition.Schema -> "MATCHES_SCHEMA"
            is Condition.ResponseTime -> "RESPONSE_TIME"
            is Condition.Variable -> "VARIABLE"
            is Condition.Negated -> "NOT_${getConditionTypeName(condition.condition)}"
            is Condition.Compound -> "COMPOUND"
            is Condition.CustomAssertion -> "CUSTOM"
        }
}
