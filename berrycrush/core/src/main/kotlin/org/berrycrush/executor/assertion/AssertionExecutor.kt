package org.berrycrush.executor.assertion

import org.berrycrush.context.MutableTestExecutionContext
import org.berrycrush.context.ValueExtractor
import org.berrycrush.model.Assertion
import org.berrycrush.model.AssertionResult
import org.berrycrush.model.AssertionResults
import org.berrycrush.model.Condition
import org.berrycrush.model.ConditionalActions
import org.berrycrush.model.HttpResponse
import org.berrycrush.model.Step
import org.berrycrush.model.directiveAssertions
import org.berrycrush.plugin.StepContext
import org.berrycrush.plugin.adapter.StepOperationAdapter

class AssertionExecutor(
    private val assertionEngine: AssertionEngine,
) {
    fun runAssertions(
        response: HttpResponse,
        assertions: List<Assertion>,
        context: StepContext,
    ): AssertionResults {
        val allResults = mutableListOf<AssertionResult>()
        val allExtracted = mutableMapOf<String, Any?>()
        var failMessage: String? = null

        for (assertion in assertions) {
            when (assertion) {
                is Assertion.BuiltinAssertion -> {
                    allResults.add(runBuiltinAssertion(response, assertion, context))
                }

                is Assertion.CustomAssertion -> {
                    allResults.add(runCustomAssertion(assertion, context))
                }

                is Assertion.ConditionalAssertion -> {
                    val conditionalResult = runConditional(response, assertion, context)
                    allResults.addAll(conditionalResult.assertionResults)
                    allExtracted.putAll(conditionalResult.extractedValues)
                    if (conditionalResult.failMessage != null) {
                        failMessage = conditionalResult.failMessage
                        break
                    }
                }
            }
        }

        return AssertionResults(
            assertionResults = allResults,
            extractedValues = allExtracted,
            failMessage = failMessage,
        )
    }

    /**
     * Run a single assertion using the AssertionEngine.
     *
     * Delegates condition evaluation and message generation to the assertion engine,
     * ensuring consistent behavior between `assert` and `if` conditions.
     */
    private fun runBuiltinAssertion(
        response: HttpResponse,
        assertion: Assertion.BuiltinAssertion,
        context: StepContext,
    ): AssertionResult {
        val assertionContext = buildAssertionContext(response, context)
        val result = assertionEngine.evaluate(assertion.condition, assertionContext)

        return AssertionResult(
            assertion = assertion,
            passed = result.passed,
            message = result.message,
            actual = result.actual,
        )
    }

    /**
     * Run a single conditional assertion.
     */
    private fun runConditional(
        response: HttpResponse,
        conditional: Assertion.ConditionalAssertion,
        context: StepContext,
    ): AssertionResults {
        val assertionContext = buildAssertionContext(response, context)
        // Try branches first
        for (branch in conditional.branches) {
            if (assertionEngine.evaluate(branch.condition, assertionContext).passed) {
                return runConditionalActions(response, branch.actions, context)
            }
        }

        // Run else branch if present
        return if (conditional.elseActions != null) {
            runConditionalActions(response, conditional.elseActions, context)
        } else {
            // No branch matched - that's OK, no assertions to run
            AssertionResults()
        }
    }

    /**
     * Run actions within a conditional branch.
     */
    private fun runConditionalActions(
        response: HttpResponse,
        actions: ConditionalActions,
        context: StepContext,
    ): AssertionResults {
        val assertionResults = mutableListOf<AssertionResult>()
        val extractedValues = mutableMapOf<String, Any?>()
        var failMessage = actions.failMessage

        if (failMessage == null) {
            // Run extractions
            for (extraction in actions.extractions) {
                val value =
                    runCatching {
                        val body = response.body ?: ""
                        ValueExtractor.extract(body, extraction)
                    }.getOrNull()
                value?.let { context[extraction.variableName] = it }
                extractedValues[extraction.variableName] = value
            }

            // Run assertions
            val assertionEvaluation = runAssertions(response, actions.assertions, context)
            assertionResults.addAll(assertionEvaluation.assertionResults)
            extractedValues.putAll(assertionEvaluation.extractedValues)
            failMessage = assertionEvaluation.failMessage

            // Run nested conditionals
            if (failMessage == null) {
                for (nested in actions.nestedConditionals) {
                    val nestedResult = runConditional(response, nested, context)
                    assertionResults.addAll(nestedResult.assertionResults)
                    extractedValues.putAll(nestedResult.extractedValues)
                    if (nestedResult.failMessage != null) {
                        failMessage = nestedResult.failMessage
                        break
                    }
                }
            }
        }

        return AssertionResults(
            assertionResults = assertionResults,
            extractedValues = extractedValues,
            failMessage = failMessage,
        )
    }

    /**
     * Run a single custom assertion.
     */
    private fun runCustomAssertion(
        customAssertion: Assertion.CustomAssertion,
        context: StepContext,
    ): AssertionResult {
        val testContext = MutableTestExecutionContext(context)
        return runCatching {
            customAssertion.assertion(testContext)
            AssertionResult(
                assertion = customAssertion,
                passed = true,
                message = "Custom assertion passed: ${customAssertion.description}",
            )
        }.getOrElse { e ->
            // Unwrap AssertionFailureException if present
            AssertionResult(
                assertion = customAssertion,
                passed = false,
                message = e.message ?: "Custom assertion failed: ${customAssertion.description}",
                actual = e.message,
            )
        }
    }

    /**
     * Check if a step contains any custom assertions.
     */
    fun hasCustomAssertion(step: Step): Boolean = step.directiveAssertions.any { hasCustomAssertionInTree(it) }

    private fun hasCustomAssertionInTree(assertion: Assertion): Boolean =
        when (assertion) {
            is Assertion.CustomAssertion -> {
                true
            }

            is Assertion.BuiltinAssertion -> {
                assertion.condition is Condition.CustomAssertion
            }

            is Assertion.ConditionalAssertion -> {
                assertion.branches.any { branch ->
                    branch.actions.assertions.any { hasCustomAssertionInTree(it) }
                } ||
                    (assertion.elseActions?.assertions?.any { hasCustomAssertionInTree(it) } == true) ||
                    assertion.branches.any { branch ->
                        branch.actions.nestedConditionals.any { hasCustomAssertionInTree(it) }
                    } ||
                    (assertion.elseActions?.nestedConditionals?.any { hasCustomAssertionInTree(it) } == true)
            }
        }

    private fun buildAssertionContext(
        response: HttpResponse,
        context: StepContext,
    ): AssertionContext =
        AssertionContext(
            response = response,
            responseTime = context.responseTime,
            variables = context.allVariables(),
            stepContext = context,
            currentOperation =
                context.operation?.let {
                    if (it is StepOperationAdapter) {
                        it.operation
                    } else {
                        null
                    }
                },
        )
}
