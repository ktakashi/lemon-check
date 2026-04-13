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
        val features = mutableListOf<FeatureNode>()
        var parameters: ParametersNode? = null
        val startLocation = currentLocation()

        skipNewlines()

        while (!isAtEnd()) {
            // Collect tags before scenarios or features
            val tags = parseTags()

            when (current().type) {
                TokenType.PARAMETERS -> {
                    parameters = parseParameters()
                }
                TokenType.SCENARIO -> {
                    val scenario = parseScenario(tags)
                    if (scenario != null) scenarios.add(scenario)
                }
                TokenType.OUTLINE -> {
                    val scenario = parseScenarioOutline(tags)
                    if (scenario != null) scenarios.add(scenario)
                }
                TokenType.FEATURE -> {
                    val feature = parseFeature(tags)
                    if (feature != null) features.add(feature)
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
                            expected = "parameters, scenario, outline, feature, or fragment",
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
                ScenarioFileNode(scenarios, fragments, features, parameters, startLocation)
            } else {
                null
            }

        return ParserResult(ast, errors)
    }

    /**
     * Parse tags preceding a scenario or feature.
     *
     * Syntax: @tag1 @tag2 @tag3
     * Tags must be on the same line or on lines before the scenario/feature.
     */
    private fun parseTags(): Set<String> {
        val tags = mutableSetOf<String>()

        while (!isAtEnd() && current().type == TokenType.TAG) {
            tags.add(current().value)
            advance()
            skipWhitespace()
            skipNewlines()
        }

        return tags
    }

    private fun parseScenario(tags: Set<String> = emptySet()): ScenarioNode? {
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
            tags = tags,
            location = loc,
        )
    }

    private fun parseScenarioOutline(tags: Set<String> = emptySet()): ScenarioNode? {
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
            tags = tags,
            location = loc,
        )
    }

    /**
     * Parse a feature block containing grouped scenarios and optional background.
     *
     * Syntax:
     * ```
     * @tag1 @tag2
     * feature: My Feature Name
     *   background:
     *     given: precondition
     *       call ^setup
     *
     *   scenario: first scenario
     *     when: ...
     *
     *   @ignore
     *   scenario: second scenario
     *     when: ...
     * ```
     */
    private fun parseFeature(tags: Set<String> = emptySet()): FeatureNode? {
        val loc = currentLocation()

        if (!expect(TokenType.FEATURE)) return null
        skipWhitespace()

        if (!expect(TokenType.COLON)) return null
        skipWhitespace()

        val name = parseScenarioName() ?: return null
        skipNewlines()

        // Expect indent for feature body
        if (current().type == TokenType.INDENT) {
            advance()
        }

        var background: BackgroundNode? = null
        val scenarios = mutableListOf<ScenarioNode>()

        while (!isAtEnd() && current().type != TokenType.DEDENT && current().type != TokenType.FEATURE) {
            skipNewlines()
            if (isAtEnd() || current().type == TokenType.DEDENT || current().type == TokenType.FEATURE) break

            // Parse tags for nested scenarios
            val scenarioTags = parseTags()

            when (current().type) {
                TokenType.BACKGROUND -> {
                    background = parseBackground()
                }
                TokenType.SCENARIO -> {
                    val scenario = parseScenario(scenarioTags)
                    if (scenario != null) scenarios.add(scenario)
                }
                TokenType.OUTLINE -> {
                    val scenario = parseScenarioOutline(scenarioTags)
                    if (scenario != null) scenarios.add(scenario)
                }
                TokenType.NEWLINE, TokenType.INDENT -> {
                    advance()
                }
                else -> {
                    if (current().type != TokenType.DEDENT && current().type != TokenType.EOF) {
                        errors.add(
                            ParseError(
                                "Unexpected token in feature",
                                currentLocation(),
                                expected = "background, scenario, or outline",
                                found = current().value,
                            ),
                        )
                        advance()
                    }
                    break
                }
            }
            skipNewlines()
        }

        // Consume dedent if present
        if (current().type == TokenType.DEDENT) {
            advance()
        }

        return FeatureNode(
            name = name,
            background = background,
            scenarios = scenarios,
            tags = tags,
            location = loc,
        )
    }

    /**
     * Parse background steps shared by all scenarios in a feature.
     *
     * Syntax:
     * ```
     * background:
     *   given: precondition
     *     call ^setup
     * ```
     */
    private fun parseBackground(): BackgroundNode? {
        val loc = currentLocation()

        if (!expect(TokenType.BACKGROUND)) return null
        skipWhitespace()

        if (!expect(TokenType.COLON)) return null
        skipNewlines()

        val steps = parseSteps()

        return BackgroundNode(
            steps = steps,
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
                    TokenType.BUT -> StepKeyword.BUT
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
                    TokenType.ASSERT -> parseAssertAction()?.let { actions.add(it) }
                    TokenType.INCLUDE -> parseIncludeAction()?.let { actions.add(it) }
                    TokenType.IF -> parseConditional()?.let { actions.add(it) }
                    TokenType.FAIL -> parseFailAction()?.let { actions.add(it) }
                    TokenType.NEWLINE -> advance()
                    TokenType.DEDENT, TokenType.GIVEN, TokenType.WHEN, TokenType.THEN, TokenType.AND, TokenType.BUT, TokenType.EOF -> break
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
        var bodyProperties: Map<String, BodyPropertyValue>? = null
        var bodyFile: String? = null
        var autoTestConfig: AutoTestConfig? = null
        var autoTestExcludes: Set<String>? = null

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

                        when {
                            paramName.startsWith("header_") || paramName.startsWith("Header_") -> {
                                val value = parseValue()
                                if (value != null) {
                                    headers[paramName.removePrefix("header_").removePrefix("Header_")] = value
                                }
                            }
                            // body: can be raw JSON (inline) or structured properties (indented)
                            paramName == "body" -> {
                                when (val bodyResult = parseBodyContent()) {
                                    is BodyParseResult.Raw -> body = bodyResult.value
                                    is BodyParseResult.Properties -> bodyProperties = bodyResult.properties
                                }
                            }
                            paramName == "bodyFile" -> bodyFile = parseBodyFilePath()
                            paramName == "auto" -> autoTestConfig = parseAutoTestConfig()
                            paramName == "excludes" -> autoTestExcludes = parseAutoTestExcludes()
                            else -> {
                                val value = parseValue()
                                if (value != null) {
                                    parameters[paramName] = value
                                }
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

        // Merge auto test config with excludes
        val finalAutoTestConfig =
            if (autoTestConfig != null && autoTestExcludes != null) {
                AutoTestConfig(autoTestConfig.types, autoTestExcludes, autoTestConfig.location)
            } else if (autoTestConfig != null) {
                autoTestConfig
            } else {
                null
            }

        return CallNode(
            operationId = operationId,
            specName = specName,
            parameters = parameters,
            headers = headers,
            body = body,
            bodyProperties = bodyProperties,
            bodyFile = bodyFile,
            autoTestConfig = finalAutoTestConfig,
            location = loc,
        )
    }

    /**
     * Result of parsing body content.
     */
    private sealed class BodyParseResult {
        data class Raw(
            val value: ValueNode?,
        ) : BodyParseResult()

        data class Properties(
            val properties: Map<String, BodyPropertyValue>,
        ) : BodyParseResult()
    }

    /**
     * Parse body content. Can be either:
     * - Raw JSON value on the same line
     * - Triple-quoted multi-line body (""")
     * - Structured properties on subsequent indented lines
     */
    private fun parseBodyContent(): BodyParseResult {
        // Check if there's a value on the same line (raw JSON or string)
        if (current().type != TokenType.NEWLINE && current().type != TokenType.EOF) {
            val value = parseValue()
            return BodyParseResult.Raw(value)
        }

        // Check for content after newline + indent
        skipNewlines()
        if (current().type == TokenType.INDENT) {
            advance()

            // Check if next token is a STRING (could be triple-quoted content from lexer)
            if (current().type == TokenType.STRING) {
                val value = parseValue()
                if (current().type == TokenType.DEDENT) {
                    advance()
                }
                return BodyParseResult.Raw(value)
            }

            val properties = parseBodyProperties()
            if (current().type == TokenType.DEDENT) {
                advance()
            }
            return BodyParseResult.Properties(properties)
        }

        return BodyParseResult.Raw(null)
    }

    /**
     * Parse body properties recursively.
     */
    private fun parseBodyProperties(): Map<String, BodyPropertyValue> {
        val properties = mutableMapOf<String, BodyPropertyValue>()

        while (!isAtEnd() && current().type != TokenType.DEDENT) {
            when (current().type) {
                TokenType.IDENTIFIER -> {
                    val propName = current().value
                    advance()
                    skipWhitespace()

                    if (current().type == TokenType.COLON) {
                        advance()
                        skipWhitespace()
                    }

                    // Check if this is a nested object (newline + indent)
                    if (current().type == TokenType.NEWLINE) {
                        skipNewlines()
                        if (current().type == TokenType.INDENT) {
                            advance()
                            val nestedProps = parseBodyProperties()
                            if (current().type == TokenType.DEDENT) {
                                advance()
                            }
                            properties[propName] = BodyPropertyValue.Nested(nestedProps)
                        }
                    } else {
                        // Simple value
                        val value = parseValue()
                        if (value != null) {
                            properties[propName] = BodyPropertyValue.Simple(value)
                        }
                    }
                }
                TokenType.NEWLINE -> advance()
                else -> advance()
            }
        }

        return properties
    }

    /**
     * Parse a body file path.
     * Supports: classpath:path/to/file.json, file:./path.json, or /absolute/path.json
     */
    private fun parseBodyFilePath(): String? {
        // Use STRING token value if present (quoted path)
        if (current().type == TokenType.STRING) {
            val value = current().value
            advance()
            return value
        }

        // Otherwise build path from tokens until newline/dedent
        val sb = StringBuilder()

        while (!isAtEnd() && current().type != TokenType.NEWLINE && current().type != TokenType.DEDENT) {
            when (current().type) {
                TokenType.IDENTIFIER, TokenType.OPERATION_ID -> sb.append(current().value)
                TokenType.COLON -> sb.append(':')
                TokenType.DOT -> sb.append('.')
                TokenType.NUMBER -> sb.append(current().value)
                else -> {
                    // For other token types, try to append the raw value
                    val value = current().value
                    if (value.isNotBlank() && value !in listOf("{", "}", "[", "]", "(", ")", ",", "|")) {
                        sb.append(value)
                    }
                }
            }
            advance()
        }

        return sb.toString().takeIf { it.isNotBlank() }
    }

    /**
     * Parse auto test configuration.
     * Syntax: auto: [invalid security] or auto: [invalid] or auto: [security]
     */
    private fun parseAutoTestConfig(): AutoTestConfig? {
        val loc = currentLocation()
        val types = mutableSetOf<AutoTestType>()

        // Expect [ to start the list
        if (current().type != TokenType.OPEN_BRACKET) {
            // Try to parse as bare identifiers without brackets
            while (!isAtEnd() && current().type != TokenType.NEWLINE && current().type != TokenType.DEDENT) {
                if (current().type == TokenType.IDENTIFIER || current().type == TokenType.OPERATION_ID) {
                    val typeName = current().value.lowercase()
                    when (typeName) {
                        "invalid" -> types.add(AutoTestType.INVALID)
                        "security" -> types.add(AutoTestType.SECURITY)
                    }
                }
                advance()
            }
            return if (types.isNotEmpty()) AutoTestConfig(types, emptySet(), loc) else null
        }

        advance() // consume [

        // Parse test types
        while (!isAtEnd() && current().type != TokenType.CLOSE_BRACKET) {
            when (current().type) {
                TokenType.IDENTIFIER, TokenType.OPERATION_ID -> {
                    val typeName = current().value.lowercase()
                    when (typeName) {
                        "invalid" -> types.add(AutoTestType.INVALID)
                        "security" -> types.add(AutoTestType.SECURITY)
                    }
                    advance()
                }
                TokenType.COMMA -> advance()
                else -> advance()
            }
        }

        if (current().type == TokenType.CLOSE_BRACKET) {
            advance() // consume ]
        }

        return if (types.isNotEmpty()) AutoTestConfig(types, emptySet(), loc) else null
    }

    /**
     * Parse auto test excludes configuration.
     * Syntax: excludes: [SQLInjection maxLength] or excludes: [XSS, PathTraversal]
     *
     * Supports both space-separated and comma-separated values.
     */
    private fun parseAutoTestExcludes(): Set<String> {
        val excludes = mutableSetOf<String>()

        // Expect [ to start the list
        if (current().type != TokenType.OPEN_BRACKET) {
            // Try to parse as bare identifier
            if (current().type == TokenType.IDENTIFIER || current().type == TokenType.OPERATION_ID) {
                excludes.add(current().value)
                advance()
            }
            return excludes
        }

        advance() // consume [

        // Parse exclude names
        while (!isAtEnd() && current().type != TokenType.CLOSE_BRACKET) {
            when (current().type) {
                TokenType.IDENTIFIER, TokenType.OPERATION_ID -> {
                    excludes.add(current().value)
                    advance()
                }
                TokenType.COMMA -> advance()
                else -> advance()
            }
        }

        if (current().type == TokenType.CLOSE_BRACKET) {
            advance() // consume ]
        }

        return excludes
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

    private fun parseAssertAction(): AssertNode? {
        val loc = currentLocation()
        advance() // consume 'assert'
        skipWhitespace()

        // Check for "not" keyword at the beginning
        var negate = false
        if (current().value.lowercase() == "not") {
            negate = true
            advance()
            skipWhitespace()
        }

        // Determine assertion type
        val assertionKind: AssertionKind
        var path: String? = null
        var expected: ValueNode? = null
        var headerName: String? = null

        val typeOrPath = current().value.lowercase()
        val typeOrPathLoc = currentLocation()
        advance()
        skipWhitespace()

        when {
            typeOrPath == "status" || typeOrPath == "statuscode" -> {
                assertionKind = AssertionKind.STATUS_CODE
                expected = parseStatusValue()
            }
            typeOrPath == "schema" || typeOrPath == "matchesschema" -> {
                assertionKind = AssertionKind.MATCHES_SCHEMA
            }
            typeOrPath == "header" -> {
                skipWhitespace()
                // Parse header name (may contain hyphens, e.g., Content-Type, X-Request-Id)
                headerName = parseHeaderName()
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

                // Check for "not" after the JSON path (e.g., "assert $.name not equals ...")
                if (current().value.lowercase() == "not") {
                    negate = true
                    advance()
                    skipWhitespace()
                }

                val actionKeyword = current().value.lowercase()
                when {
                    current().type == TokenType.EQUALS || actionKeyword == "equals" -> {
                        advance()
                        skipWhitespace()
                        assertionKind = AssertionKind.BODY_EQUALS
                        expected = parseValue()
                    }
                    actionKeyword == "matches" -> {
                        advance()
                        skipWhitespace()
                        assertionKind = AssertionKind.BODY_MATCHES
                        expected = parseValue()
                    }
                    actionKeyword == "size" || actionKeyword == "arraysize" -> {
                        advance()
                        skipWhitespace()
                        assertionKind = AssertionKind.BODY_ARRAY_SIZE
                        expected = parseValue()
                    }
                    actionKeyword == "notempty" -> {
                        advance()
                        assertionKind = AssertionKind.BODY_ARRAY_NOT_EMPTY
                    }
                    else -> {
                        errors.add(
                            ParseError(
                                "Unknown assertion action '$actionKeyword' for JSON path. " +
                                    "Expected: equals, matches, size, arraysize, or notempty",
                                currentLocation(),
                            ),
                        )
                        return null
                    }
                }
            }
            typeOrPath == "responsetime" -> {
                assertionKind = AssertionKind.RESPONSE_TIME
                expected = parseValue()
            }
            else -> {
                errors.add(
                    ParseError(
                        "Unknown assertion type '$typeOrPath'. " +
                            "Expected: status, header, contains, \$.<jsonpath>, schema, or responsetime",
                        typeOrPathLoc,
                    ),
                )
                return null
            }
        }

        return AssertNode(
            assertionType = assertionKind,
            path = path,
            expected = expected,
            headerName = headerName,
            negate = negate,
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

    /**
     * Parse a status value, which can be:
     * - A number (200, 404, etc.)
     * - A pattern (1xx, 2xx, 3xx, 4xx, 5xx) representing a range (e.g., 4xx = 400-499)
     * - A variable reference
     */
    private fun parseStatusValue(): ValueNode? {
        val loc = currentLocation()

        // Check for lexer-detected status range tokens (e.g., "2xx", "4xx")
        if (current().type == TokenType.STATUS_RANGE) {
            val value = current().value
            val base = value[0].digitToInt()
            advance()
            return StatusRangeNode(base, loc)
        }

        // Check for status range patterns in identifiers/strings (fallback)
        if (current().type == TokenType.IDENTIFIER || current().type == TokenType.STRING) {
            val value = current().value
            val statusRangePattern = """^([1-5])xx$""".toRegex(RegexOption.IGNORE_CASE)
            val match = statusRangePattern.matchEntire(value)
            if (match != null) {
                val base = match.groupValues[1].toInt()
                advance()
                return StatusRangeNode(base, loc)
            }
        }

        // Fall back to regular value parsing
        return parseValue()
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

    /**
     * Parse a header name which may contain hyphens.
     *
     * HTTP header names commonly use hyphens (e.g., Content-Type, X-Request-Id).
     * This method concatenates identifier tokens separated by hyphen symbols
     * to form the complete header name.
     */
    private fun parseHeaderName(): String {
        val sb = StringBuilder()

        // First part must be an identifier
        if (current().type == TokenType.IDENTIFIER || current().type == TokenType.OPERATION_ID) {
            sb.append(current().value)
            advance()
        }

        // Continue while we see hyphen followed by identifier
        while (!isAtEnd() && current().value == "-") {
            sb.append("-")
            advance() // consume hyphen

            if (current().type == TokenType.IDENTIFIER || current().type == TokenType.OPERATION_ID) {
                sb.append(current().value)
                advance()
            } else {
                break
            }
        }

        return sb.toString()
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

    /**
     * Parse a conditional block (if/else if/else).
     *
     * Syntax:
     * ```
     * if <condition>
     *   <actions>
     * else if <condition>
     *   <actions>
     * else
     *   <actions>
     * ```
     *
     * Conditions support `and`/`or` operators:
     * ```
     * if status 4xx and test.type equals invalid
     * if status 200 or status 201
     * ```
     */
    private fun parseConditional(): ConditionalNode? {
        val loc = currentLocation()
        advance() // consume 'if'
        skipWhitespace()

        // Parse the primary condition with optional and/or
        val ifCondition = parseConditionWithLogicalOps() ?: return null

        skipNewlines()
        val ifActions = parseConditionalActions()
        val ifBranch = ConditionBranch(ifCondition, ifActions, loc)

        // Parse optional else-if branches
        val elseIfBranches = mutableListOf<ConditionBranch>()
        while (!isAtEnd() && current().type == TokenType.ELSE) {
            val elseIfLoc = currentLocation()
            advance() // consume 'else'
            skipWhitespace()

            if (current().type == TokenType.IF) {
                advance() // consume 'if'
                skipWhitespace()
                val elseIfCondition = parseConditionWithLogicalOps() ?: break
                skipNewlines()
                val elseIfActions = parseConditionalActions()
                elseIfBranches.add(ConditionBranch(elseIfCondition, elseIfActions, elseIfLoc))
            } else {
                // Plain 'else' - parse actions and break
                skipNewlines()
                val elseActions = parseConditionalActions()
                return ConditionalNode(
                    ifBranch = ifBranch,
                    elseIfBranches = elseIfBranches,
                    elseActions = elseActions,
                    location = loc,
                )
            }
        }

        return ConditionalNode(
            ifBranch = ifBranch,
            elseIfBranches = elseIfBranches,
            elseActions = null,
            location = loc,
        )
    }

    /**
     * Parse a condition that may include `and`/`or` operators.
     * Returns a single ConditionNode or a CompoundConditionNode.
     */
    private fun parseConditionWithLogicalOps(): ConditionNode? {
        val left = parseSimpleCondition() ?: return null

        skipWhitespace()
        val operatorText = current().value.lowercase()

        // Check for 'and' or 'or' operator
        if (operatorText == "and" || operatorText == "or") {
            val op = if (operatorText == "and") LogicalOperator.AND else LogicalOperator.OR
            advance() // consume 'and' or 'or'
            skipWhitespace()

            val right = parseConditionWithLogicalOps() ?: return null

            return ConditionNode.CompoundCondition(
                left = left,
                operator = op,
                right = right,
                location = left.location,
            )
        }

        return left
    }

    /**
     * Parse a simple condition (not including logical operators).
     */
    private fun parseSimpleCondition(): ConditionNode? {
        val loc = currentLocation()

        // Check for 'not' prefix for negation
        val negate =
            if (current().value.lowercase() == "not") {
                advance()
                skipWhitespace()
                true
            } else {
                false
            }

        val keyword = current().value.lowercase()

        return when {
            keyword == "status" || keyword == "statuscode" -> {
                advance()
                skipWhitespace()
                val expected = parseStatusValue() ?: return null
                val cond = ConditionNode.StatusCondition(expected, loc)
                if (negate) ConditionNode.NegatedCondition(cond, loc) else cond
            }
            keyword == "header" -> {
                advance()
                skipWhitespace()
                val headerName = parseHeaderName()
                skipWhitespace()
                val (op, expected) = parseConditionOperatorAndValue()
                val cond = ConditionNode.HeaderCondition(headerName, op, expected, loc)
                if (negate) ConditionNode.NegatedCondition(cond, loc) else cond
            }
            keyword.startsWith("\$") || current().type == TokenType.JSON_PATH -> {
                val path =
                    if (current().type == TokenType.JSON_PATH) {
                        current().value.also { advance() }
                    } else {
                        keyword.also { advance() }
                    }
                skipWhitespace()
                val (op, expected) = parseConditionOperatorAndValue()
                val cond = ConditionNode.JsonPathCondition(path, op, expected, loc)
                if (negate) ConditionNode.NegatedCondition(cond, loc) else cond
            }
            current().type == TokenType.IDENTIFIER || current().type == TokenType.VARIABLE -> {
                // Variable condition (e.g., test.type equals "invalid")
                val varName = buildVariablePath()
                skipWhitespace()
                val (op, expected) = parseConditionOperatorAndValue()
                val cond = ConditionNode.VariableCondition(varName, op, expected, loc)
                if (negate) ConditionNode.NegatedCondition(cond, loc) else cond
            }
            else -> {
                errors.add(ParseError("Expected condition (status, header, jsonpath, or variable)", loc))
                null
            }
        }
    }

    /**
     * Build a dotted variable path (e.g., test.type).
     */
    private fun buildVariablePath(): String {
        val parts = mutableListOf<String>()

        when (current().type) {
            TokenType.IDENTIFIER, TokenType.OPERATION_ID -> {
                parts.add(current().value)
                advance()
            }
            TokenType.VARIABLE -> {
                parts.add(current().value)
                advance()
                return parts.joinToString(".")
            }
            else -> return ""
        }

        while (!isAtEnd() && current().type == TokenType.DOT) {
            advance() // consume dot
            if (current().type == TokenType.IDENTIFIER || current().type == TokenType.OPERATION_ID) {
                parts.add(current().value)
                advance()
            } else {
                break
            }
        }

        return parts.joinToString(".")
    }

    /**
     * Parse a condition operator and expected value.
     */
    private fun parseConditionOperatorAndValue(): Pair<ConditionOperator, ValueNode?> {
        val opText = current().value.lowercase()

        val op =
            when (opText) {
                "equals", "=" -> {
                    advance()
                    skipWhitespace()
                    ConditionOperator.EQUALS
                }
                "contains" -> {
                    advance()
                    skipWhitespace()
                    ConditionOperator.CONTAINS
                }
                "matches" -> {
                    advance()
                    skipWhitespace()
                    ConditionOperator.MATCHES
                }
                "exists" -> {
                    advance()
                    return Pair(ConditionOperator.EXISTS, null)
                }
                "greaterthan", ">" -> {
                    advance()
                    skipWhitespace()
                    ConditionOperator.GREATER_THAN
                }
                "lessthan", "<" -> {
                    advance()
                    skipWhitespace()
                    ConditionOperator.LESS_THAN
                }
                else -> {
                    // Default to equals if no operator
                    ConditionOperator.EQUALS
                }
            }

        val expected = parseValue()
        return Pair(op, expected)
    }

    /**
     * Parse actions within a conditional block (same indent level).
     */
    private fun parseConditionalActions(): List<ActionNode> {
        val actions = mutableListOf<ActionNode>()

        if (current().type == TokenType.INDENT) {
            advance()

            while (!isAtEnd()) {
                when (current().type) {
                    TokenType.CALL -> parseCallAction()?.let { actions.add(it) }
                    TokenType.EXTRACT -> parseExtractAction()?.let { actions.add(it) }
                    TokenType.ASSERT -> parseAssertAction()?.let { actions.add(it) }
                    TokenType.INCLUDE -> parseIncludeAction()?.let { actions.add(it) }
                    TokenType.FAIL -> parseFailAction()?.let { actions.add(it) }
                    TokenType.NEWLINE -> advance()
                    TokenType.DEDENT,
                    TokenType.ELSE,
                    TokenType.IF,
                    TokenType.GIVEN,
                    TokenType.WHEN,
                    TokenType.THEN,
                    TokenType.AND,
                    TokenType.BUT,
                    TokenType.EOF,
                    -> break
                    else -> advance()
                }
            }

            if (current().type == TokenType.DEDENT) {
                advance()
            }
        }

        return actions
    }

    /**
     * Parse a fail action.
     *
     * Syntax: `fail "error message"` or `fail "message with {{variable}}"`
     */
    private fun parseFailAction(): FailNode? {
        val loc = currentLocation()
        advance() // consume 'fail'
        skipWhitespace()

        // Get the message
        val message =
            when (current().type) {
                TokenType.STRING -> {
                    val msg = current().value
                    advance()
                    msg
                }
                else -> {
                    // Collect tokens until newline as message
                    val parts = mutableListOf<String>()
                    while (!isAtEnd() && current().type != TokenType.NEWLINE && current().type != TokenType.DEDENT) {
                        parts.add(current().value)
                        advance()
                    }
                    parts.joinToString(" ")
                }
            }

        return FailNode(message, loc)
    }
}
