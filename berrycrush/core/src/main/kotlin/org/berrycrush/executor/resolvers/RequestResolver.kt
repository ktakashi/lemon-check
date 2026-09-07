package org.berrycrush.executor.resolvers

import org.berrycrush.config.BindingConfig
import org.berrycrush.executor.BerryCrushConfigurationProvider
import org.berrycrush.executor.HttpRequestBuilder
import org.berrycrush.model.BodyProperty
import org.berrycrush.model.HttpRequest
import org.berrycrush.model.Step
import org.berrycrush.model.callDirective
import org.berrycrush.openapi.LoadedSpec
import org.berrycrush.openapi.ResolvedOperation
import org.berrycrush.openapi.SchemaSpec
import org.berrycrush.plugin.StepContext
import org.berrycrush.util.FileLoader
import org.berrycrush.util.toNonNullMap
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID

interface UrlResolver {
    fun resolveUrl(
        step: Step,
        spec: LoadedSpec,
        operation: ResolvedOperation,
        context: StepContext,
        pathParams: Map<String, Any>? = null,
        queryParams: Map<String, Any>? = null,
    ): String
}

fun interface HeaderResolver {
    fun resolveHeader(
        step: Step,
        spec: LoadedSpec,
        context: StepContext,
    ): Map<String, String>
}

interface BodyResolver {
    fun resolveBody(
        properties: Map<String, BodyProperty>,
        operation: ResolvedOperation?,
        context: StepContext,
    ): Map<String, Any>

    fun resolveBody(
        step: Step,
        operation: ResolvedOperation?,
        context: StepContext,
    ): String?
}

interface RequestResolver :
    UrlResolver,
    HeaderResolver,
    BodyResolver {
    fun resolve(
        step: Step,
        spec: LoadedSpec,
        operation: ResolvedOperation,
        context: StepContext,
    ): HttpRequest
}

/**
 * Request resolver.
 */
class DefaultRequestResolver(
    private val urlResolver: UrlResolver,
    private val headerResolver: HeaderResolver,
    private val bodyResolver: BodyResolver,
) : RequestResolver,
    UrlResolver by urlResolver,
    HeaderResolver by headerResolver,
    BodyResolver by bodyResolver {
    @JvmOverloads
    constructor(
        configuration: BerryCrushConfigurationProvider,
        httpBuilder: HttpRequestBuilder,
        objectMapper: ObjectMapper = ObjectMapper(),
    ) :
        this(
            DefaultUrlResolver(configuration, httpBuilder),
            DefaultHeaderResolver(configuration),
            DefaultBodyResolver(objectMapper),
        )

    override fun resolve(
        step: Step,
        spec: LoadedSpec,
        operation: ResolvedOperation,
        context: StepContext,
    ): HttpRequest =
        HttpRequest(
            operation.method,
            resolveUrl(step, spec, operation, context),
            resolveHeader(step, spec, context),
            resolveBody(step, operation, context),
        )
}

// default implementations of the resolvers

private class DefaultUrlResolver(
    private val configuration: BerryCrushConfigurationProvider,
    private val httpBuilder: HttpRequestBuilder,
) : UrlResolver {
    override fun resolveUrl(
        step: Step,
        spec: LoadedSpec,
        operation: ResolvedOperation,
        context: StepContext,
        pathParams: Map<String, Any>?,
        queryParams: Map<String, Any>?,
    ): String {
        val call = step.callDirective
        return httpBuilder.buildUrl(
            baseUrl = getBinding(call?.specName)?.baseUrl ?: spec.baseUrl,
            path = operation.path,
            pathParams = context.resolveParams(pathParams ?: (call?.pathParams ?: emptyMap())).toNonNullMap(),
            queryParams = context.resolveParams(queryParams ?: (call?.queryParams ?: emptyMap())).toNonNullMap(),
        )
    }

    fun getBinding(name: String?) =
        name?.let {
            configuration.bindings[it]
        } ?: configuration.bindings[BindingConfig.DEFAULT_BINDING_NAME]
}

private class DefaultHeaderResolver(
    private val configuration: BerryCrushConfigurationProvider,
) : HeaderResolver {
    override fun resolveHeader(
        step: Step,
        spec: LoadedSpec,
        context: StepContext,
    ): Map<String, String> {
        val call = step.callDirective
        return (configuration.defaultHeaders + spec.defaultHeaders + (call?.headers ?: emptyMap())).mapValues { (_, value) ->
            context.interpolate(value)
        }
    }
}

private class DefaultBodyResolver(
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : BodyResolver {
    override fun resolveBody(
        properties: Map<String, BodyProperty>,
        operation: ResolvedOperation?,
        context: StepContext,
    ): Map<String, Any> {
        val schemaDefaults = operation?.let { getSchemaDefaults(it) } ?: emptyMap()
        val merged = if (schemaDefaults.isEmpty()) properties else mergeBodyProperties(schemaDefaults, properties)
        return merged.mapValues { (_, value) -> resolveProperty(value, context) }.toNonNullMap()
    }

    override fun resolveBody(
        step: Step,
        operation: ResolvedOperation?,
        context: StepContext,
    ): String? {
        val call = step.callDirective
        // Inline body takes precedence
        call?.body?.let { return context.interpolate(it) }

        // Structured body properties - generate from schema and merge
        call?.bodyProperties?.let { props ->
            return objectMapper.writeValueAsString(resolveBody(props, operation, context))
        }
        return call?.bodyFile?.let { file ->
            context.interpolate(FileLoader.load(context.interpolate(file)))
        }
    }

    @Suppress("ReturnCount") // Multiple early returns for validation guards
    private fun getSchemaDefaults(resolvedOp: ResolvedOperation): Map<String, BodyProperty> {
        val requestBody = resolvedOp.operation.requestBody ?: return emptyMap()
        val content = requestBody.content
        if (content.isEmpty()) return emptyMap()

        // Prefer application/json schema
        val mediaType = content["application/json"] ?: content.values.firstOrNull() ?: return emptyMap()
        val schema = mediaType.schema ?: return emptyMap()

        return extractPropertiesFromSchemaSpec(schema)
    }

    private fun extractPropertiesFromSchemaSpec(schema: SchemaSpec): Map<String, BodyProperty> {
        val result = mutableMapOf<String, BodyProperty>()

        schema.properties?.forEach { (name, propSchema) ->
            val defaultValue = getSchemaSpecDefaultValue(propSchema)
            if (defaultValue != null) {
                result[name] = defaultValue
            }
        }

        return result
    }

    private fun getSchemaSpecDefaultValue(schema: SchemaSpec): BodyProperty? {
        // Use explicit default if provided
        schema.default?.let { return BodyProperty.Simple(it) }

        // Use example if provided
        schema.example?.let { return BodyProperty.Simple(it) }

        // Generate a sensible default based on type
        return when (schema.type) {
            "string" -> {
                handleFormat(schema)
            }

            "integer", "number" -> {
                BodyProperty.Simple(0)
            }

            "boolean" -> {
                BodyProperty.Simple(false)
            }

            "array" -> {
                BodyProperty.Simple(emptyList<Any>())
            }

            "object" -> {
                val nestedProps = extractPropertiesFromSchemaSpec(schema)
                if (nestedProps.isNotEmpty()) {
                    BodyProperty.Nested(nestedProps)
                } else {
                    BodyProperty.Simple(emptyMap<String, Any>())
                }
            }

            else -> {
                null
            }
        }
    }

    private fun handleFormat(schema: SchemaSpec): BodyProperty? =
        when (schema.format) {
            "date" -> BodyProperty.Simple(LocalDate.now().toString())
            "date-time" -> BodyProperty.Simple(OffsetDateTime.now().toString())
            "time" -> BodyProperty.Simple(LocalTime.now().toString())
            "email", "idn-email" -> BodyProperty.Simple("no-reply@berrycrush.org")
            "uri" -> BodyProperty.Simple("https://berrycrush.org")
            "uri-reference" -> BodyProperty.Simple("/berrycrush.org")
            "uuid" -> BodyProperty.Simple(UUID.randomUUID().toString())
            else -> BodyProperty.Simple("")
        }

    /**
     * Merge schema defaults with user-provided properties.
     * User properties override schema defaults.
     */
    private fun mergeBodyProperties(
        defaults: Map<String, BodyProperty>,
        userProps: Map<String, BodyProperty>,
    ): Map<String, BodyProperty> {
        val result = defaults.toMutableMap()

        userProps.forEach { (key, value) ->
            val existing = result[key]
            if (existing is BodyProperty.Nested && value is BodyProperty.Nested) {
                // Deep merge nested properties
                result[key] = BodyProperty.Nested(mergeBodyProperties(existing.properties, value.properties))
            } else {
                // User property overrides
                result[key] = value
            }
        }

        return result
    }

    private fun resolveProperty(
        value: BodyProperty,
        context: StepContext,
    ): Any? =
        when (value) {
            is BodyProperty.Simple -> context.resolveParam(value.value)
            is BodyProperty.Container -> objectMapper.readTree(context.interpolate(value.value))
            is BodyProperty.Nested -> value.properties.mapValues { (_, value) -> resolveProperty(value, context) }
        }
}
