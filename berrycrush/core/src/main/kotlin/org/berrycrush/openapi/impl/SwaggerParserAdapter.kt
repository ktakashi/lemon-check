package org.berrycrush.openapi.impl

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.callbacks.Callback
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.headers.Header
import io.swagger.v3.oas.models.links.Link
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.parser.OpenAPIV3Parser
import io.swagger.v3.parser.core.models.ParseOptions
import org.berrycrush.openapi.ApiKeyLocation
import org.berrycrush.openapi.ComponentsSpec
import org.berrycrush.openapi.ContactInfo
import org.berrycrush.openapi.DiscriminatorSpec
import org.berrycrush.openapi.ExampleSpec
import org.berrycrush.openapi.ExternalDocsSpec
import org.berrycrush.openapi.HeaderSpec
import org.berrycrush.openapi.HttpMethod
import org.berrycrush.openapi.LicenseInfo
import org.berrycrush.openapi.LinkSpec
import org.berrycrush.openapi.MediaTypeSpec
import org.berrycrush.openapi.OAuthFlowSpec
import org.berrycrush.openapi.OAuthFlowsSpec
import org.berrycrush.openapi.OpenApiParseException
import org.berrycrush.openapi.OpenApiParser
import org.berrycrush.openapi.OpenApiSpec
import org.berrycrush.openapi.OpenApiVersion
import org.berrycrush.openapi.OperationSpec
import org.berrycrush.openapi.ParameterLocation
import org.berrycrush.openapi.ParameterSpec
import org.berrycrush.openapi.PathSpec
import org.berrycrush.openapi.RequestBodySpec
import org.berrycrush.openapi.ResponseSpec
import org.berrycrush.openapi.SchemaSpec
import org.berrycrush.openapi.SecuritySchemeSpec
import org.berrycrush.openapi.SecuritySchemeType
import org.berrycrush.openapi.ServerInfo
import org.berrycrush.openapi.ServerVariable
import org.berrycrush.openapi.SpecInfo
import org.berrycrush.openapi.XmlSpec
import java.nio.file.Path

/**
 * OpenAPI parser implementation using swagger-parser library.
 *
 * Supports OpenAPI 2.0 (Swagger), 3.0.x, and 3.1.x specifications.
 */
class SwaggerParserAdapter : OpenApiParser {
    private val parser = OpenAPIV3Parser()

    override fun parse(path: Path): OpenApiSpec = parse(path.toString())

    override fun parse(path: String): OpenApiSpec {
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

        return SwaggerOpenApiSpec(result.openAPI)
    }

    override fun parseContent(content: String): OpenApiSpec {
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

        return SwaggerOpenApiSpec(result.openAPI)
    }

    override fun supportedVersions(): Set<OpenApiVersion> =
        setOf(
            OpenApiVersion.V2_X,
            OpenApiVersion.V3_0_X,
            OpenApiVersion.V3_1_X,
        )
}

/**
 * OpenApiSpec implementation wrapping swagger-parser's OpenAPI model.
 */
internal class SwaggerOpenApiSpec(
    private val openApi: OpenAPI,
) : OpenApiSpec {
    override val version: OpenApiVersion by lazy {
        OpenApiVersion.detect(openApi.openapi)
    }

    override val specVersion: String
        get() = openApi.openapi ?: "unknown"

    override val info: SpecInfo by lazy {
        val i = openApi.info
        SpecInfo(
            title = i?.title ?: "",
            description = i?.description,
            version = i?.version ?: "",
            contact =
                i?.contact?.let { c ->
                    ContactInfo(
                        name = c.name,
                        url = c.url,
                        email = c.email,
                    )
                },
            license =
                i?.license?.let { l ->
                    LicenseInfo(
                        name = l.name ?: "",
                        url = l.url,
                        identifier = l.identifier,
                    )
                },
        )
    }

    override val servers: List<ServerInfo> by lazy {
        openApi.servers?.map { s ->
            ServerInfo(
                url = s.url ?: "",
                description = s.description,
                variables =
                    s.variables?.mapValues { (_, v) ->
                        ServerVariable(
                            default = v.default ?: "",
                            enum = v.enum,
                            description = v.description,
                        )
                    } ?: emptyMap(),
            )
        } ?: emptyList()
    }

    override val paths: Map<String, PathSpec> by lazy {
        openApi.paths?.mapValues { (path, pathItem) ->
            SwaggerPathSpec(path, pathItem)
        } ?: emptyMap()
    }

    override val components: ComponentsSpec? by lazy {
        openApi.components?.let { SwaggerComponentsSpec(it) }
    }

    override val webhooks: Map<String, PathSpec> by lazy {
        openApi.webhooks?.mapValues { (name, pathItem) ->
            SwaggerPathSpec(name, pathItem)
        } ?: emptyMap()
    }

    override fun getOperation(operationId: String): OperationSpec? = getAllOperations().find { it.operationId == operationId }

    override fun getAllOperations(): List<OperationSpec> = paths.values.flatMap { it.operations.values }

    override val rawModel: Any
        get() = openApi
}

/**
 * PathSpec implementation wrapping swagger-parser's PathItem.
 */
internal class SwaggerPathSpec(
    override val path: String,
    private val pathItem: PathItem,
) : PathSpec {
    override val operations: Map<HttpMethod, OperationSpec> by lazy {
        buildMap {
            pathItem.get?.let { put(HttpMethod.GET, SwaggerOperationSpec(path, HttpMethod.GET, it, pathItem.parameters)) }
            pathItem.post?.let { put(HttpMethod.POST, SwaggerOperationSpec(path, HttpMethod.POST, it, pathItem.parameters)) }
            pathItem.put?.let { put(HttpMethod.PUT, SwaggerOperationSpec(path, HttpMethod.PUT, it, pathItem.parameters)) }
            pathItem.delete?.let { put(HttpMethod.DELETE, SwaggerOperationSpec(path, HttpMethod.DELETE, it, pathItem.parameters)) }
            pathItem.patch?.let { put(HttpMethod.PATCH, SwaggerOperationSpec(path, HttpMethod.PATCH, it, pathItem.parameters)) }
            pathItem.head?.let { put(HttpMethod.HEAD, SwaggerOperationSpec(path, HttpMethod.HEAD, it, pathItem.parameters)) }
            pathItem.options?.let { put(HttpMethod.OPTIONS, SwaggerOperationSpec(path, HttpMethod.OPTIONS, it, pathItem.parameters)) }
        }
    }

    override val summary: String?
        get() = pathItem.summary

    override val description: String?
        get() = pathItem.description

    override val parameters: List<ParameterSpec> by lazy {
        pathItem.parameters?.map { SwaggerParameterSpec(it) } ?: emptyList()
    }
}

/**
 * OperationSpec implementation wrapping swagger-parser's Operation.
 */
internal class SwaggerOperationSpec(
    override val path: String,
    override val method: HttpMethod,
    private val operation: Operation,
    private val pathParameters: List<Parameter>?,
) : OperationSpec {
    override val operationId: String?
        get() = operation.operationId

    override val summary: String?
        get() = operation.summary

    override val description: String?
        get() = operation.description

    override val tags: List<String>
        get() = operation.tags ?: emptyList()

    override val parameters: List<ParameterSpec> by lazy {
        val params = mutableListOf<ParameterSpec>()
        pathParameters?.forEach { params.add(SwaggerParameterSpec(it)) }
        operation.parameters?.forEach { params.add(SwaggerParameterSpec(it)) }
        params
    }

    override val requestBody: RequestBodySpec? by lazy {
        operation.requestBody?.let { SwaggerRequestBodySpec(it) }
    }

    override val responses: Map<String, ResponseSpec> by lazy {
        operation.responses?.mapValues { (code, response) ->
            SwaggerResponseSpec(code, response)
        } ?: emptyMap()
    }

    override val security: List<Map<String, List<String>>>? by lazy {
        operation.security?.map { requirement ->
            requirement.mapValues { it.value ?: emptyList() }
        }
    }

    override val deprecated: Boolean
        get() = operation.deprecated == true

    override val callbacks: Map<String, Map<String, PathSpec>> by lazy {
        operation.callbacks?.mapValues { (_, callback) ->
            convertCallback(callback)
        } ?: emptyMap()
    }

    private fun convertCallback(callback: Callback): Map<String, PathSpec> =
        callback.mapValues { (expr, pathItem) ->
            SwaggerPathSpec(expr, pathItem)
        }
}

/**
 * ParameterSpec implementation.
 */
internal class SwaggerParameterSpec(
    private val param: Parameter,
) : ParameterSpec {
    override val name: String
        get() = param.name ?: ""

    override val location: ParameterLocation
        get() =
            when (param.`in`) {
                "path" -> ParameterLocation.PATH
                "query" -> ParameterLocation.QUERY
                "header" -> ParameterLocation.HEADER
                "cookie" -> ParameterLocation.COOKIE
                else -> ParameterLocation.QUERY
            }

    override val description: String?
        get() = param.description

    override val required: Boolean
        get() = param.required == true

    override val deprecated: Boolean
        get() = param.deprecated == true

    override val schema: SchemaSpec? by lazy {
        param.schema?.let { SwaggerSchemaSpec(it) }
    }

    override val example: Any?
        get() = param.example
}

/**
 * RequestBodySpec implementation.
 */
internal class SwaggerRequestBodySpec(
    private val requestBody: RequestBody,
) : RequestBodySpec {
    override val description: String?
        get() = requestBody.description

    override val required: Boolean
        get() = requestBody.required == true

    override val content: Map<String, MediaTypeSpec> by lazy {
        convertContent(requestBody.content)
    }
}

/**
 * ResponseSpec implementation.
 */
internal class SwaggerResponseSpec(
    override val statusCode: String,
    private val response: ApiResponse,
) : ResponseSpec {
    override val description: String?
        get() = response.description

    override val headers: Map<String, HeaderSpec> by lazy {
        response.headers?.mapValues { (_, h) ->
            SwaggerHeaderSpec(h)
        } ?: emptyMap()
    }

    override val content: Map<String, MediaTypeSpec> by lazy {
        convertContent(response.content)
    }
}

/**
 * HeaderSpec implementation.
 */
internal class SwaggerHeaderSpec(
    private val header: Header,
) : HeaderSpec {
    override val description: String?
        get() = header.description

    override val required: Boolean
        get() = header.required == true

    override val deprecated: Boolean
        get() = header.deprecated == true

    override val schema: SchemaSpec? by lazy {
        header.schema?.let { SwaggerSchemaSpec(it) }
    }
}

/**
 * MediaTypeSpec implementation.
 */
internal class SwaggerMediaTypeSpec(
    private val mediaType: MediaType,
) : MediaTypeSpec {
    override val schema: SchemaSpec? by lazy {
        mediaType.schema?.let { SwaggerSchemaSpec(it) }
    }

    override val example: Any?
        get() = mediaType.example

    override val examples: Map<String, ExampleSpec> by lazy {
        mediaType.examples?.mapValues { (_, e) ->
            SwaggerExampleSpec(e)
        } ?: emptyMap()
    }
}

/**
 * ExampleSpec implementation.
 */
internal class SwaggerExampleSpec(
    private val example: Example,
) : ExampleSpec {
    override val summary: String?
        get() = example.summary

    override val description: String?
        get() = example.description

    override val value: Any?
        get() = example.value

    override val externalValue: String?
        get() = example.externalValue
}

/**
 * Convert Content to MediaTypeSpec map.
 */
private fun convertContent(content: Content?): Map<String, MediaTypeSpec> =
    content?.mapValues { (_, mt) ->
        SwaggerMediaTypeSpec(mt)
    } ?: emptyMap()

/**
 * SchemaSpec implementation.
 */
internal class SwaggerSchemaSpec(
    private val schema: Schema<*>,
) : SchemaSpec {
    override val types: List<String> by lazy {
        schema.types?.toList() ?: listOfNotNull(schema.type)
    }

    override val nullable: Boolean by lazy {
        // 3.1.x: check if "null" is in types
        // 3.0.x: check nullable flag
        types.contains("null") || schema.nullable == true
    }

    override val format: String?
        get() = schema.format

    override val title: String?
        get() = schema.title

    override val description: String?
        get() = schema.description

    override val default: Any?
        get() = schema.default

    override val example: Any?
        get() = schema.example

    override val examples: List<Any> by lazy {
        schema.examples ?: emptyList()
    }

    override val enum: List<Any>?
        get() = schema.enum

    override val ref: String?
        get() = schema.`$ref`

    override val minLength: Int?
        get() = schema.minLength

    override val maxLength: Int?
        get() = schema.maxLength

    override val pattern: String?
        get() = schema.pattern

    override val contentMediaType: String?
        get() = schema.contentMediaType

    override val contentEncoding: String?
        get() = schema.contentEncoding

    override val minimum: Number?
        get() = schema.minimum

    override val maximum: Number?
        get() = schema.maximum

    override val exclusiveMinimum: Number?
        get() = schema.exclusiveMinimumValue

    override val exclusiveMaximum: Number?
        get() = schema.exclusiveMaximumValue

    override val multipleOf: Number?
        get() = schema.multipleOf

    override val minItems: Int?
        get() = schema.minItems

    override val maxItems: Int?
        get() = schema.maxItems

    override val uniqueItems: Boolean?
        get() = schema.uniqueItems

    override val items: SchemaSpec? by lazy {
        schema.items?.let { SwaggerSchemaSpec(it) }
    }

    override val prefixItems: List<SchemaSpec>? by lazy {
        schema.prefixItems?.map { SwaggerSchemaSpec(it) }
    }

    override val contains: SchemaSpec? by lazy {
        schema.contains?.let { SwaggerSchemaSpec(it) }
    }

    override val minContains: Int?
        get() = schema.minContains

    override val maxContains: Int?
        get() = schema.maxContains

    override val properties: Map<String, SchemaSpec>? by lazy {
        schema.properties?.mapValues { (_, s) -> SwaggerSchemaSpec(s) }
    }

    override val required: List<String>
        get() = schema.required ?: emptyList()

    override val additionalProperties: SchemaSpec? by lazy {
        when (val ap = schema.additionalProperties) {
            is Schema<*> -> SwaggerSchemaSpec(ap)
            else -> null
        }
    }

    override val minProperties: Int?
        get() = schema.minProperties

    override val maxProperties: Int?
        get() = schema.maxProperties

    override val propertyNames: SchemaSpec? by lazy {
        schema.propertyNames?.let { SwaggerSchemaSpec(it) }
    }

    override val unevaluatedProperties: Any?
        get() = schema.unevaluatedProperties

    override val allOf: List<SchemaSpec>? by lazy {
        schema.allOf?.map { SwaggerSchemaSpec(it) }
    }

    override val anyOf: List<SchemaSpec>? by lazy {
        schema.anyOf?.map { SwaggerSchemaSpec(it) }
    }

    override val oneOf: List<SchemaSpec>? by lazy {
        schema.oneOf?.map { SwaggerSchemaSpec(it) }
    }

    override val not: SchemaSpec? by lazy {
        schema.not?.let { SwaggerSchemaSpec(it) }
    }

    override val `if`: SchemaSpec? by lazy {
        schema.`if`?.let { SwaggerSchemaSpec(it) }
    }

    override val then: SchemaSpec? by lazy {
        schema.then?.let { SwaggerSchemaSpec(it) }
    }

    override val `else`: SchemaSpec? by lazy {
        schema.`else`?.let { SwaggerSchemaSpec(it) }
    }

    override val const: Any?
        get() = schema.const

    override val readOnly: Boolean
        get() = schema.readOnly == true

    override val writeOnly: Boolean
        get() = schema.writeOnly == true

    override val deprecated: Boolean
        get() = schema.deprecated == true

    override val discriminator: DiscriminatorSpec? by lazy {
        schema.discriminator?.let { d ->
            DiscriminatorSpec(
                propertyName = d.propertyName ?: "",
                mapping = d.mapping ?: emptyMap(),
            )
        }
    }

    override val externalDocs: ExternalDocsSpec? by lazy {
        schema.externalDocs?.let { e ->
            ExternalDocsSpec(
                url = e.url ?: "",
                description = e.description,
            )
        }
    }

    override val xml: XmlSpec? by lazy {
        schema.xml?.let { x ->
            XmlSpec(
                name = x.name,
                namespace = x.namespace,
                prefix = x.prefix,
                attribute = x.attribute == true,
                wrapped = x.wrapped == true,
            )
        }
    }

    override val rawSchema: Any
        get() = schema
}

/**
 * ComponentsSpec implementation.
 */
internal class SwaggerComponentsSpec(
    private val components: io.swagger.v3.oas.models.Components,
) : ComponentsSpec {
    override val schemas: Map<String, SchemaSpec> by lazy {
        components.schemas?.mapValues { (_, s) ->
            SwaggerSchemaSpec(s)
        } ?: emptyMap()
    }

    override val responses: Map<String, ResponseSpec> by lazy {
        components.responses?.mapValues { (code, r) ->
            SwaggerResponseSpec(code, r)
        } ?: emptyMap()
    }

    override val parameters: Map<String, ParameterSpec> by lazy {
        components.parameters?.mapValues { (_, p) ->
            SwaggerParameterSpec(p)
        } ?: emptyMap()
    }

    override val examples: Map<String, ExampleSpec> by lazy {
        components.examples?.mapValues { (_, e) ->
            SwaggerExampleSpec(e)
        } ?: emptyMap()
    }

    override val requestBodies: Map<String, RequestBodySpec> by lazy {
        components.requestBodies?.mapValues { (_, r) ->
            SwaggerRequestBodySpec(r)
        } ?: emptyMap()
    }

    override val headers: Map<String, HeaderSpec> by lazy {
        components.headers?.mapValues { (_, h) ->
            SwaggerHeaderSpec(h)
        } ?: emptyMap()
    }

    override val securitySchemes: Map<String, SecuritySchemeSpec> by lazy {
        components.securitySchemes?.mapValues { (_, s) ->
            SwaggerSecuritySchemeSpec(s)
        } ?: emptyMap()
    }

    override val links: Map<String, LinkSpec> by lazy {
        components.links?.mapValues { (_, l) ->
            SwaggerLinkSpec(l)
        } ?: emptyMap()
    }

    override val callbacks: Map<String, Map<String, PathSpec>> by lazy {
        components.callbacks?.mapValues { (_, callback) ->
            callback.mapValues { (expr, pathItem) ->
                SwaggerPathSpec(expr, pathItem)
            }
        } ?: emptyMap()
    }

    override val pathItems: Map<String, PathSpec> by lazy {
        components.pathItems?.mapValues { (name, pathItem) ->
            SwaggerPathSpec(name, pathItem)
        } ?: emptyMap()
    }
}

/**
 * SecuritySchemeSpec implementation.
 */
internal class SwaggerSecuritySchemeSpec(
    private val securityScheme: SecurityScheme,
) : SecuritySchemeSpec {
    override val type: SecuritySchemeType
        get() =
            when (securityScheme.type) {
                SecurityScheme.Type.APIKEY -> SecuritySchemeType.API_KEY
                SecurityScheme.Type.HTTP -> SecuritySchemeType.HTTP
                SecurityScheme.Type.OAUTH2 -> SecuritySchemeType.OAUTH2
                SecurityScheme.Type.OPENIDCONNECT -> SecuritySchemeType.OPEN_ID_CONNECT
                SecurityScheme.Type.MUTUALTLS -> SecuritySchemeType.MUTUAL_TLS
                else -> SecuritySchemeType.API_KEY
            }

    override val description: String?
        get() = securityScheme.description

    override val name: String?
        get() = securityScheme.name

    override val location: ApiKeyLocation?
        get() =
            when (securityScheme.`in`) {
                SecurityScheme.In.QUERY -> ApiKeyLocation.QUERY
                SecurityScheme.In.HEADER -> ApiKeyLocation.HEADER
                SecurityScheme.In.COOKIE -> ApiKeyLocation.COOKIE
                else -> null
            }

    override val scheme: String?
        get() = securityScheme.scheme

    override val bearerFormat: String?
        get() = securityScheme.bearerFormat

    override val flows: OAuthFlowsSpec? by lazy {
        securityScheme.flows?.let { SwaggerOAuthFlowsSpec(it) }
    }

    override val openIdConnectUrl: String?
        get() = securityScheme.openIdConnectUrl
}

/**
 * OAuthFlowsSpec implementation.
 */
internal class SwaggerOAuthFlowsSpec(
    private val flows: OAuthFlows,
) : OAuthFlowsSpec {
    override val implicit: OAuthFlowSpec? by lazy {
        flows.implicit?.let { SwaggerOAuthFlowSpec(it) }
    }

    override val password: OAuthFlowSpec? by lazy {
        flows.password?.let { SwaggerOAuthFlowSpec(it) }
    }

    override val clientCredentials: OAuthFlowSpec? by lazy {
        flows.clientCredentials?.let { SwaggerOAuthFlowSpec(it) }
    }

    override val authorizationCode: OAuthFlowSpec? by lazy {
        flows.authorizationCode?.let { SwaggerOAuthFlowSpec(it) }
    }
}

/**
 * OAuthFlowSpec implementation.
 */
internal class SwaggerOAuthFlowSpec(
    private val flow: OAuthFlow,
) : OAuthFlowSpec {
    override val authorizationUrl: String?
        get() = flow.authorizationUrl

    override val tokenUrl: String?
        get() = flow.tokenUrl

    override val refreshUrl: String?
        get() = flow.refreshUrl

    override val scopes: Map<String, String>
        get() = flow.scopes ?: emptyMap()
}

/**
 * LinkSpec implementation.
 */
internal class SwaggerLinkSpec(
    private val link: Link,
) : LinkSpec {
    override val operationId: String?
        get() = link.operationId

    override val operationRef: String?
        get() = link.operationRef

    override val parameters: Map<String, Any>
        get() = link.parameters ?: emptyMap()

    override val requestBody: Any?
        get() = link.requestBody

    override val description: String?
        get() = link.description

    override val server: ServerInfo? by lazy {
        link.server?.let { s ->
            ServerInfo(
                url = s.url ?: "",
                description = s.description,
                variables =
                    s.variables?.mapValues { (_, v) ->
                        ServerVariable(
                            default = v.default ?: "",
                            enum = v.enum,
                            description = v.description,
                        )
                    } ?: emptyMap(),
            )
        }
    }
}
