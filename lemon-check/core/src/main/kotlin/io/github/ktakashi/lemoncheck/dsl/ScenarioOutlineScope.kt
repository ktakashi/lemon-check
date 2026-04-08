package io.github.ktakashi.lemoncheck.dsl

import io.github.ktakashi.lemoncheck.model.ExampleRow
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepType

/**
 * DSL scope for defining a scenario outline (parameterized scenario).
 */
@LemonCheckDsl
class ScenarioOutlineScope internal constructor(
    private val name: String,
    private val tags: Set<String>,
    private val suite: LemonCheckSuite,
) {
    private val stepTemplates = mutableListOf<StepTemplate>()
    private val exampleRows = mutableListOf<ExampleRow>()

    /**
     * Define a GIVEN step template.
     */
    fun given(
        description: String,
        block: StepScope.() -> Unit = {},
    ) {
        addStepTemplate(StepType.GIVEN, description, block)
    }

    /**
     * Define a WHEN step template.
     */
    @Suppress("FunctionName")
    fun `when`(
        description: String,
        block: StepScope.() -> Unit = {},
    ) {
        addStepTemplate(StepType.WHEN, description, block)
    }

    /**
     * Define a THEN step template.
     */
    fun then(
        description: String,
        block: StepScope.() -> Unit = {},
    ) {
        addStepTemplate(StepType.THEN, description, block)
    }

    /**
     * Define an AND step template.
     */
    fun and(
        description: String,
        block: StepScope.() -> Unit = {},
    ) {
        addStepTemplate(StepType.AND, description, block)
    }

    /**
     * Add example rows for parameterization.
     */
    fun examples(vararg rows: ExampleRow) {
        exampleRows.addAll(rows)
    }

    /**
     * Create an example row with named parameters.
     */
    fun row(vararg params: Pair<String, Any>): ExampleRow = ExampleRow(params.toMap())

    private fun addStepTemplate(
        type: StepType,
        description: String,
        block: StepScope.() -> Unit,
    ) {
        stepTemplates.add(StepTemplate(type, description, block))
    }

    internal fun build(): List<Scenario> {
        if (exampleRows.isEmpty()) {
            error("Scenario outline '$name' requires at least one example row")
        }

        return exampleRows.mapIndexed { index, row ->
            val expandedSteps =
                stepTemplates.map { template ->
                    expandStep(template, row)
                }

            Scenario(
                name = "$name (Example ${index + 1})",
                tags = tags,
                steps = expandedSteps,
                background = emptyList(),
            )
        }
    }

    private fun expandStep(
        template: StepTemplate,
        row: ExampleRow,
    ): Step {
        val expandedDescription = substituteParams(template.description, row)
        val stepScope = StepScope(template.type, expandedDescription, suite)
        template.block(stepScope)
        return stepScope.build()
    }

    private fun substituteParams(
        template: String,
        row: ExampleRow,
    ): String {
        var result = template
        row.values.forEach { (key, value) ->
            result = result.replace("<$key>", value.toString())
        }
        return result
    }

    private data class StepTemplate(
        val type: StepType,
        val description: String,
        val block: StepScope.() -> Unit,
    )
}
