package org.berrycrush.scenario

/**
 * Lexer for scenario files.
 *
 * Tokenizes `.scenario` files into a stream of tokens for parsing.
 * Supports:
 * - Keywords: scenario, outline, fragment, given, when, then, and, call, extract, assert, examples
 * - Identifiers and operation IDs
 * - JSON paths and variables
 * - String and number literals
 * - Symbols and operators
 * - Significant whitespace (indentation)
 */
class Lexer(
    private val source: String,
    private val fileName: String? = null,
) {
    private var pos = 0
    private var line = 1
    private var column = 1
    private var indentStack = mutableListOf(0)
    private var atLineStart = true
    private val pendingTokens = mutableListOf<Token>()

    companion object {
        private val KEYWORDS =
            mapOf(
                "feature" to TokenType.FEATURE,
                "background" to TokenType.BACKGROUND,
                "scenario" to TokenType.SCENARIO,
                "outline" to TokenType.OUTLINE,
                "fragment" to TokenType.FRAGMENT,
                "parameters" to TokenType.PARAMETERS,
                "given" to TokenType.GIVEN,
                "when" to TokenType.WHEN,
                "then" to TokenType.THEN,
                "and" to TokenType.AND,
                "but" to TokenType.BUT,
                "call" to TokenType.CALL,
                "extract" to TokenType.EXTRACT,
                "assert" to TokenType.ASSERT,
                "examples" to TokenType.EXAMPLES,
                "using" to TokenType.USING,
                "include" to TokenType.INCLUDE,
                "if" to TokenType.IF,
                "else" to TokenType.ELSE,
                "fail" to TokenType.FAIL,
            )
    }

    /**
     * Tokenize the entire source into a list of tokens.
     */
    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (true) {
            val token = nextToken()
            tokens.add(token)
            if (token.type == TokenType.EOF) break
        }
        return tokens
    }

    /**
     * Get the next token from the source.
     */
    fun nextToken(): Token {
        // Return any pending tokens first (from indent/dedent handling)
        if (pendingTokens.isNotEmpty()) {
            return pendingTokens.removeAt(0)
        }

        // Handle indentation at line start
        if (atLineStart) {
            val indentToken = handleIndentation()
            if (indentToken != null) {
                return indentToken
            }
        }

        // Skip whitespace (but not newlines)
        skipWhitespace()

        if (isAtEnd()) {
            // Handle any remaining dedents
            while (indentStack.size > 1) {
                indentStack.removeLast()
                pendingTokens.add(Token(TokenType.DEDENT, "", currentLocation()))
            }
            if (pendingTokens.isNotEmpty()) {
                return pendingTokens.removeAt(0)
            }
            return Token(TokenType.EOF, "", currentLocation())
        }

        val c = peek()

        // Handle newlines
        if (c == '\n' || c == '\r') {
            return scanNewline()
        }

        // Handle comments
        if (c == '#') {
            skipComment()
            return nextToken()
        }

        // Handle triple-quoted strings first (""")
        if (c == '"' && peekAhead(1) == '"' && peekAhead(2) == '"') {
            return scanTripleQuote()
        }

        // Handle strings
        if (c == '"' || c == '\'') {
            return scanString()
        }

        // Handle JSON path
        if (c == '$') {
            return scanJsonPath()
        }

        // Handle tags (@tagname)
        if (c == '@') {
            return scanTag()
        }

        // Handle variable reference
        if (c == '{' && peekAhead(1) == '{') {
            return scanVariable()
        }

        // Handle numbers
        if (c.isDigit() || (c == '-' && peekAhead(1)?.isDigit() == true)) {
            return scanNumber()
        }

        // Handle operation ID prefix (^operationId)
        if (c == '^') {
            return scanOperationId()
        }

        // Handle identifiers and keywords
        if (c.isLetter() || c == '_') {
            return scanIdentifier()
        }

        // Handle symbols
        return scanSymbol()
    }

    private fun handleIndentation(): Token? {
        atLineStart = false

        // Skip blank lines
        while (!isAtEnd() && (peek() == '\n' || peek() == '\r')) {
            advance()
            if (!isAtEnd() && peek(-1) == '\r' && peek() == '\n') {
                advance()
            }
            line++
            column = 1
        }

        if (isAtEnd()) return null

        // Skip comment-only lines
        if (peek() == '#') {
            skipComment()
            return handleIndentation()
        }

        // Count indentation
        var indent = 0
        while (!isAtEnd() && peek() == ' ') {
            indent++
            advance()
        }

        // Skip lines that are only whitespace
        if (!isAtEnd() && (peek() == '\n' || peek() == '\r' || peek() == '#')) {
            if (peek() == '#') skipComment()
            return handleIndentation()
        }

        val currentIndent = indentStack.last()

        return when {
            indent > currentIndent -> {
                indentStack.add(indent)
                Token(TokenType.INDENT, " ".repeat(indent), currentLocation())
            }
            indent < currentIndent -> {
                while (indentStack.size > 1 && indentStack.last() > indent) {
                    indentStack.removeLast()
                    pendingTokens.add(Token(TokenType.DEDENT, "", currentLocation()))
                }
                if (pendingTokens.isNotEmpty()) {
                    pendingTokens.removeAt(0)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun scanNewline(): Token {
        val loc = currentLocation()
        advance()
        if (!isAtEnd() && peek(-1) == '\r' && peek() == '\n') {
            advance()
        }
        line++
        column = 1
        atLineStart = true
        return Token(TokenType.NEWLINE, "\\n", loc)
    }

    private fun scanString(): Token {
        val loc = currentLocation()
        val quote = advance()
        val sb = StringBuilder()

        while (!isAtEnd() && peek() != quote) {
            if (peek() == '\\' && !isAtEnd(1)) {
                advance() // skip backslash
                when (val escaped = advance()) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\'' -> sb.append('\'')
                    '\\' -> sb.append('\\')
                    else -> sb.append(escaped)
                }
            } else if (peek() == '\n') {
                break // Unterminated string
            } else {
                sb.append(advance())
            }
        }

        if (!isAtEnd() && peek() == quote) {
            advance() // consume closing quote
        } else {
            return Token(TokenType.ERROR, "Unterminated string", loc)
        }

        return Token(TokenType.STRING, sb.toString(), loc)
    }

    /**
     * Scan a triple-quoted string.
     * Captures all content between """ and closing """, preserving whitespace and newlines.
     */
    private fun scanTripleQuote(): Token {
        val loc = currentLocation()
        // Consume opening """
        advance() // first "
        advance() // second "
        advance() // third "

        // Skip the newline after opening """ if present
        if (!isAtEnd() && peek() == '\n') {
            advance()
            line++
            column = 1
        } else if (!isAtEnd() && peek() == '\r') {
            advance()
            if (!isAtEnd() && peek() == '\n') {
                advance()
            }
            line++
            column = 1
        }

        val sb = StringBuilder()

        // Capture content until closing """
        while (!isAtEnd()) {
            // Check for closing """
            if (peek() == '"' && peekAhead(1) == '"' && peekAhead(2) == '"') {
                break
            }

            when (val c = advance()) {
                '\n' -> {
                    sb.append(c)
                    line++
                    column = 1
                }
                '\r' -> {
                    if (!isAtEnd() && peek() == '\n') {
                        advance()
                    }
                    sb.append('\n')
                    line++
                    column = 1
                }
                else -> {
                    sb.append(c)
                }
            }
        }

        // Consume closing """
        if (!isAtEnd() && peek() == '"') {
            advance() // first "
            advance() // second "
            advance() // third "
        }

        // Skip to end of line after closing """
        while (!isAtEnd() && peek() != '\n' && peek() != '\r') {
            advance()
        }

        // Mark that we should process indentation on the next token
        if (!isAtEnd() && (peek() == '\n' || peek() == '\r')) {
            // Consume the newline
            advance()
            if (!isAtEnd() && peek(-1) == '\r' && peek() == '\n') {
                advance()
            }
            line++
            column = 1
            atLineStart = true
        }

        // Remove common indentation
        val content = removeCommonIndentation(sb.toString())

        return Token(TokenType.STRING, content, loc)
    }

    /**
     * Remove common leading whitespace from all lines in a multi-line string.
     */
    private fun removeCommonIndentation(text: String): String {
        val lines = text.split('\n')
        if (lines.isEmpty()) return text

        // Filter out empty lines for indent calculation
        val nonEmptyLines = lines.filter { it.isNotBlank() }
        if (nonEmptyLines.isEmpty()) {
            return lines.joinToString("\n")
        }

        // Find minimum leading spaces
        val minIndent =
            nonEmptyLines.minOf { line ->
                line.takeWhile { it == ' ' }.length
            }

        // Remove the common indentation
        return lines
            .joinToString("\n") { line ->
                if (line.length >= minIndent && line.isNotBlank()) {
                    line.substring(minIndent)
                } else {
                    line
                }
            }.trim()
    }

    private fun scanJsonPath(): Token {
        val loc = currentLocation()
        val sb = StringBuilder()
        sb.append(advance()) // $

        while (!isAtEnd()) {
            val c = peek()
            if (c.isLetterOrDigit() || c == '.' || c == '[' || c == ']' || c == '*' || c == '?' || c == '@' || c == '_') {
                sb.append(advance())
            } else {
                break
            }
        }

        return Token(TokenType.JSON_PATH, sb.toString(), loc)
    }

    private fun scanVariable(): Token {
        val loc = currentLocation()
        advance() // {
        advance() // {

        val sb = StringBuilder()
        while (!isAtEnd() && !(peek() == '}' && peekAhead(1) == '}')) {
            sb.append(advance())
        }

        if (!isAtEnd() && peek() == '}') {
            advance()
            if (!isAtEnd() && peek() == '}') {
                advance()
            }
        }

        return Token(TokenType.VARIABLE, sb.toString().trim(), loc)
    }

    private fun scanNumber(): Token {
        val loc = currentLocation()
        val sb = StringBuilder()

        if (peek() == '-') {
            sb.append(advance())
        }

        while (!isAtEnd() && peek().isDigit()) {
            sb.append(advance())
        }

        // Check for status range pattern (e.g., 1xx, 2xx, 3xx, 4xx, 5xx)
        val value = sb.toString()
        if (value.length == 1 && value[0] in '1'..'5') {
            val nextTwo = peekString(2)
            if (nextTwo.equals("xx", ignoreCase = true)) {
                sb.append(advance()) // x
                sb.append(advance()) // x
                return Token(TokenType.STATUS_RANGE, sb.toString(), loc)
            }
        }

        if (!isAtEnd() && peek() == '.' && peekAhead(1)?.isDigit() == true) {
            sb.append(advance())
            while (!isAtEnd() && peek().isDigit()) {
                sb.append(advance())
            }
        }

        return Token(TokenType.NUMBER, sb.toString(), loc)
    }

    /**
     * Peek the next n characters without advancing.
     */
    private fun peekString(n: Int): String {
        val sb = StringBuilder()
        for (i in 0 until n) {
            val c = peekAhead(i)
            if (c != null) {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun scanIdentifier(): Token {
        val loc = currentLocation()
        val sb = StringBuilder()

        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_' || peek() == '-')) {
            sb.append(advance())
        }

        val text = sb.toString()
        val type = KEYWORDS[text.lowercase()] ?: TokenType.IDENTIFIER

        return Token(type, text, loc)
    }

    /**
     * Scan an operation ID prefixed with ^.
     *
     * Operation IDs are explicitly marked with ^ prefix to distinguish them
     * from regular identifiers. For example: ^listPets, ^getPetById
     *
     * @return OPERATION_ID token with the identifier value (excluding ^),
     *         or ERROR token if ^ is not followed by a valid identifier
     */
    private fun scanOperationId(): Token {
        val loc = currentLocation()
        advance() // consume '^'

        // Check if followed by a valid identifier start (letter or underscore)
        if (isAtEnd() || (!peek().isLetter() && peek() != '_')) {
            return Token(TokenType.ERROR, "Expected identifier after '^'", loc)
        }

        // Read the identifier
        val sb = StringBuilder()
        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_')) {
            sb.append(advance())
        }

        return Token(TokenType.OPERATION_ID, sb.toString(), loc)
    }

    /**
     * Scan a tag prefixed with @.
     *
     * Tags are used to categorize and filter scenarios.
     * For example: @ignore, @slow, @wip
     *
     * @return TAG token with the tag name (excluding @),
     *         or ERROR token if @ is not followed by a valid identifier
     */
    private fun scanTag(): Token {
        val loc = currentLocation()
        advance() // consume '@'

        // Check if followed by a valid identifier start (letter or underscore or hyphen)
        if (isAtEnd() || (!peek().isLetter() && peek() != '_')) {
            return Token(TokenType.ERROR, "Expected identifier after '@'", loc)
        }

        // Read the tag name (allows letters, digits, underscore, hyphen)
        val sb = StringBuilder()
        while (!isAtEnd() && (peek().isLetterOrDigit() || peek() == '_' || peek() == '-')) {
            sb.append(advance())
        }

        return Token(TokenType.TAG, sb.toString(), loc)
    }

    private fun scanSymbol(): Token {
        val loc = currentLocation()
        val c = advance()

        val type =
            when (c) {
                ':' -> TokenType.COLON
                '=' -> {
                    if (!isAtEnd() && peek() == '>') {
                        advance()
                        return Token(TokenType.ARROW, "=>", loc)
                    }
                    TokenType.EQUALS
                }
                '-' -> {
                    if (!isAtEnd() && peek() == '>') {
                        advance()
                        return Token(TokenType.ARROW, "->", loc)
                    }
                    TokenType.ERROR
                }
                '(' -> TokenType.OPEN_PAREN
                ')' -> TokenType.CLOSE_PAREN
                '{' -> TokenType.OPEN_BRACE
                '}' -> TokenType.CLOSE_BRACE
                '[' -> TokenType.OPEN_BRACKET
                ']' -> TokenType.CLOSE_BRACKET
                ',' -> TokenType.COMMA
                '|' -> TokenType.PIPE
                '.' -> TokenType.DOT
                else -> TokenType.ERROR
            }

        return Token(type, c.toString(), loc)
    }

    private fun skipWhitespace() {
        while (!isAtEnd() && (peek() == ' ' || peek() == '\t') && !atLineStart) {
            advance()
        }
    }

    private fun skipComment() {
        while (!isAtEnd() && peek() != '\n') {
            advance()
        }
    }

    private fun currentLocation() = SourceLocation(line, column, fileName)

    private fun isAtEnd(offset: Int = 0) = pos + offset >= source.length

    private fun peek(offset: Int = 0): Char = if (isAtEnd(offset)) '\u0000' else source[pos + offset]

    private fun peekAhead(n: Int): Char? = if (pos + n >= source.length) null else source[pos + n]

    private fun advance(): Char {
        val c = source[pos++]
        column++
        return c
    }
}
