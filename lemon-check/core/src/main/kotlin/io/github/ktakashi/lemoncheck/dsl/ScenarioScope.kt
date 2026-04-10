package io.github.ktakashi.lemoncheck.dsl

import io.github.ktakashi.lemoncheck.model.Fragment
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepType

/**
 * DSL scope for defining a scenario.
 */
@LemonCheckDsl
class ScenarioScope internal constructor(
    private val name: String,
    private val tags: Set<String>,
    private val suite: LemonCheckSuite,
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
     * Note: Uses backticks because 'when' is a Kotlin keyword.
     */
    fun `when`(
        description: String,
        block: StepScope.() -> Unit = {},
    ) {
        addStep(StepType.WHEN, description, block)
    }

    /**
     * Define a THEN step (assertion).
     */
    fun then(
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
    fun but(
        description: String,
        block: StepScope.() -> Unit = {},
    ) {
        addStep(StepType.BUT, description, block)
    }

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
