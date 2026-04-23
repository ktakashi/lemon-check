package org.berrycrush.dsl

import org.berrycrush.model.Fragment
import org.berrycrush.model.Step

/**
 * DSL scope for defining a reusable fragment.
 */
@BerryCrushDsl
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
        addStep(org.berrycrush.model.StepType.GIVEN, description, block)
    }

    /**
     * Define a WHEN step.
     */
    fun whenever(
        description: String,
        block: FragmentStepScope.() -> Unit = {},
    ) {
        addStep(org.berrycrush.model.StepType.WHEN, description, block)
    }

    /**
     * Define a THEN step.
     */
    fun afterwards(
        description: String,
        block: FragmentStepScope.() -> Unit = {},
    ) {
        addStep(org.berrycrush.model.StepType.THEN, description, block)
    }

    /**
     * Define an AND step.
     */
    fun and(
        description: String,
        block: FragmentStepScope.() -> Unit = {},
    ) {
        addStep(org.berrycrush.model.StepType.AND, description, block)
    }

    // ========== Scenario File Compatibility Aliases ==========

    /**
     * Alias for [whenever] - matches scenario file `when` keyword.
     */
    fun `when`(
        description: String,
        block: FragmentStepScope.() -> Unit = {},
    ) = whenever(description, block)

    /**
     * Alias for [afterwards] - matches scenario file `then` keyword.
     */
    fun then(
        description: String,
        block: FragmentStepScope.() -> Unit = {},
    ) = afterwards(description, block)

    private fun addStep(
        type: org.berrycrush.model.StepType,
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
@BerryCrushDsl
class FragmentStepScope internal constructor(
    private val type: org.berrycrush.model.StepType,
    private val description: String,
) {
    private var operationId: String? = null
    private var specName: String? = null
    private val pathParams = mutableMapOf<String, Any>()
    private val queryParams = mutableMapOf<String, Any>()
    private val headers = mutableMapOf<String, String>()
    private var body: String? = null
    private val extractions = mutableListOf<org.berrycrush.model.Extraction>()
    private val assertions = mutableListOf<org.berrycrush.model.Assertion>()

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
            org.berrycrush.model
                .Extraction(variableName, jsonPath),
        )
    }

    fun statusCode(expected: Int) {
        assertions.add(
            org.berrycrush.model.Assertion(
                condition =
                    org.berrycrush.model.Condition
                        .Status(expected),
                description = "status $expected",
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
