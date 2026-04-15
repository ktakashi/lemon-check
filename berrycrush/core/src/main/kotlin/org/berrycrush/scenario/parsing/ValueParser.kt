package org.berrycrush.scenario.parsing

import org.berrycrush.scenario.*

/**
 * Extension functions for parsing values, JSON objects, and status codes.
 */

/**
 * Parse a value (string, number, variable, JSON object, or JSON array).
 */
internal fun ParserState.parseValue(): ValueNode? {
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

/**
 * Parse a status value (number, pattern like 2xx, or variable).
 */
internal fun ParserState.parseStatusValue(): ValueNode? {
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

    return parseValue()
}

/**
 * Parse a JSON object.
 */
internal fun ParserState.parseJsonObject(loc: SourceLocation): JsonValueNode {
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

/**
 * Parse a JSON array.
 */
internal fun ParserState.parseJsonArray(loc: SourceLocation): JsonValueNode {
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
