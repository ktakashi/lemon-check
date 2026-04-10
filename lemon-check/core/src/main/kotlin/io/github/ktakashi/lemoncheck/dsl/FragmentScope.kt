package io.github.ktakashi.lemoncheck.dsl

import io.github.ktakashi.lemoncheck.model.Fragment
import io.github.ktakashi.lemoncheck.model.Step

/**
 * DSL scope for defining a reusable fragment.
 */
@LemonCheckDsl
class FragmentScope internal constructor(
    private val name: String,
) {
    internal val steps = mutableListOf<Step>()

    /**
     * Define a GIVEN step.
     */
    fun given(
        description: String,
        block: FragmentStepScope.() -> Unit = {},
    ) {
        addStep(io.github.ktakashi.lemoncheck.model.StepType.GIVEN, description, block)
    }

    /**
     * Define a WHEN step.
     */
    fun `when`(
        description: String,
        block: FragmentStepScope.() -> Unit = {},
    ) {
        addStep(io.github.ktakashi.lemoncheck.model.StepType.WHEN, description, block)
    }

    /**
     * Define a THEN step.
     */
    fun then(
        description: String,
        block: FragmentStepScope.() -> Unit = {},
    ) {
        addStep(io.github.ktakashi.lemoncheck.model.StepType.THEN, description, block)
    }

    /**
     * Define an AND step.
     */
    fun and(
        description: String,
        block: FragmentStepScope.() -> Unit = {},
    ) {
        addStep(io.github.ktakashi.lemoncheck.model.StepType.AND, description, block)
    }

    private fun addStep(
        type: io.github.ktakashi.lemoncheck.model.StepType,
        description: String,
        block: FragmentStepScope.() -> Unit,
    ) {
        val stepScope = FragmentStepScope(type, description)
        block(stepScope)
        steps.add(stepScope.build())
    }

    internal fun build(): Fragment =
        Fragment(
            name = name,
            steps = steps.toList(),
        )
}

/**
 * Simplified step scope for fragments (no suite reference needed).
 */
@LemonCheckDsl
class FragmentStepScope internal constructor(
    private val type: io.github.ktakashi.lemoncheck.model.StepType,
    private val description: String,
) {
    private var operationId: String? = null
    private var specName: String? = null
    private val pathParams = mutableMapOf<String, Any>()
    private val queryParams = mutableMapOf<String, Any>()
    private val headers = mutableMapOf<String, String>()
    private var body: String? = null
    private val extractions = mutableListOf<io.github.ktakashi.lemoncheck.model.Extraction>()
    private val assertions = mutableListOf<io.github.ktakashi.lemoncheck.model.Assertion>()

    fun using(specName: String) {
        this.specName = specName
    }

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
    }

    fun extractTo(
        variableName: String,
        jsonPath: String,
    ) {
        extractions.add(
            io.github.ktakashi.lemoncheck.model
                .Extraction(variableName, jsonPath),
        )
    }

    fun statusCode(expected: Int) {
        assertions.add(
            io.github.ktakashi.lemoncheck.model.Assertion(
                type = io.github.ktakashi.lemoncheck.model.AssertionType.STATUS_CODE,
                expected = expected,
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
            autoAssert = true,
        )
}
