package io.github.ktakashi.lemoncheck.scenario

/**
 * Token types for the scenario file lexer.
 */
enum class TokenType {
    // Keywords
    FEATURE,
    BACKGROUND,
    SCENARIO,
    OUTLINE,
    FRAGMENT,
    PARAMETERS,
    GIVEN,
    WHEN,
    THEN,
    AND,
    BUT,
    CALL,
    EXTRACT,
    ASSERT,
    EXAMPLES,
    USING,
    INCLUDE,

    // Literals
    STRING,
    NUMBER,
    IDENTIFIER,
    OPERATION_ID,
    JSON_PATH,
    VARIABLE,
    TAG,

    // Symbols
    COLON,
    ARROW,
    EQUALS,
    OPEN_PAREN,
    CLOSE_PAREN,
    OPEN_BRACE,
    CLOSE_BRACE,
    OPEN_BRACKET,
    CLOSE_BRACKET,
    COMMA,
    PIPE,
    DOT,
    TRIPLE_QUOTE,

    // Special
    NEWLINE,
    INDENT,
    DEDENT,
    EOF,

    // Error
    ERROR,
}

/**
 * Represents a token in the scenario file.
 *
 * @property type The type of the token
 * @property value The string value of the token
 * @property location The source location where this token was found
 */
data class Token(
    val type: TokenType,
    val value: String,
    val location: SourceLocation,
)
