package org.berrycrush.assertion

import java.lang.reflect.Method

/**
 * Registry for custom assertion definitions.
 *
 * Manages registration and lookup of assertion definitions based on patterns.
 */
interface AssertionRegistry {
    /**
     * Registers an assertion definition.
     *
     * @param definition The assertion definition to register
     */
    fun register(definition: AssertionDefinition)

    /**
     * Registers multiple assertion definitions.
     *
     * @param definitions The assertion definitions to register
     */
    fun registerAll(definitions: Collection<AssertionDefinition>)

    /**
     * Finds a matching assertion definition for the given assertion text.
     *
     * @param assertionText The assertion text to match
     * @return The matching assertion definition with extracted parameters, or null if no match
     */
    fun findMatch(assertionText: String): AssertionMatch?

    /**
     * Returns all registered assertion definitions.
     *
     * @return Immutable list of all registered definitions
     */
    fun allDefinitions(): List<AssertionDefinition>

    /**
     * Clears all registered assertion definitions.
     */
    fun clear()
}

/**
 * A registered assertion definition.
 *
 * @property pattern The original pattern string with placeholders
 * @property method The method to invoke when the assertion matches
 * @property instance The instance to invoke the method on (null for static)
 * @property description Optional description of the assertion
 */
data class AssertionDefinition(
    val pattern: String,
    val method: Method,
    val instance: Any?,
    val description: String = "",
)

/**
 * Result of matching an assertion text against registered definitions.
 *
 * @property definition The matched assertion definition
 * @property parameters Extracted parameter values from the assertion text
 */
data class AssertionMatch(
    val definition: AssertionDefinition,
    val parameters: List<Any?>,
)
