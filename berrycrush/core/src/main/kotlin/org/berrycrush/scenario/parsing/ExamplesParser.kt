package org.berrycrush.scenario.parsing

import org.berrycrush.scenario.ExampleRowNode
import org.berrycrush.scenario.ParserState
import org.berrycrush.scenario.TokenType
import org.berrycrush.scenario.ValueNode

/**
 * Parse examples table.
 */
internal fun ParserState.parseExamples(): List<ExampleRowNode>? {
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

/**
 * Parse a single example row.
 */
internal fun ParserState.parseExampleRow(headers: List<String>): ExampleRowNode? {
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
