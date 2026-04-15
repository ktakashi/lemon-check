package org.berrycrush.scenario

/**
 * Holds the shared state for parsing scenario files.
 *
 * This class is used by extension functions in the `parsing` package
 * to access tokens, track position, and accumulate errors.
 */
class ParserState(
    val tokens: List<Token>,
    val fileName: String? = null,
) {
    /** Current position in the token stream */
    var pos = 0
        internal set

    /** Accumulated parse errors */
    val errors = mutableListOf<ParseError>()

    /**
     * Get the current token, or the last token if at end.
     */
    fun current(): Token = if (isAtEnd()) tokens.last() else tokens[pos]

    /**
     * Get the current source location.
     */
    fun currentLocation(): SourceLocation = current().location

    /**
     * Advance to the next token and return the previous one.
     */
    fun advance(): Token {
        if (!isAtEnd()) pos++
        return tokens[pos - 1]
    }

    /**
     * Check if we've reached the end of input.
     */
    fun isAtEnd(): Boolean = pos >= tokens.size || tokens[pos].type == TokenType.EOF

    /**
     * Expect and consume a specific token type.
     * If the current token doesn't match, adds an error and returns false.
     */
    fun expect(type: TokenType): Boolean {
        if (current().type == type) {
            advance()
            return true
        }
        errors.add(
            ParseError(
                "Unexpected token",
                currentLocation(),
                expected = type.name,
                found = current().type.name,
            ),
        )
        return false
    }

    /**
     * Skip whitespace tokens (but not indent tokens as they're significant).
     */
    fun skipWhitespace() {
        while (!isAtEnd() && (current().type == TokenType.INDENT)) {
            // Don't skip indent tokens - they're significant
            break
        }
    }

    /**
     * Skip newline tokens.
     */
    fun skipNewlines() {
        while (!isAtEnd() && current().type == TokenType.NEWLINE) {
            advance()
        }
    }

    /**
     * Add a parse error.
     */
    fun addError(
        message: String,
        location: SourceLocation = currentLocation(),
        expected: String? = null,
        found: String? = null,
    ) {
        errors.add(ParseError(message, location, expected, found))
    }
}
