package io.github.ktakashi.lemoncheck.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import java.nio.file.Path

/**
 * Loads and parses OpenAPI specifications.
 */
class OpenApiLoader {
    private val parser = OpenAPIV3Parser()

    /**
     * Load an OpenAPI spec from a file path.
     *
     * @param path Path to the OpenAPI spec file (YAML or JSON)
     * @return Parsed OpenAPI model
     * @throws OpenApiParseException if parsing fails
     */
    fun load(path: String): OpenAPI {
        val parseOptions =
            ParseOptions().apply {
                isResolve = true
                isResolveFully = true
            }

        val result = parser.readLocation(path, null, parseOptions)

        if (result.openAPI == null) {
            val errors = result.messages?.joinToString("\n") ?: "Unknown error"
            throw OpenApiParseException("Failed to parse OpenAPI spec at $path: $errors")
        }

        return result.openAPI
    }

    /**
     * Load an OpenAPI spec from a Path.
     */
    fun load(path: Path): OpenAPI = load(path.toString())

    /**
     * Load an OpenAPI spec from content string.
     *
     * @param content OpenAPI spec content (YAML or JSON)
     * @return Parsed OpenAPI model
     */
    fun loadFromString(content: String): OpenAPI {
        val parseOptions =
            ParseOptions().apply {
                isResolve = true
                isResolveFully = true
            }

        val result = parser.readContents(content, null, parseOptions)

        if (result.openAPI == null) {
            val errors = result.messages?.joinToString("\n") ?: "Unknown error"
            throw OpenApiParseException("Failed to parse OpenAPI spec: $errors")
        }

        return result.openAPI
    }
}

/**
 * Exception thrown when OpenAPI parsing fails.
 */
class OpenApiParseException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
