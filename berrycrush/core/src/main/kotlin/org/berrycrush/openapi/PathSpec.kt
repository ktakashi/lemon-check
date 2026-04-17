package org.berrycrush.openapi

/**
 * Represents a path item in the OpenAPI specification.
 *
 * A path item contains operations (HTTP methods) for a specific endpoint.
 */
interface PathSpec {
    /**
     * The path template (e.g., "/pets/{petId}").
     */
    val path: String

    /**
     * Operations available on this path, keyed by HTTP method.
     */
    val operations: Map<HttpMethod, OperationSpec>

    /**
     * Summary for the entire path item.
     */
    val summary: String?

    /**
     * Description for the entire path item.
     */
    val description: String?

    /**
     * Parameters common to all operations on this path.
     */
    val parameters: List<ParameterSpec>

    /**
     * Get an operation by HTTP method.
     */
    fun getOperation(method: HttpMethod): OperationSpec? = operations[method]
}

/**
 * Represents an API operation (HTTP method on a path).
 */
interface OperationSpec {
    /**
     * Unique operation identifier.
     */
    val operationId: String?

    /**
     * The path this operation belongs to.
     */
    val path: String

    /**
     * HTTP method for this operation.
     */
    val method: HttpMethod

    /**
     * Short summary of the operation.
     */
    val summary: String?

    /**
     * Detailed description of the operation.
     */
    val description: String?

    /**
     * Tags for grouping operations.
     */
    val tags: List<String>

    /**
     * Parameters for this operation (path, query, header, cookie).
     */
    val parameters: List<ParameterSpec>

    /**
     * Request body specification.
     */
    val requestBody: RequestBodySpec?

    /**
     * Response specifications keyed by status code.
     */
    val responses: Map<String, ResponseSpec>

    /**
     * Security requirements for this operation.
     */
    val security: List<Map<String, List<String>>>?

    /**
     * Whether this operation is deprecated.
     */
    val deprecated: Boolean

    /**
     * Callbacks for async operations.
     */
    val callbacks: Map<String, Map<String, PathSpec>>
}

/**
 * Parameter specification.
 */
interface ParameterSpec {
    /**
     * Parameter name.
     */
    val name: String

    /**
     * Location of the parameter.
     */
    val location: ParameterLocation

    /**
     * Description of the parameter.
     */
    val description: String?

    /**
     * Whether the parameter is required.
     */
    val required: Boolean

    /**
     * Whether the parameter is deprecated.
     */
    val deprecated: Boolean

    /**
     * Schema for the parameter value.
     */
    val schema: SchemaSpec?

    /**
     * Example value.
     */
    val example: Any?
}

/**
 * Parameter location.
 */
enum class ParameterLocation {
    PATH,
    QUERY,
    HEADER,
    COOKIE,
}

/**
 * Request body specification.
 */
interface RequestBodySpec {
    /**
     * Description of the request body.
     */
    val description: String?

    /**
     * Whether the request body is required.
     */
    val required: Boolean

    /**
     * Content types and their schemas.
     */
    val content: Map<String, MediaTypeSpec>
}

/**
 * Media type specification.
 */
interface MediaTypeSpec {
    /**
     * Schema for this media type.
     */
    val schema: SchemaSpec?

    /**
     * Example value.
     */
    val example: Any?

    /**
     * Named examples (3.1+).
     */
    val examples: Map<String, ExampleSpec>
}

/**
 * Example specification.
 */
interface ExampleSpec {
    /**
     * Summary of the example.
     */
    val summary: String?

    /**
     * Description of the example.
     */
    val description: String?

    /**
     * The example value.
     */
    val value: Any?

    /**
     * External URL for the example.
     */
    val externalValue: String?
}

/**
 * Response specification.
 */
interface ResponseSpec {
    /**
     * Status code for this response.
     */
    val statusCode: String

    /**
     * Description of the response.
     */
    val description: String?

    /**
     * Headers returned with the response.
     */
    val headers: Map<String, HeaderSpec>

    /**
     * Content types and their schemas.
     */
    val content: Map<String, MediaTypeSpec>
}

/**
 * Header specification.
 */
interface HeaderSpec {
    /**
     * Description of the header.
     */
    val description: String?

    /**
     * Whether the header is required.
     */
    val required: Boolean

    /**
     * Whether the header is deprecated.
     */
    val deprecated: Boolean

    /**
     * Schema for the header value.
     */
    val schema: SchemaSpec?
}
