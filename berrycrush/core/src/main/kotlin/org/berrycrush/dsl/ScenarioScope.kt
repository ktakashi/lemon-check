package org.berrycrush.dsl

import org.berrycrush.model.Fragment
import org.berrycrush.model.Scenario
import org.berrycrush.model.Step
import org.berrycrush.model.StepType

/**
 * DSL scope for defining a scenario.
 */
@BerryCrushDsl
class ScenarioScope internal constructor(
    private val name: String,
    private val tags: Set<String>,
    private val suite: BerryCrushSuite,
) {
    internal val steps = mutableListOf<Step>()
    internal val backgroundSteps = mutableListOf<Step>()

    /**
     * Define a GIVEN step (precondition).
     */
    fun given(
        description: String,
        block: StepScope.() -> Unit = {},
    ) {
        addStep(StepType.GIVEN, description, block)
    }

    /**
     * Define a WHEN step (action).
     */
    fun whenever(
        description: String,
        block: StepScope.() -> Unit = {},
    ) {
        addStep(StepType.WHEN, description, block)
    }

    /**
     * Define a THEN step (assertion).
     */
    fun afterwards(
        description: String,
        block: StepScope.() -> Unit = {},
    ) {
        addStep(StepType.THEN, description, block)
    }

    /**
     * Define an AND step (continuation of previous).
     */
    fun and(
        description: String,
        block: StepScope.() -> Unit = {},
    ) {
        addStep(StepType.AND, description, block)
    }

    /**
     * Define a BUT step (exception/negative case).
     */
    fun otherwise(
        description: String,
        block: StepScope.() -> Unit = {},
    ) {
        addStep(StepType.BUT, description, block)
    }

    // ========== Scenario File Compatibility Aliases ==========

    /**
     * Alias for [whenever] - matches scenario file `when` keyword.
     */
    fun `when`(
        description: String,
        block: StepScope.() -> Unit = {},
    ) = whenever(description, block)

    /**
     * Alias for [afterwards] - matches scenario file `then` keyword.
     */
    fun then(
        description: String,
        block: StepScope.() -> Unit = {},
    ) = afterwards(description, block)

    /**
     * Alias for [otherwise] - matches scenario file `but` keyword.
     */
    fun but(
        description: String,
        block: StepScope.() -> Unit = {},
    ) = otherwise(description, block)

    /**
     * Include a fragment's steps in this scenario.
     */
    fun include(fragment: Fragment) {
        steps.addAll(fragment.steps)
    }

    /**
     * Include a fragment by name.
     */
    fun include(fragmentName: String) {
        val fragment =
            suite.getFragment(fragmentName)
                ?: error("Fragment '$fragmentName' not found. Register it first with suite.fragment()")
        include(fragment)
    }

    private fun addStep(
        type: StepType,
        description: String,
        block: StepScope.() -> Unit,
    ) {
        val stepScope = StepScope(type, description, suite)
        block(stepScope)
        steps.add(stepScope.build())
    }

    internal fun build(): Scenario =
        Scenario(
            name = name,
            tags = tags,
            steps = steps.toList(),
            background = backgroundSteps.toList(),
        )
}
