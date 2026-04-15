package org.berrycrush.scenario.parsing

import org.berrycrush.scenario.*

/**
 * Extension functions for parsing scenario-level constructs.
 *
 * Includes: tags, scenarios, outlines, features, fragments, background, parameters
 */

/**
 * Parse tags preceding a scenario or feature.
 * Syntax: @tag1 @tag2 @tag3
 */
internal fun ParserState.parseTags(): Set<String> {
    val tags = mutableSetOf<String>()

    while (!isAtEnd() && current().type == TokenType.TAG) {
        tags.add(current().value)
        advance()
        skipWhitespace()
        skipNewlines()
    }

    return tags
}

/**
 * Parse a scenario definition.
 */
internal fun ParserState.parseScenario(tags: Set<String> = emptySet()): ScenarioNode? {
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

/**
 * Parse a scenario outline with examples.
 */
internal fun ParserState.parseScenarioOutline(tags: Set<String> = emptySet()): ScenarioNode? {
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
 */
internal fun ParserState.parseFeature(tags: Set<String> = emptySet()): FeatureNode? {
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

    var parameters: ParametersNode? = null
    var background: BackgroundNode? = null
    val scenarios = mutableListOf<ScenarioNode>()

    while (!isAtEnd() && current().type != TokenType.DEDENT && current().type != TokenType.FEATURE) {
        skipNewlines()
        if (isAtEnd() || current().type == TokenType.DEDENT || current().type == TokenType.FEATURE) break

        // Parse tags for nested scenarios
        val scenarioTags = parseTags()

        when (current().type) {
            TokenType.PARAMETERS -> {
                if (scenarios.isNotEmpty()) {
                    addError(
                        "Feature parameters must appear before scenarios",
                        expected = "scenario or outline",
                        found = "parameters",
                    )
                }
                parameters = parseParameters()
            }
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
                    addError(
                        "Unexpected token in feature",
                        expected = "parameters, background, scenario, or outline",
                        found = current().value,
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
        parameters = parameters,
        background = background,
        scenarios = scenarios,
        tags = tags,
        location = loc,
    )
}

/**
 * Parse background steps shared by all scenarios in a feature.
 */
internal fun ParserState.parseBackground(): BackgroundNode? {
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

/**
 * Parse a fragment definition.
 */
internal fun ParserState.parseFragment(): FragmentNode? {
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
 * Parse file-level or feature-level parameters block.
 */
internal fun ParserState.parseParameters(): ParametersNode? {
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
        current().type != TokenType.PARAMETERS &&
        current().type != TokenType.BACKGROUND &&
        current().type != TokenType.FEATURE
    ) {
        if (current().type == TokenType.NEWLINE) {
            advance()
            continue
        }

        val paramName = parseParameterName() ?: break
        skipWhitespace()

        if (!expect(TokenType.COLON)) {
            addError("Expected ':' after parameter name")
            break
        }
        skipWhitespace()

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
 */
internal fun ParserState.parseParameterName(): String? {
    if (current().type != TokenType.IDENTIFIER) {
        return null
    }

    val parts = StringBuilder(current().value)
    advance()

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
                parts.append(current().value)
                advance()
            }
            TokenType.ERROR -> {
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
internal fun ParserState.parseParameterValue(): Any? =
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
            when (value.lowercase()) {
                "true" -> true
                "false" -> false
                else -> value
            }
        }
        else -> {
            addError("Expected parameter value")
            null
        }
    }

/**
 * Parse a scenario or fragment name.
 */
internal fun ParserState.parseScenarioName(): String? {
    val nameParts = mutableListOf<String>()

    while (!isAtEnd() && current().type != TokenType.NEWLINE && current().type != TokenType.EOF) {
        nameParts.add(current().value)
        advance()
    }

    if (nameParts.isEmpty()) {
        addError("Expected scenario name")
        return null
    }

    return nameParts.joinToString(" ").trim()
}

/**
 * Parse a fragment name (alias for parseScenarioName).
 */
internal fun ParserState.parseFragmentName(): String? = parseScenarioName()
