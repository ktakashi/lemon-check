package io.github.ktakashi.lemoncheck.autotest.provider

import io.swagger.v3.oas.models.media.Schema
import java.util.UUID

/**
 * Collection of default invalid test providers.
 */
object DefaultInvalidTestProviders {
    /**
     * All built-in invalid test providers.
     */
    val all: List<InvalidTestProvider> =
        listOf(
            MinLengthProvider(),
            MaxLengthProvider(),
            PatternProvider(),
            FormatProvider(),
            EnumProvider(),
            MinimumProvider(),
            MaximumProvider(),
            TypeProvider(),
            RequiredProvider(),
            MinItemsProvider(),
            MaxItemsProvider(),
        )
}

/**
 * Tests for string values shorter than minLength.
 */
class MinLengthProvider : InvalidTestProvider {
    override val testType: String = "minLength"

    override fun canHandle(schema: Schema<*>): Boolean = schema.type == "string" && schema.minLength != null && schema.minLength > 0

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> {
        val minLen = schema.minLength ?: return emptyList()
        val invalidValue = "x".repeat((minLen - 1).coerceAtLeast(0))
        return listOf(
            InvalidTestValue(
                value = invalidValue,
                description = "String shorter than minLength ($minLen)",
            ),
        )
    }
}

/**
 * Tests for string values longer than maxLength.
 */
class MaxLengthProvider : InvalidTestProvider {
    override val testType: String = "maxLength"

    override fun canHandle(schema: Schema<*>): Boolean = schema.type == "string" && schema.maxLength != null

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> {
        val maxLen = schema.maxLength ?: return emptyList()
        val invalidValue = "x".repeat(maxLen + 10)
        return listOf(
            InvalidTestValue(
                value = invalidValue,
                description = "String longer than maxLength ($maxLen)",
            ),
        )
    }
}

/**
 * Tests for strings not matching pattern constraint.
 */
class PatternProvider : InvalidTestProvider {
    override val testType: String = "pattern"

    override fun canHandle(schema: Schema<*>): Boolean = schema.type == "string" && schema.pattern != null

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> {
        val pattern = schema.pattern ?: return emptyList()
        return listOf(
            InvalidTestValue(
                value = "!!!invalid_pattern!!!",
                description = "String not matching pattern ($pattern)",
            ),
        )
    }
}

/**
 * Tests for strings with invalid format (email, uuid, date, etc.).
 */
class FormatProvider : InvalidTestProvider {
    override val testType: String = "format"

    override fun canHandle(schema: Schema<*>): Boolean = schema.type == "string" && schema.format != null

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> {
        val format = schema.format ?: return emptyList()
        val invalidValue =
            when (format) {
                "email" -> "not-an-email"
                "uuid" -> "not-a-uuid"
                "uri", "url" -> "not-a-url"
                "date" -> "not-a-date"
                "date-time" -> "not-a-datetime"
                "ipv4" -> "not.an.ip"
                "ipv6" -> "not:an:ipv6"
                else -> return emptyList()
            }
        return listOf(
            InvalidTestValue(
                value = invalidValue,
                description = "Invalid format ($format)",
            ),
        )
    }
}

/**
 * Tests for values not in enum.
 */
class EnumProvider : InvalidTestProvider {
    override val testType: String = "enum"

    override fun canHandle(schema: Schema<*>): Boolean = schema.enum != null && schema.enum.isNotEmpty()

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> {
        @Suppress("UNUSED_VARIABLE")
        val enumValues = schema.enum ?: return emptyList()
        return listOf(
            InvalidTestValue(
                value = "INVALID_ENUM_VALUE_${UUID.randomUUID()}",
                description = "Value not in enum",
            ),
        )
    }
}

/**
 * Tests for numeric values below minimum.
 */
class MinimumProvider : InvalidTestProvider {
    override val testType: String = "minimum"

    override fun canHandle(schema: Schema<*>): Boolean = (schema.type == "integer" || schema.type == "number") && schema.minimum != null

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> {
        val min = schema.minimum ?: return emptyList()
        val invalidValue = min.subtract(java.math.BigDecimal.ONE)
        return listOf(
            InvalidTestValue(
                value = invalidValue,
                description = "Value below minimum ($min)",
            ),
        )
    }
}

/**
 * Tests for numeric values above maximum.
 */
class MaximumProvider : InvalidTestProvider {
    override val testType: String = "maximum"

    override fun canHandle(schema: Schema<*>): Boolean = (schema.type == "integer" || schema.type == "number") && schema.maximum != null

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> {
        val max = schema.maximum ?: return emptyList()
        val invalidValue = max.add(java.math.BigDecimal.ONE)
        return listOf(
            InvalidTestValue(
                value = invalidValue,
                description = "Value above maximum ($max)",
            ),
        )
    }
}

/**
 * Tests for type mismatches (e.g., string instead of number).
 */
class TypeProvider : InvalidTestProvider {
    override val testType: String = "type"

    override fun canHandle(schema: Schema<*>): Boolean = schema.type in listOf("integer", "number", "boolean")

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> {
        val type = schema.type
        return when (type) {
            "integer", "number" ->
                listOf(
                    InvalidTestValue(
                        value = "not-a-number",
                        description = "Invalid type (string instead of $type)",
                    ),
                )
            "boolean" ->
                listOf(
                    InvalidTestValue(
                        value = "not-a-boolean",
                        description = "Invalid boolean value",
                    ),
                )
            else -> emptyList()
        }
    }
}

/**
 * Tests for missing required fields.
 *
 * Note: This provider returns empty values; the actual test generation
 * for required fields is handled by the generator which removes the field.
 */
class RequiredProvider : InvalidTestProvider {
    override val testType: String = "required"

    override fun canHandle(schema: Schema<*>): Boolean = false

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> = emptyList()
}

/**
 * Tests for arrays with fewer items than minItems.
 */
class MinItemsProvider : InvalidTestProvider {
    override val testType: String = "minItems"

    override fun canHandle(schema: Schema<*>): Boolean = schema.type == "array" && schema.minItems != null && schema.minItems > 0

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> {
        val minItems = schema.minItems ?: return emptyList()
        return listOf(
            InvalidTestValue(
                value = emptyList<Any>(),
                description = "Array with fewer items than minItems ($minItems)",
            ),
        )
    }
}

/**
 * Tests for arrays with more items than maxItems.
 */
class MaxItemsProvider : InvalidTestProvider {
    override val testType: String = "maxItems"

    override fun canHandle(schema: Schema<*>): Boolean = schema.type == "array" && schema.maxItems != null

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> {
        val maxItems = schema.maxItems ?: return emptyList()
        val tooManyItems = (0..maxItems + 5).map { "item$it" }
        return listOf(
            InvalidTestValue(
                value = tooManyItems,
                description = "Array with more items than maxItems ($maxItems)",
            ),
        )
    }
}
