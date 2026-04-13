package org.berrycrush.model

/**
 * Represents an assertion to verify on an API response.
 *
 * Assertions use the same `Condition` type as conditionals (`if` statements),
 * ensuring consistent evaluation logic between `assert` and `if` conditions.
 *
 * @property condition The condition that must be true for the assertion to pass
 * @property description Optional description for reporting (derived from the condition)
 */
data class Assertion(
    val condition: Condition,
    val description: String? = null,
)
