package org.berrycrush.scenario.parsing

import org.berrycrush.scenario.*

/**
 * Extension functions for parsing steps and step descriptions.
 */

/**
 * Parse steps within a scenario or background block.
 */
internal fun ParserState.parseSteps(): List<StepNode> {
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

/**
 * Parse a single step with its keyword and actions.
 */
internal fun ParserState.parseStep(keyword: StepKeyword): StepNode {
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

/**
 * Parse step description text.
 * Preserves quotes for STRING tokens so custom step matchers can extract parameters.
 */
internal fun ParserState.parseStepDescription(): String {
    val parts = mutableListOf<String>()

    while (!isAtEnd() && current().type != TokenType.NEWLINE && current().type != TokenType.EOF) {
        // Preserve quotes for STRING tokens so custom step matchers can extract parameters
        val tokenValue =
            if (current().type == TokenType.STRING) {
                "\"${current().value}\""
            } else {
                current().value
            }
        parts.add(tokenValue)
        advance()
    }

    return parts.joinToString(" ").trim()
}

/**
 * Parse actions within a step.
 */
internal fun ParserState.parseActions(): List<ActionNode> {
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
                TokenType.FAIL -> actions.add(parseFailAction())
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
