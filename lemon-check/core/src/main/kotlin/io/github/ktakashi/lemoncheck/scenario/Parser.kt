package io.github.ktakashi.lemoncheck.scenario

/**
 * Parser for scenario files.
 *
 * Parses tokenized scenario files into an AST.
 * Supports:
 * - Scenarios and scenario outlines
 * - Fragments
 * - Given/When/Then steps
 * - API calls, assertions, extractions
 * - Examples tables for parameterization
 */
class Parser(
    private val tokens: List<Token>,
    private val fileName: String? = null,
) {
    private var pos = 0
    private val errors = mutableListOf<ParseError>()

    companion object {
        /**
         * Parse source code directly.
         */
        fun parse(
            source: String,
            fileName: String? = null,
        ): ParserResult {
            val lexer = Lexer(source, fileName)
            val tokens = lexer.tokenize()
            return Parser(tokens, fileName).parse()
        }
    }

    /**
     * Result of parsing.
     */
    data class ParserResult(
        val ast: ScenarioFileNode?,
        val errors: List<ParseError>,
    ) {
        val isSuccess: Boolean get() = errors.isEmpty() && ast != null
    }

    /**
     * Parse the token stream into an AST.
     */
    fun parse(): ParserResult {
        val scenarios = mutableListOf<ScenarioNode>()
        val fragments = mutableListOf<FragmentNode>()
        var parameters: ParametersNode? = null
        val startLocation = currentLocation()

        skipNewlines()

        while (!isAtEnd()) {
            when (current().type) {
                TokenType.PARAMETERS -> {
                    parameters = parseParameters()
                }
                TokenType.SCENARIO -> {
                    val scenario = parseScenario()
                    if (scenario != null) scenarios.add(scenario)
                }
                TokenType.OUTLINE -> {
                    val scenario = parseScenarioOutline()
                    if (scenario != null) scenarios.add(scenario)
                }
                TokenType.FRAGMENT -> {
                    val fragment = parseFragment()
                    if (fragment != null) fragments.add(fragment)
                }
                TokenType.EOF -> break
                TokenType.NEWLINE, TokenType.INDENT, TokenType.DEDENT -> {
                    advance()
                }
                else -> {
                    errors.add(
                        ParseError(
                            "Unexpected token",
                            currentLocation(),
                            expected = "parameters, scenario, outline, or fragment",
                            found = current().value,
                        ),
                    )
                    advance()
                }
            }
            skipNewlines()
        }

        val ast =
            if (errors.isEmpty()) {
                ScenarioFileNode(scenarios, fragments, parameters, startLocation)
            } else {
                null
            }

        return ParserResult(ast, errors)
    }

    private fun parseScenario(): ScenarioNode? {
        val loc = currentLocation()

        if (!expect(TokenType.SCENARIO)) return null
        skipWhitespace()

        if (!expect(TokenType.COLON)) return null
        skipWhitespace()

        val name = parseScenarioName() ?: return null
        skipNewlines()

        val steps = parseSteps()

        return ScenarioNode(
            name = name,
            steps = steps,
            isOutline = false,
            examples = null,
            location = loc,
        )
    }

    private fun parseScenarioOutline(): ScenarioNode? {
        val loc = currentLocation()

        if (!expect(TokenType.OUTLINE)) return null
        skipWhitespace()

        if (!expect(TokenType.COLON)) return null
        skipWhitespace()

        val name = parseScenarioName() ?: return null
        skipNewlines()

        val steps = parseSteps()
        val examples = parseExamples()

        return ScenarioNode(
            name = name,
            steps = steps,
            isOutline = true,
            examples = examples,
            location = loc,
        )
    }

    private fun parseFragment(): FragmentNode? {
        val loc = currentLocation()

        if (!expect(TokenType.FRAGMENT)) return null
        skipWhitespace()

        if (!expect(TokenType.COLON)) return null
        skipWhitespace()

        val name = parseFragmentName() ?: return null
        skipNewlines()

        val steps = parseSteps()

        return FragmentNode(
            name = name,
            steps = steps,
            location = loc,
        )
    }

    /**
     * Parse file-level parameters block.
     *
     * Syntax:
     * ```
     * parameters:
     *   name: value
     *   name2: value2
     * ```
     */
    private fun parseParameters(): ParametersNode? {
        val loc = currentLocation()

        if (!expect(TokenType.PARAMETERS)) return null
        skipWhitespace()

        if (!expect(TokenType.COLON)) return null
        skipNewlines()

        val values = mutableMapOf<String, Any>()

        // Expect indent
        if (current().type == TokenType.INDENT) {
            advance()
        }

        // Parse name: value pairs
        while (!isAtEnd() &&
            current().type != TokenType.DEDENT &&
            current().type != TokenType.SCENARIO &&
            current().type != TokenType.OUTLINE &&
            current().type != TokenType.FRAGMENT &&
            current().type != TokenType.PARAMETERS
        ) {
            if (current().type == TokenType.NEWLINE) {
                advance()
                continue
            }

            // Parse parameter name (can be compound like "header.Authorization" or "header.X-Custom")
            // We need to read tokens until we hit a colon at the same level
            val paramName = parseParameterName() ?: break
            skipWhitespace()

            // Expect colon
            if (!expect(TokenType.COLON)) {
                errors.add(ParseError("Expected ':' after parameter name", currentLocation()))
                break
            }
            skipWhitespace()

            // Parse value
            val value = parseParameterValue()
            if (value != null) {
                values[paramName] = value
            }

            skipNewlines()
        }

        // Handle dedent
        if (current().type == TokenType.DEDENT) {
            advance()
        }

        return ParametersNode(values, loc)
    }

    /**
     * Parse a parameter name, supporting compound names with dots and hyphens.
     * E.g., "header.Authorization", "header.X-Custom", "autoAssertions.enabled"
     */
    private fun parseParameterName(): String? {
        if (current().type != TokenType.IDENTIFIER) {
            return null
        }

        val parts = StringBuilder(current().value)
        advance()

        // Continue reading while we see dots, hyphens, or identifiers
        while (!isAtEnd() && current().type != TokenType.COLON && current().type != TokenType.NEWLINE) {
            when (current().type) {
                TokenType.DOT -> {
                    parts.append(".")
                    advance()
                }
                TokenType.IDENTIFIER -> {
                    parts.append(current().value)
                    advance()
                }
                TokenType.NUMBER -> {
                    // Support numbers in compound names
                    parts.append(current().value)
                    advance()
                }
                TokenType.ERROR -> {
                    // Might be a hyphen or other character - include it
                    val value = current().value
                    if (value == "-" || value.startsWith("-")) {
                        parts.append(value)
                        advance()
                    } else {
                        break
                    }
                }
                else -> break
            }
        }

        return parts.toString()
    }

    /**
     * Parse a parameter value (string, number, or boolean).
     */
    private fun parseParameterValue(): Any? =
        when (current().type) {
            TokenType.STRING -> {
                val value = current().value
                advance()
                value
            }
            TokenType.NUMBER -> {
                val value = current().value
                advance()
                if (value.contains('.')) value.toDouble() else value.toLong()
            }
            TokenType.IDENTIFIER -> {
                val value = current().value
                advance()
                // Handle boolean values
                when (value.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> value
                }
            }
            else -> {
                errors.add(ParseError("Expected parameter value", currentLocation()))
                null
            }
        }

    private fun parseScenarioName(): String? {
        val nameParts = mutableListOf<String>()

        while (!isAtEnd() && current().type != TokenType.NEWLINE && current().type != TokenType.EOF) {
            nameParts.add(current().value)
            advance()
        }

        if (nameParts.isEmpty()) {
            errors.add(ParseError("Expected scenario name", currentLocation()))
            return null
        }

        return nameParts.joinToString(" ").trim()
    }

    private fun parseFragmentName(): String? = parseScenarioName()

    private fun parseSteps(): List<StepNode> {
        val steps = mutableListOf<StepNode>()

        // Expect indent
        if (current().type == TokenType.INDENT) {
            advance()
        }

        while (!isAtEnd()) {
            val stepKeyword =
                when (current().type) {
                    TokenType.GIVEN -> StepKeyword.GIVEN
                    TokenType.WHEN -> StepKeyword.WHEN
                    TokenType.THEN -> StepKeyword.THEN
                    TokenType.AND -> StepKeyword.AND
                    TokenType.DEDENT, TokenType.SCENARIO, TokenType.OUTLINE, TokenType.FRAGMENT, TokenType.EXAMPLES, TokenType.EOF -> break
                    else -> {
                        advance()
                        continue
                    }
                }

            steps.add(parseStep(stepKeyword))
        }

        // Consume dedent if present
        if (current().type == TokenType.DEDENT) {
            advance()
        }

        return steps
    }

    private fun parseStep(keyword: StepKeyword): StepNode {
        val loc = currentLocation()
        advance() // consume keyword
        skipWhitespace()

        val description = parseStepDescription()
        skipNewlines()

        val actions = parseActions()

        return StepNode(
            keyword = keyword,
            description = description,
            actions = actions,
            location = loc,
        )
    }

    private fun parseStepDescription(): String {
        val parts = mutableListOf<String>()

        while (!isAtEnd() && current().type != TokenType.NEWLINE && current().type != TokenType.EOF) {
            parts.add(current().value)
            advance()
        }

        return parts.joinToString(" ").trim()
    }

    private fun parseActions(): List<ActionNode> {
        val actions = mutableListOf<ActionNode>()

        // Check for indent
        if (current().type == TokenType.INDENT) {
            advance()

            while (!isAtEnd()) {
                when (current().type) {
                    TokenType.CALL -> parseCallAction()?.let { actions.add(it) }
                    TokenType.EXTRACT -> parseExtractAction()?.let { actions.add(it) }
                    TokenType.ASSERT -> actions.add(parseAssertAction())
                    TokenType.INCLUDE -> parseIncludeAction()?.let { actions.add(it) }
                    TokenType.NEWLINE -> advance()
                    TokenType.DEDENT, TokenType.GIVEN, TokenType.WHEN, TokenType.THEN, TokenType.AND, TokenType.EOF -> break
                    else -> advance()
                }
            }

            // Consume dedent
            if (current().type == TokenType.DEDENT) {
                advance()
            }
        }

        return actions
    }

    private fun parseCallAction(): CallNode? {
        val loc = currentLocation()
        advance() // consume 'call'
        skipWhitespace()

        var specName: String? = null

        // Check for "using spec_name"
        if (current().type == TokenType.USING) {
            advance()
            skipWhitespace()
            if (current().type == TokenType.IDENTIFIER || current().type == TokenType.STRING) {
                specName = current().value
                advance()
            }
            skipWhitespace()
        }

        // Get operation ID
        if (current().type != TokenType.OPERATION_ID && current().type != TokenType.IDENTIFIER) {
            errors.add(ParseError("Expected operation ID", currentLocation()))
            return null
        }

        val operationId = current().value
        advance()

        val parameters = mutableMapOf<String, ValueNode>()
        val headers = mutableMapOf<String, ValueNode>()
        var body: ValueNode? = null

        // Parse optional parameters block
        skipNewlines()
        if (current().type == TokenType.INDENT) {
            advance()

            while (!isAtEnd() && current().type != TokenType.DEDENT) {
                when (current().type) {
                    TokenType.IDENTIFIER, TokenType.OPERATION_ID -> {
                        val paramName = current().value
                        advance()
                        skipWhitespace()

                        if (current().type == TokenType.COLON || current().type == TokenType.EQUALS) {
                            advance()
                            skipWhitespace()
                        }

                        val value = parseValue()
                        if (value != null) {
                            when {
                                paramName.startsWith("header_") || paramName.startsWith("Header_") -> {
                                    headers[paramName.removePrefix("header_").removePrefix("Header_")] = value
                                }
                                paramName == "body" -> body = value
                                else -> parameters[paramName] = value
                            }
                        }
                    }
                    TokenType.NEWLINE -> advance()
                    else -> advance()
                }
            }

            if (current().type == TokenType.DEDENT) {
                advance()
            }
        }

        return CallNode(
            operationId = operationId,
            specName = specName,
            parameters = parameters,
            headers = headers,
            body = body,
            location = loc,
        )
    }

    private fun parseExtractAction(): ExtractNode? {
        val loc = currentLocation()
        advance() // consume 'extract'
        skipWhitespace()

        // Parse JSON path
        if (current().type != TokenType.JSON_PATH && current().type != TokenType.STRING) {
            errors.add(ParseError("Expected JSON path", currentLocation()))
            return null
        }
        val jsonPath = current().value
        advance()
        skipWhitespace()

        // Parse arrow
        if (current().type != TokenType.ARROW) {
            errors.add(ParseError("Expected '=>' or '->'", currentLocation()))
            return null
        }
        advance()
        skipWhitespace()

        // Parse variable name
        val current = current()
        val variableName =
            when (current.type) {
                TokenType.VARIABLE, TokenType.IDENTIFIER -> current.value
                else -> {
                    errors.add(ParseError("Expected variable name", currentLocation()))
                    return null
                }
            }
        advance()

        return ExtractNode(
            variableName = variableName,
            jsonPath = jsonPath,
            location = loc,
        )
    }

    private fun parseAssertAction(): AssertNode {
        val loc = currentLocation()
        advance() // consume 'assert'
        skipWhitespace()

        // Determine assertion type
        val assertionKind: AssertionKind
        var path: String? = null
        var expected: ValueNode? = null
        var headerName: String? = null

        val typeOrPath = current().value.lowercase()
        advance()
        skipWhitespace()

        when {
            typeOrPath == "status" || typeOrPath == "statuscode" -> {
                assertionKind = AssertionKind.STATUS_CODE
                expected = parseValue()
            }
            typeOrPath == "schema" || typeOrPath == "matchesschema" -> {
                assertionKind = AssertionKind.MATCHES_SCHEMA
            }
            typeOrPath == "header" -> {
                skipWhitespace()
                headerName = current().value
                advance()
                skipWhitespace()

                if (current().type == TokenType.EQUALS || current().type == TokenType.COLON) {
                    advance()
                    skipWhitespace()
                    assertionKind = AssertionKind.HEADER_EQUALS
                    expected = parseValue()
                } else {
                    assertionKind = AssertionKind.HEADER_EXISTS
                }
            }
            typeOrPath == "contains" || typeOrPath == "bodycontains" -> {
                assertionKind = AssertionKind.BODY_CONTAINS
                expected = parseValue()
            }
            typeOrPath.startsWith("$") -> {
                path = typeOrPath
                skipWhitespace()

                when {
                    current().type == TokenType.EQUALS || current().value.lowercase() == "equals" -> {
                        advance()
                        skipWhitespace()
                        assertionKind = AssertionKind.BODY_EQUALS
                        expected = parseValue()
                    }
                    current().value.lowercase() == "matches" -> {
                        advance()
                        skipWhitespace()
                        assertionKind = AssertionKind.BODY_MATCHES
                        expected = parseValue()
                    }
                    current().value.lowercase() == "size" || current().value.lowercase() == "arraysize" -> {
                        advance()
                        skipWhitespace()
                        assertionKind = AssertionKind.BODY_ARRAY_SIZE
                        expected = parseValue()
                    }
                    current().value.lowercase() == "notempty" -> {
                        advance()
                        assertionKind = AssertionKind.BODY_ARRAY_NOT_EMPTY
                    }
                    else -> {
                        assertionKind = AssertionKind.BODY_EQUALS
                        expected = parseValue()
                    }
                }
            }
            typeOrPath == "responsetime" -> {
                assertionKind = AssertionKind.RESPONSE_TIME
                expected = parseValue()
            }
            else -> {
                assertionKind = AssertionKind.BODY_CONTAINS
                expected = StringValueNode(typeOrPath, loc)
            }
        }

        return AssertNode(
            assertionType = assertionKind,
            path = path,
            expected = expected,
            headerName = headerName,
            location = loc,
        )
    }

    private fun parseIncludeAction(): IncludeNode? {
        val loc = currentLocation()
        advance() // consume 'include'
        skipWhitespace()

        val fragmentName =
            when (current().type) {
                TokenType.STRING -> current().value
                TokenType.IDENTIFIER, TokenType.OPERATION_ID -> current().value
                else -> {
                    errors.add(ParseError("Expected fragment name", currentLocation()))
                    return null
                }
            }
        advance()

        return IncludeNode(
            fragmentName = fragmentName,
            location = loc,
        )
    }

    private fun parseExamples(): List<ExampleRowNode>? {
        skipNewlines()
        if (current().type != TokenType.EXAMPLES) {
            return null
        }
        advance() // consume 'examples'
        skipWhitespace()

        if (current().type == TokenType.COLON) {
            advance()
        }
        skipNewlines()

        val rows = mutableListOf<ExampleRowNode>()

        // Expect indent
        if (current().type == TokenType.INDENT) {
            advance()
        }

        // Parse header row (column names)
        val headers = mutableListOf<String>()
        if (current().type == TokenType.PIPE) {
            advance()
            while (!isAtEnd() && current().type != TokenType.NEWLINE) {
                when (current().type) {
                    TokenType.IDENTIFIER, TokenType.OPERATION_ID, TokenType.STRING -> {
                        headers.add(current().value)
                        advance()
                    }
                    TokenType.PIPE -> {
                        advance()
                    }
                    else -> {
                        // Skip unknown tokens to avoid infinite loop
                        advance()
                    }
                }
            }
            skipNewlines()
        }

        // Parse data rows
        while (
            !isAtEnd() &&
            current().type != TokenType.DEDENT &&
            current().type != TokenType.SCENARIO &&
            current().type != TokenType.OUTLINE &&
            current().type != TokenType.FRAGMENT
        ) {
            if (current().type == TokenType.PIPE) {
                val row = parseExampleRow(headers)
                if (row != null) {
                    rows.add(row)
                }
            } else if (current().type == TokenType.NEWLINE) {
                advance()
            } else {
                break
            }
        }

        // Consume dedent
        if (current().type == TokenType.DEDENT) {
            advance()
        }

        return rows.ifEmpty { null }
    }

    private fun parseExampleRow(headers: List<String>): ExampleRowNode? {
        val loc = currentLocation()
        val values = mutableMapOf<String, ValueNode>()

        advance() // consume initial pipe

        var columnIndex = 0
        while (!isAtEnd() && current().type != TokenType.NEWLINE) {
            when (current().type) {
                TokenType.PIPE -> {
                    advance()
                }
                else -> {
                    val value = parseValue()
                    if (value != null && columnIndex < headers.size) {
                        values[headers[columnIndex]] = value
                        columnIndex++
                    } else {
                        // Skip unknown tokens to avoid infinite loop
                        advance()
                    }
                }
            }
        }

        skipNewlines()

        return if (values.isNotEmpty()) {
            ExampleRowNode(values, loc)
        } else {
            null
        }
    }

    private fun parseValue(): ValueNode? {
        val loc = currentLocation()

        return when (current().type) {
            TokenType.IDENTIFIER,
            TokenType.OPERATION_ID,
            TokenType.STRING,
            -> {
                val value = current().value
                advance()
                StringValueNode(value, loc)
            }
            TokenType.NUMBER -> {
                val value = current().value
                advance()
                val num =
                    if (value.contains('.')) {
                        value.toDouble()
                    } else {
                        value.toLong()
                    }
                NumberValueNode(num, loc)
            }
            TokenType.VARIABLE -> {
                val name = current().value
                advance()
                VariableValueNode(name, loc)
            }
            TokenType.OPEN_BRACE -> {
                parseJsonObject(loc)
            }
            TokenType.OPEN_BRACKET -> {
                parseJsonArray(loc)
            }
            else -> null
        }
    }

    private fun parseJsonObject(loc: SourceLocation): JsonValueNode {
        val sb = StringBuilder()
        var depth = 0

        while (!isAtEnd()) {
            when (current().type) {
                TokenType.OPEN_BRACE -> {
                    depth++
                    sb.append('{')
                }
                TokenType.CLOSE_BRACE -> {
                    depth--
                    sb.append('}')
                    if (depth == 0) {
                        advance()
                        return JsonValueNode(sb.toString(), loc)
                    }
                }
                TokenType.STRING -> sb.append('"').append(current().value).append('"')
                TokenType.NUMBER -> sb.append(current().value)
                TokenType.COLON -> sb.append(':')
                TokenType.COMMA -> sb.append(',')
                TokenType.OPEN_BRACKET -> sb.append('[')
                TokenType.CLOSE_BRACKET -> sb.append(']')
                TokenType.IDENTIFIER -> sb.append('"').append(current().value).append('"')
                else -> {}
            }
            advance()
        }

        return JsonValueNode(sb.toString(), loc)
    }

    private fun parseJsonArray(loc: SourceLocation): JsonValueNode {
        val sb = StringBuilder()
        var depth = 0

        while (!isAtEnd()) {
            when (current().type) {
                TokenType.OPEN_BRACKET -> {
                    depth++
                    sb.append('[')
                }
                TokenType.CLOSE_BRACKET -> {
                    depth--
                    sb.append(']')
                    if (depth == 0) {
                        advance()
                        return JsonValueNode(sb.toString(), loc)
                    }
                }
                TokenType.STRING -> sb.append('"').append(current().value).append('"')
                TokenType.NUMBER -> sb.append(current().value)
                TokenType.COLON -> sb.append(':')
                TokenType.COMMA -> sb.append(',')
                TokenType.OPEN_BRACE -> sb.append('{')
                TokenType.CLOSE_BRACE -> sb.append('}')
                TokenType.IDENTIFIER -> sb.append('"').append(current().value).append('"')
                else -> {}
            }
            advance()
        }

        return JsonValueNode(sb.toString(), loc)
    }

    private fun expect(type: TokenType): Boolean {
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

    private fun skipWhitespace() {
        while (!isAtEnd() && (current().type == TokenType.INDENT)) {
            // Don't skip indent tokens - they're significant
            break
        }
    }

    private fun skipNewlines() {
        while (!isAtEnd() && current().type == TokenType.NEWLINE) {
            advance()
        }
    }

    private fun current(): Token = if (isAtEnd()) tokens.last() else tokens[pos]

    private fun currentLocation(): SourceLocation = current().location

    private fun advance(): Token {
        if (!isAtEnd()) pos++
        return tokens[pos - 1]
    }

    private fun isAtEnd(): Boolean = pos >= tokens.size || tokens[pos].type == TokenType.EOF
}
