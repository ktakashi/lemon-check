package org.berrycrush.openapi

/**
 * Reusable components defined in the OpenAPI specification.
 */
interface ComponentsSpec {
    /**
     * Reusable schemas.
     */
    val schemas: Map<String, SchemaSpec>

    /**
     * Reusable response definitions.
     */
    val responses: Map<String, ResponseSpec>

    /**
     * Reusable parameter definitions.
     */
    val parameters: Map<String, ParameterSpec>

    /**
     * Reusable example definitions.
     */
    val examples: Map<String, ExampleSpec>

    /**
     * Reusable request body definitions.
     */
    val requestBodies: Map<String, RequestBodySpec>

    /**
     * Reusable header definitions.
     */
    val headers: Map<String, HeaderSpec>

    /**
     * Security scheme definitions.
     */
    val securitySchemes: Map<String, SecuritySchemeSpec>

    /**
     * Reusable link definitions.
     */
    val links: Map<String, LinkSpec>

    /**
     * Reusable callback definitions.
     */
    val callbacks: Map<String, Map<String, PathSpec>>

    /**
     * Path items (3.1+ - reusable path items).
     */
    val pathItems: Map<String, PathSpec>
}

/**
 * Security scheme specification.
 */
interface SecuritySchemeSpec {
    /**
     * Security scheme type.
     */
    val type: SecuritySchemeType

    /**
     * Description of the security scheme.
     */
    val description: String?

    /**
     * Name of the header, query, or cookie parameter (for apiKey type).
     */
    val name: String?

    /**
     * Location of the API key (for apiKey type).
     */
    val location: ApiKeyLocation?

    /**
     * HTTP authorization scheme (for http type).
     */
    val scheme: String?

    /**
     * Bearer token format hint (for http type with bearer scheme).
     */
    val bearerFormat: String?

    /**
     * OAuth2 flows (for oauth2 type).
     */
    val flows: OAuthFlowsSpec?

    /**
     * OpenID Connect URL (for openIdConnect type).
     */
    val openIdConnectUrl: String?
}

/**
 * Security scheme types.
 */
enum class SecuritySchemeType {
    API_KEY,
    HTTP,
    OAUTH2,
    OPEN_ID_CONNECT,
    MUTUAL_TLS,
}

/**
 * API key location.
 */
enum class ApiKeyLocation {
    QUERY,
    HEADER,
    COOKIE,
}

/**
 * OAuth2 flows specification.
 */
interface OAuthFlowsSpec {
    val implicit: OAuthFlowSpec?
    val password: OAuthFlowSpec?
    val clientCredentials: OAuthFlowSpec?
    val authorizationCode: OAuthFlowSpec?
}

/**
 * OAuth2 flow specification.
 */
interface OAuthFlowSpec {
    val authorizationUrl: String?
    val tokenUrl: String?
    val refreshUrl: String?
    val scopes: Map<String, String>
}

/**
 * Link specification for HATEOAS.
 */
interface LinkSpec {
    /**
     * Reference to an operation by operationId.
     */
    val operationId: String?

    /**
     * Reference to an operation by relative reference.
     */
    val operationRef: String?

    /**
     * Parameters to pass to the linked operation.
     */
    val parameters: Map<String, Any>

    /**
     * Request body for the linked operation.
     */
    val requestBody: Any?

    /**
     * Description of the link.
     */
    val description: String?

    /**
     * Server to use for the linked operation.
     */
    val server: ServerInfo?
}
