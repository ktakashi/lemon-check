package org.berrycrush.dsl

import org.berrycrush.context.TestExecutionContext
import org.berrycrush.model.Condition
import org.berrycrush.model.ConditionBranch
import org.berrycrush.model.ConditionalActions
import org.berrycrush.model.ConditionalAssertion
import org.berrycrush.model.StepType

/**
 * Builder for conditional execution blocks in the DSL.
 *
 * This builder allows creating if-else style conditional logic:
 * ```kotlin
 * conditional({ ctx -> ctx.statusCode == 200 }) {
 *     assert("success case") { /* ... */ }
 * } orElse {
 *     assert("error case") { /* ... */ }
 * }
 * ```
 */
@BerryCrushDsl
class ConditionalBuilder internal constructor(
    private val predicate: (TestExecutionContext) -> Boolean,
    private val thenBlock: StepScope.() -> Unit,
    private val parentScope: StepScope,
) {
    private var elseBlock: (StepScope.() -> Unit)? = null

    /**
     * Define the else branch for when the predicate returns false.
     */
    infix fun orElse(block: StepScope.() -> Unit): ConditionalBuilder {
        this.elseBlock = block
        return this
    }

    /**
     * Build the conditional assertion for integration with Step.
     */
    internal fun build(): ConditionalAssertion {
        // Create a then branch step scope to capture assertions (using internal constructor)
        val thenStepScope = createChildScope("conditional then")
        thenBlock(thenStepScope)
        val thenStep = thenStepScope.build()

        // Create an else branch if present
        val elseActions =
            elseBlock?.let { block ->
                val elseStepScope = createChildScope("conditional else")
                block(elseStepScope)
                val elseStep = elseStepScope.build()
                ConditionalActions(
                    assertions = elseStep.assertions,
                    extractions = elseStep.extractions,
                )
            }

        return ConditionalAssertion(
            ifBranch =
                ConditionBranch(
                    condition = Condition.Custom(predicate),
                    actions =
                        ConditionalActions(
                            assertions = thenStep.assertions,
                            extractions = thenStep.extractions,
                        ),
                ),
            elseActions = elseActions,
        )
    }

    private fun createChildScope(description: String): StepScope {
        // Access parent scope's internal state via reflection to create child scope
        // This is a workaround since we can't directly access private fields
        return StepScope(
            type = StepType.THEN, // Default type for conditional branches
            description = description,
            suite = getSuite(),
            internal = true,
        )
    }

    private fun getSuite(): BerryCrushSuite {
        // Use reflection to get the suite from parent scope
        val suiteField = StepScope::class.java.getDeclaredField("suite")
        suiteField.isAccessible = true
        return suiteField.get(parentScope) as BerryCrushSuite
    }
}
