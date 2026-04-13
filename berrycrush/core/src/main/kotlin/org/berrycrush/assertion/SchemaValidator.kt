package org.berrycrush.assertion

import com.networknt.schema.Error
import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import org.berrycrush.exception.SchemaValidationException
import org.berrycrush.model.ValidationError
import tools.jackson.databind.ObjectMapper
import io.swagger.v3.oas.models.media.Schema as OpenApiSchema

/**
 * Validates JSON responses against OpenAPI schemas.
 */
class SchemaValidator(
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {
    // Use SchemaRegistry supporting all standard dialects, default to Draft 2020-12
    private val schemaRegistry =
        SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)

    /**
     * Validate a JSON response against an OpenAPI schema.
     *
     * @param responseBody JSON response body to validate
     * @param schema OpenAPI schema to validate against
     * @param strict If true, fail on additional properties not in schema
     * @return List of validation errors (empty if valid)
     */
    fun validate(
        responseBody: String,
        schema: OpenApiSchema<*>,
        strict: Boolean = false,
    ): List<ValidationError> {
        val jsonSchema = convertToJsonSchema(schema, strict)
        return validateAgainstSchema(responseBody, jsonSchema)
    }

    /**
     * Validate a JSON response, throwing an exception if invalid.
     *
     * @param responseBody JSON response body to validate
     * @param schema OpenAPI schema to validate against
     * @param strict If true, fail on additional properties not in schema
     * @throws SchemaValidationException if validation fails
     */
    fun validateOrThrow(
        responseBody: String,
        schema: OpenApiSchema<*>,
        strict: Boolean = false,
    ) {
        val errors = validate(responseBody, schema, strict)
        if (errors.isNotEmpty()) {
            throw SchemaValidationException(errors.map { it.message })
        }
    }

    /**
     * Validate JSON against a JSON Schema string.
     *
     * @param responseBody JSON response body
     * @param jsonSchemaString JSON Schema as a string
     * @return List of validation errors
     */
    fun validateAgainstJsonSchema(
        responseBody: String,
        jsonSchemaString: String,
    ): List<ValidationError> {
        val schema = schemaRegistry.getSchema(jsonSchemaString, InputFormat.JSON)
        return validateAgainstSchema(responseBody, schema)
    }

    private fun validateAgainstSchema(
        responseBody: String,
        schema: Schema,
    ): List<ValidationError> {
        val errors: List<Error> = schema.validate(responseBody, InputFormat.JSON)

        return errors.map { error ->
            ValidationError(
                path = error.instanceLocation.toString(),
                message = error.message,
                keyword = error.keyword ?: "unknown",
                schemaPath = error.schemaLocation.toString(),
            )
        }
    }

    private fun convertToJsonSchema(
        schema: OpenApiSchema<*>,
        strict: Boolean,
    ): Schema {
        val jsonSchemaMap = buildJsonSchemaMap(schema, strict)
        val jsonSchemaString = objectMapper.writeValueAsString(jsonSchemaMap)
        return schemaRegistry.getSchema(jsonSchemaString, InputFormat.JSON)
    }

    private fun buildJsonSchemaMap(
        schema: OpenApiSchema<*>,
        strict: Boolean,
    ): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        result[$$"$schema"] = "https://json-schema.org/draft/2020-12/schema"

        schema.type?.let { result["type"] = it }
        schema.format?.let { result["format"] = it }
        schema.minimum?.let { result["minimum"] = it }
        schema.maximum?.let { result["maximum"] = it }
        schema.minLength?.let { result["minLength"] = it }
        schema.maxLength?.let { result["maxLength"] = it }
        schema.pattern?.let { result["pattern"] = it }
        schema.enum?.let { result["enum"] = it }

        // Handle object properties
        schema.properties?.let { props ->
            result["properties"] =
                props.mapValues { (_, v) ->
                    buildJsonSchemaMap(v, strict)
                }
        }

        schema.required?.let { result["required"] = it }

        if (strict && schema.type == "object") {
            result["additionalProperties"] = false
        }

        // Handle array items
        schema.items?.let { items ->
            result["items"] = buildJsonSchemaMap(items, strict)
        }

        schema.minItems?.let { result["minItems"] = it }
        schema.maxItems?.let { result["maxItems"] = it }

        return result
    }
}
