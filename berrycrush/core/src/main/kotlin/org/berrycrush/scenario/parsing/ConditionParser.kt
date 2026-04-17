package org.berrycrush.scenario.parsing

import org.berrycrush.scenario.AssertNode
import org.berrycrush.scenario.ConditionNode
import org.berrycrush.scenario.ConditionOperator
import org.berrycrush.scenario.ParserState
import org.berrycrush.scenario.SourceLocation
import org.berrycrush.scenario.TokenType
import org.berrycrush.scenario.ValueNode

/**
 * Context for condition parsing - affects parsing style for some conditions.
 */
internal enum class ConditionContext {
    /** Parsing for assertion */
    ASSERT,

    /** Parsing for if/else condition */
    CONDITIONAL,
}

/**
 * Parse an assertion action.
 */
internal fun ParserState.parseAssertAction(): AssertNode? {
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

    val condition = parseAssertCondition(loc, negate) ?: return null

    return AssertNode(
        condition = condition,
        location = loc,
    )
}

/**
 * Apply negation to a condition if needed.
 */
internal fun applyNegation(
    condition: ConditionNode,
    negate: Boolean,
    loc: SourceLocation,
): ConditionNode = if (negate) ConditionNode.NegatedCondition(condition, loc) else condition

/**
 * Parse a condition. All condition types are available in both assert and if contexts.
 */
internal fun ParserState.parseCondition(
    keyword: String,
    loc: SourceLocation,
    context: ConditionContext,
    negate: Boolean,
): ConditionNode? =
    when {
        keyword == "status" || keyword == "statuscode" -> {
            advance()
            skipWhitespace()
            parseStatusValue()?.let { expected ->
                applyNegation(ConditionNode.StatusCondition(expected, loc), negate, loc)
            }
        }
        keyword == "schema" || keyword == "matchesschema" -> {
            advance()
            skipWhitespace()
            applyNegation(ConditionNode.SchemaCondition(loc), negate, loc)
        }
        keyword == "header" -> {
            advance()
            skipWhitespace()
            val headerName = parseHeaderName()
            skipWhitespace()

            val cond =
                if (context == ConditionContext.ASSERT) {
                    if (current().type == TokenType.EQUALS || current().type == TokenType.COLON) {
                        advance()
                        skipWhitespace()
                        ConditionNode.HeaderCondition(headerName, ConditionOperator.EQUALS, parseValue(), loc)
                    } else {
                        ConditionNode.HeaderCondition(headerName, ConditionOperator.EXISTS, null, loc)
                    }
                } else {
                    val (op, expected) = parseConditionOperatorAndValue()
                    ConditionNode.HeaderCondition(headerName, op, expected, loc)
                }
            applyNegation(cond, negate, loc)
        }
        keyword == "contains" || keyword == "bodycontains" -> {
            advance()
            skipWhitespace()
            parseValue()?.let { text ->
                applyNegation(ConditionNode.BodyContainsCondition(text, loc), negate, loc)
            }
        }
        keyword == "responsetime" -> {
            advance()
            skipWhitespace()
            parseValue()?.let { maxMs ->
                applyNegation(ConditionNode.ResponseTimeCondition(maxMs, loc), negate, loc)
            }
        }
        keyword.startsWith("$") || current().type == TokenType.JSON_PATH -> {
            parseJsonPathCondition(keyword, loc, context, negate)
        }
        else -> null
    }

/**
 * Parse a JSON path condition with operator validation for assertions.
 */
internal fun ParserState.parseJsonPathCondition(
    keyword: String,
    loc: SourceLocation,
    context: ConditionContext,
    initialNegate: Boolean,
): ConditionNode? {
    val path =
        if (current().type == TokenType.JSON_PATH) {
            current().value.also { advance() }
        } else {
            keyword.also { advance() }
        }
    skipWhitespace()

    // Check for "not" after the JSON path (e.g., "$.name not equals ...")
    val negate =
        if (current().value.lowercase() == "not") {
            advance()
            skipWhitespace()
            true
        } else {
            initialNegate
        }

    // For assertions, validate operators
    if (context == ConditionContext.ASSERT) {
        val operatorText = current().value.lowercase()
        val validOperators =
            setOf(
                "equals",
                "=",
                "matches",
                "exists",
                "hassize",
                "size",
                "arraysize",
                "notempty",
                "contains",
                "greaterthan",
                ">",
                "lessthan",
                "<",
                "in",
            )

        if (!validOperators.contains(operatorText) &&
            current().type != TokenType.EQUALS &&
            current().type != TokenType.NEWLINE &&
            current().type != TokenType.DEDENT
        ) {
            addError(
                "Unknown assertion action '$operatorText' for JSON path. " +
                    "Expected: equals, matches, exists, hasSize, size, arraySize, notEmpty, contains, greaterThan, lessThan, or in",
            )
            return null
        }
    }

    val (op, expected) = parseConditionOperatorAndValue()
    return applyNegation(ConditionNode.JsonPathCondition(path, op, expected, loc), negate, loc)
}

/**
 * Parse a condition for an assertion.
 * If no built-in condition matches, treats the text as a custom assertion pattern.
 */
internal fun ParserState.parseAssertCondition(
    loc: SourceLocation,
    initialNegate: Boolean,
): ConditionNode? {
    val typeOrPath = current().value.lowercase()

    // Use unified condition parsing for built-in conditions
    val result = parseCondition(typeOrPath, loc, ConditionContext.ASSERT, initialNegate)
    if (result != null) {
        return result
    }

    // No built-in condition matched - treat as custom assertion pattern
    val patternBuilder = StringBuilder()
    while (!isAtEnd() && current().type != TokenType.NEWLINE && current().type != TokenType.EOF) {
        if (patternBuilder.isNotEmpty()) {
            patternBuilder.append(" ")
        }
        // Preserve quotes for STRING tokens so custom assertion matchers can extract parameters
        val tokenValue =
            if (current().type == TokenType.STRING) {
                "\"${current().value}\""
            } else {
                current().value
            }
        patternBuilder.append(tokenValue)
        advance()
    }

    val pattern = patternBuilder.toString().trim()
    if (pattern.isEmpty()) {
        addError("Empty assertion pattern")
        return null
    }

    val condition = ConditionNode.CustomAssertionCondition(pattern, loc)
    return if (initialNegate) {
        ConditionNode.NegatedCondition(condition, loc)
    } else {
        condition
    }
}

/**
 * Parse a condition operator and expected value.
 */
internal fun ParserState.parseConditionOperatorAndValue(): Pair<ConditionOperator, ValueNode?> {
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
            "hassize", "size", "arraysize" -> {
                advance()
                skipWhitespace()
                ConditionOperator.HAS_SIZE
            }
            "notempty" -> {
                advance()
                return Pair(ConditionOperator.NOT_EMPTY, null)
            }
            "in" -> {
                advance()
                skipWhitespace()
                ConditionOperator.CONTAINS
            }
            else -> {
                ConditionOperator.EQUALS
            }
        }

    val expected = parseValue()
    return Pair(op, expected)
}

/**
 * Parse a header name which may contain hyphens.
 */
internal fun ParserState.parseHeaderName(): String {
    val sb = StringBuilder()

    if (current().type == TokenType.IDENTIFIER || current().type == TokenType.OPERATION_ID) {
        sb.append(current().value)
        advance()
    }

    while (!isAtEnd() && current().value == "-") {
        sb.append("-")
        advance()

        if (current().type == TokenType.IDENTIFIER || current().type == TokenType.OPERATION_ID) {
            sb.append(current().value)
            advance()
        } else {
            break
        }
    }

    return sb.toString()
}
