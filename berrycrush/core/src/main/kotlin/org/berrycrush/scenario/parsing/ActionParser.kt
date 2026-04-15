package org.berrycrush.scenario.parsing

import org.berrycrush.scenario.*

/**
 * Extension functions for parsing actions (call, extract, assert, include).
 */

/**
 * Result of parsing body content.
 */
internal sealed class BodyParseResult {
    data class Raw(val value: ValueNode?) : BodyParseResult()
    data class Properties(val properties: Map<String, BodyPropertyValue>) : BodyParseResult()
}

/**
 * Parse a call action.
 */
internal fun ParserState.parseCallAction(): CallNode? {
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
        addError("Expected operation ID")
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
        } else {
            autoTestConfig
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
 * Parse body content (raw JSON, triple-quoted, or structured properties).
 */
internal fun ParserState.parseBodyContent(): BodyParseResult {
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
internal fun ParserState.parseBodyProperties(): Map<String, BodyPropertyValue> {
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
 */
internal fun ParserState.parseBodyFilePath(): String? {
    if (current().type == TokenType.STRING) {
        val value = current().value
        advance()
        return value
    }

    val sb = StringBuilder()
    while (!isAtEnd() && current().type != TokenType.NEWLINE && current().type != TokenType.DEDENT) {
        when (current().type) {
            TokenType.IDENTIFIER, TokenType.OPERATION_ID -> sb.append(current().value)
            TokenType.COLON -> sb.append(':')
            TokenType.DOT -> sb.append('.')
            TokenType.NUMBER -> sb.append(current().value)
            else -> {
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
 */
internal fun ParserState.parseAutoTestConfig(): AutoTestConfig? {
    val loc = currentLocation()
    val types = mutableSetOf<AutoTestType>()

    if (current().type != TokenType.OPEN_BRACKET) {
        // Parse as bare identifiers without brackets
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
        advance()
    }

    return if (types.isNotEmpty()) AutoTestConfig(types, emptySet(), loc) else null
}

/**
 * Parse auto test excludes configuration.
 */
internal fun ParserState.parseAutoTestExcludes(): Set<String> {
    val excludes = mutableSetOf<String>()

    if (current().type != TokenType.OPEN_BRACKET) {
        if (current().type == TokenType.IDENTIFIER || current().type == TokenType.OPERATION_ID) {
            excludes.add(current().value)
            advance()
        }
        return excludes
    }

    advance() // consume [

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
        advance()
    }

    return excludes
}

/**
 * Parse an extract action.
 */
internal fun ParserState.parseExtractAction(): ExtractNode? {
    val loc = currentLocation()
    advance() // consume 'extract'
    skipWhitespace()

    if (current().type != TokenType.JSON_PATH && current().type != TokenType.STRING) {
        addError("Expected JSON path")
        return null
    }
    val jsonPath = current().value
    advance()
    skipWhitespace()

    if (current().type != TokenType.ARROW) {
        addError("Expected '=>' or '->'")
        return null
    }
    advance()
    skipWhitespace()

    val current = current()
    val variableName =
        when (current.type) {
            TokenType.VARIABLE, TokenType.IDENTIFIER -> current.value
            else -> {
                addError("Expected variable name")
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

/**
 * Parse an include action.
 */
internal fun ParserState.parseIncludeAction(): IncludeNode? {
    val loc = currentLocation()
    advance() // consume 'include'
    skipWhitespace()

    if (current().type != TokenType.IDENTIFIER && current().type != TokenType.OPERATION_ID) {
        addError("Expected fragment name")
        return null
    }

    val fragmentName = current().value
    advance()

    return IncludeNode(
        fragmentName = fragmentName,
        location = loc,
    )
}
