package org.berrycrush.openapi

/**
 * Schema specification for JSON Schema-based validation.
 *
 * Supports both OpenAPI 3.0.x schema and OpenAPI 3.1.x (JSON Schema Draft 2020-12).
 */
interface SchemaSpec {
    /**
     * Schema type(s).
     *
     * In OpenAPI 3.0.x, this is a single type.
     * In OpenAPI 3.1.x, this can be an array (e.g., ["string", "null"]).
     */
    val types: List<String>

    /**
     * The primary type (first type in the list).
     */
    val type: String?
        get() = types.firstOrNull()

    /**
     * Whether this schema allows null values.
     *
     * In 3.0.x: based on nullable field.
     * In 3.1.x: based on "null" being in the types array.
     */
    val nullable: Boolean

    /**
     * Schema format (e.g., "date-time", "uuid", "email").
     */
    val format: String?

    /**
     * Schema title.
     */
    val title: String?

    /**
     * Schema description.
     */
    val description: String?

    /**
     * Default value.
     */
    val default: Any?

    /**
     * Example value.
     */
    val example: Any?

    /**
     * Examples (3.1+ - array of examples).
     */
    val examples: List<Any>

    /**
     * Enum values.
     */
    val enum: List<Any>?

    /**
     * $ref reference to another schema.
     */
    val ref: String?

    // String constraints
    val minLength: Int?
    val maxLength: Int?
    val pattern: String?

    /**
     * Content media type (3.1+ - for encoding strings).
     */
    val contentMediaType: String?

    /**
     * Content encoding (3.1+ - e.g., "base64").
     */
    val contentEncoding: String?

    // Numeric constraints
    val minimum: Number?
    val maximum: Number?

    /**
     * Exclusive minimum (3.1+: number, 3.0: boolean flag).
     */
    val exclusiveMinimum: Number?

    /**
     * Exclusive maximum (3.1+: number, 3.0: boolean flag).
     */
    val exclusiveMaximum: Number?

    val multipleOf: Number?

    // Array constraints
    val minItems: Int?
    val maxItems: Int?
    val uniqueItems: Boolean?
    val items: SchemaSpec?
    val prefixItems: List<SchemaSpec>? // 3.1+ (replaces tuple validation)
    val contains: SchemaSpec? // 3.1+
    val minContains: Int? // 3.1+
    val maxContains: Int? // 3.1+

    // Object constraints
    val properties: Map<String, SchemaSpec>?
    val required: List<String>
    val additionalProperties: SchemaSpec?
    val minProperties: Int?
    val maxProperties: Int?
    val propertyNames: SchemaSpec? // 3.1+

    /**
     * Unevaluated properties (3.1+ JSON Schema).
     */
    val unevaluatedProperties: Any?

    // Composition keywords
    val allOf: List<SchemaSpec>?
    val anyOf: List<SchemaSpec>?
    val oneOf: List<SchemaSpec>?
    val not: SchemaSpec?

    // Conditional (3.1+)
    val `if`: SchemaSpec?
    val then: SchemaSpec?
    val `else`: SchemaSpec?

    /**
     * Const value (exact match required).
     */
    val const: Any?

    /**
     * Read-only flag.
     */
    val readOnly: Boolean

    /**
     * Write-only flag.
     */
    val writeOnly: Boolean

    /**
     * Whether this schema is deprecated.
     */
    val deprecated: Boolean

    /**
     * Discriminator for polymorphism.
     */
    val discriminator: DiscriminatorSpec?

    /**
     * External documentation.
     */
    val externalDocs: ExternalDocsSpec?

    /**
     * XML configuration.
     */
    val xml: XmlSpec?

    /**
     * Access the raw schema model for advanced use.
     */
    val rawSchema: Any?
}

/**
 * Discriminator for polymorphic schemas.
 */
data class DiscriminatorSpec(
    val propertyName: String,
    val mapping: Map<String, String>,
)

/**
 * External documentation reference.
 */
data class ExternalDocsSpec(
    val url: String,
    val description: String?,
)

/**
 * XML serialization configuration.
 */
data class XmlSpec(
    val name: String?,
    val namespace: String?,
    val prefix: String?,
    val attribute: Boolean,
    val wrapped: Boolean,
)
