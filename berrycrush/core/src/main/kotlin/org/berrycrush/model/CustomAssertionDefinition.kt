package org.berrycrush.model

import org.berrycrush.context.TestExecutionContext

/**
 * Represents a custom assertion definition for execution.
 *
 * Custom assertions allow users to define arbitrary assertion logic
 * with access to the full test execution context.
 */
data class CustomAssertionDefinition(
    /**
     * Human-readable description of what this assertion checks.
     */
    val description: String,
    /**
     * The assertion callback that performs the actual check.
     * Any exception thrown (including from require/check/assert) is treated as assertion failure.
     */
    val assertion: (TestExecutionContext) -> Unit,
)
