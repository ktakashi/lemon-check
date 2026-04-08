package io.github.ktakashi.lemoncheck.scenario

/**
 * Represents a parse error encountered during scenario file parsing.
 *
 * @property message Description of the error
 * @property location Source location where the error occurred
 * @property expected What was expected at this location (if applicable)
 * @property found What was actually found (if applicable)
 */
data class ParseError(
    val message: String,
    val location: SourceLocation,
    val expected: String? = null,
    val found: String? = null,
) {
    override fun toString(): String {
        val details =
            buildString {
                if (expected != null) append(", expected: $expected")
                if (found != null) append(", found: $found")
            }
        return "Parse error at $location: $message$details"
    }
}
