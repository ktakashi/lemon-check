package org.berrycrush.openapi

import java.nio.file.Path

/**
 * Parser interface for OpenAPI specifications.
 *
 * Implementations convert raw OpenAPI documents (YAML/JSON) into
 * the unified [OpenApiSpec] abstraction.
 */
interface OpenApiParser {
    /**
     * Parse an OpenAPI spec from a file path.
     *
     * @param path Path to the OpenAPI spec file (YAML or JSON)
     * @return Parsed OpenAPI specification
     * @throws OpenApiParseException if parsing fails
     */
    fun parse(path: Path): OpenApiSpec

    /**
     * Parse an OpenAPI spec from a string path.
     *
     * @param path Path string to the OpenAPI spec file
     * @return Parsed OpenAPI specification
     * @throws OpenApiParseException if parsing fails
     */
    fun parse(path: String): OpenApiSpec

    /**
     * Parse an OpenAPI spec from content string.
     *
     * @param content OpenAPI spec content (YAML or JSON)
     * @return Parsed OpenAPI specification
     * @throws OpenApiParseException if parsing fails
     */
    fun parseContent(content: String): OpenApiSpec

    /**
     * Get the OpenAPI versions supported by this parser.
     */
    fun supportedVersions(): Set<OpenApiVersion>
}
