package org.berrycrush.assertion

import org.berrycrush.step.CompiledPattern
import org.berrycrush.step.StepMatcher

/**
 * Default implementation of [AssertionRegistry].
 *
 * Maintains a list of registered assertion definitions and finds matches using [StepMatcher].
 * Reuses the pattern matching logic from the step system.
 */
class DefaultAssertionRegistry : AssertionRegistry {
    private val definitions = mutableListOf<RegisteredAssertion>()
    private val matcher = StepMatcher()

    override fun register(definition: AssertionDefinition) {
        val compiled = matcher.compile(definition.pattern)
        definitions.add(RegisteredAssertion(definition, compiled))
    }

    override fun registerAll(definitions: Collection<AssertionDefinition>) {
        definitions.forEach { register(it) }
    }

    override fun findMatch(assertionText: String): AssertionMatch? {
        for (registered in definitions) {
            val parameters = matcher.match(assertionText, registered.compiled)
            if (parameters != null) {
                return AssertionMatch(registered.definition, parameters)
            }
        }
        return null
    }

    override fun allDefinitions(): List<AssertionDefinition> = definitions.map { it.definition }.toList()

    override fun clear() {
        definitions.clear()
    }

    private data class RegisteredAssertion(
        val definition: AssertionDefinition,
        val compiled: CompiledPattern,
    )
}
