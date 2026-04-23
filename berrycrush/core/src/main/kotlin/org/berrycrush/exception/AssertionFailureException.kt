package org.berrycrush.exception

/**
 * Exception thrown when a custom assertion fails.
 *
 * This exception wraps any exception thrown within an assertion block,
 * converting it to a proper assertion failure that can be reported
 * appropriately by test frameworks.
 *
 * @property description The assertion description
 * @property cause The underlying exception that caused the failure
 */
class AssertionFailureException(
    val description: String,
    override val cause: Throwable? = null,
) : AssertionError("Assertion failed: $description${cause?.let { " - ${it.message}" } ?: ""}") {
    constructor(description: String, message: String) : this(
        description = description,
        cause = AssertionError(message),
    )
}

/**
 * Exception thrown when a conditional predicate evaluation fails.
 */
class ConditionalEvaluationException(
    message: String,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)
