package org.berrycrush.scenario.parsing

import org.berrycrush.scenario.*

/**
 * Extension functions for parsing conditional blocks (if/else if/else).
 */

/**
 * Parse a conditional block (if/else if/else).
 */
internal fun ParserState.parseConditional(): ConditionalNode? {
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
 */
internal fun ParserState.parseConditionWithLogicalOps(): ConditionNode? {
    val left = parseSimpleCondition() ?: return null

    skipWhitespace()
    val operatorText = current().value.lowercase()

    if (operatorText == "and" || operatorText == "or") {
        val op = if (operatorText == "and") LogicalOperator.AND else LogicalOperator.OR
        advance()
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
internal fun ParserState.parseSimpleCondition(): ConditionNode? {
    val loc = currentLocation()

    val negate =
        if (current().value.lowercase() == "not") {
            advance()
            skipWhitespace()
            true
        } else {
            false
        }

    val keyword = current().value.lowercase()

    // Use unified condition parsing
    val result = parseCondition(keyword, loc, ConditionContext.CONDITIONAL, negate)
    if (result != null) {
        return result
    }

    // Handle variable conditions (only available in conditionals)
    return when {
        current().type == TokenType.IDENTIFIER || current().type == TokenType.VARIABLE -> {
            val varName = buildVariablePath()
            skipWhitespace()
            val (op, expected) = parseConditionOperatorAndValue()
            val cond = ConditionNode.VariableCondition(varName, op, expected, loc)
            if (negate) ConditionNode.NegatedCondition(cond, loc) else cond
        }
        else -> {
            addError("Expected condition (status, header, jsonpath, contains, schema, responseTime, or variable)")
            null
        }
    }
}

/**
 * Build a dotted variable path (e.g., test.type).
 */
internal fun ParserState.buildVariablePath(): String {
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
        advance()
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
 * Parse actions within a conditional block.
 */
internal fun ParserState.parseConditionalActions(): List<ActionNode> {
    val actions = mutableListOf<ActionNode>()

    if (current().type == TokenType.INDENT) {
        advance()

        while (!isAtEnd()) {
            when (current().type) {
                TokenType.CALL -> parseCallAction()?.let { actions.add(it) }
                TokenType.EXTRACT -> parseExtractAction()?.let { actions.add(it) }
                TokenType.ASSERT -> parseAssertAction()?.let { actions.add(it) }
                TokenType.INCLUDE -> parseIncludeAction()?.let { actions.add(it) }
                TokenType.FAIL -> actions.add(parseFailAction())
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
 */
internal fun ParserState.parseFailAction(): FailNode {
    val loc = currentLocation()
    advance() // consume 'fail'
    skipWhitespace()

    val message =
        when (current().type) {
            TokenType.STRING -> {
                val msg = current().value
                advance()
                msg
            }
            else -> {
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
