package io.github.ktakashi.lemoncheck.autotest

import io.github.ktakashi.lemoncheck.openapi.LoadedSpec
import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
import io.github.ktakashi.lemoncheck.scenario.AutoTestType
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Schema
import java.util.*

/**
 * Generates auto-test cases based on OpenAPI schema constraints and security patterns.
 *
 * This class analyzes an OpenAPI specification and generates test cases that:
 * - Violate schema constraints (invalid tests): minLength, maxLength, pattern, required, enum, type
 * - Include common attack payloads (security tests): SQL injection, XSS, path traversal, etc.
 *
 * Test cases are generated for:
 * - Request body fields
 * - Path parameters
 * - Header parameters
 *
 * ## Usage
 *
 * ```kotlin
 * val generator = AutoTestGenerator.fromSpec(loadedSpec)
 * val testCases = generator.generateTestCases(
 *     operationId = "createPet",
 *     testTypes = setOf(AutoTestType.INVALID, AutoTestType.SECURITY),
 *     baseBody = mapOf("name" to "ValidName")
 * )
 * ```
 *
 * @property openApi The parsed OpenAPI specification
 * @see AutoTestCase The data class representing a generated test case
 * @see SecurityTestPatterns Common attack payloads for security testing
 */
class AutoTestGenerator(
    private val openApi: OpenAPI,
) {
    companion object {
        /**
         * Create an AutoTestGenerator from a SpecRegistry using the default spec.
         */
        fun fromRegistry(registry: SpecRegistry): AutoTestGenerator =
            AutoTestGenerator(registry.getDefault().openApi)

        /**
         * Create an AutoTestGenerator from a SpecRegistry for a specific spec.
         */
        fun fromRegistry(
            registry: SpecRegistry,
            specName: String,
        ): AutoTestGenerator = AutoTestGenerator(registry.get(specName).openApi)

        /**
         * Create an AutoTestGenerator from a LoadedSpec.
         */
        fun fromSpec(spec: LoadedSpec): AutoTestGenerator = AutoTestGenerator(spec.openApi)
    }

    /**
     * Generate test cases for an operation.
     *
     * @param operationId The operation to generate tests for
     * @param testTypes Which types of tests to generate
     * @param baseBody Optional existing body properties to start from
     * @param basePathParams Optional existing path parameters (with valid values for other params)
     * @param baseHeaders Optional existing headers (with valid values for other headers)
     * @return List of generated test cases
     */
    fun generateTestCases(
        operationId: String,
        testTypes: Set<AutoTestType>,
        baseBody: Map<String, Any>? = null,
        basePathParams: Map<String, Any?>? = null,
        baseHeaders: Map<String, String>? = null,
    ): List<AutoTestCase> {
        val operation = findOperation(operationId) ?: return emptyList()
        val requestBodySchema = extractRequestBodySchema(operation)

        val testCases = mutableListOf<AutoTestCase>()
        val effectiveBody = baseBody ?: emptyMap()
        val effectivePathParams = basePathParams ?: emptyMap()
        val effectiveHeaders = baseHeaders ?: emptyMap()

        // Generate tests for request body
        if (requestBodySchema != null) {
            if (AutoTestType.INVALID in testTypes) {
                testCases.addAll(generateInvalidTestCases(requestBodySchema, effectiveBody))
            }
            if (AutoTestType.SECURITY in testTypes) {
                testCases.addAll(generateSecurityTestCases(requestBodySchema, effectiveBody))
            }
        }

        // Generate tests for path parameters
        val pathParams = operation.parameters?.filter { it.`in` == "path" } ?: emptyList()
        if (pathParams.isNotEmpty()) {
            if (AutoTestType.INVALID in testTypes) {
                testCases.addAll(generatePathParamInvalidTests(pathParams, effectiveBody, effectivePathParams))
            }
            if (AutoTestType.SECURITY in testTypes) {
                testCases.addAll(generatePathParamSecurityTests(pathParams, effectiveBody, effectivePathParams))
            }
        }

        // Generate tests for header parameters
        val headerParams = operation.parameters?.filter { it.`in` == "header" } ?: emptyList()
        if (headerParams.isNotEmpty()) {
            if (AutoTestType.INVALID in testTypes) {
                testCases.addAll(generateHeaderInvalidTests(headerParams, effectiveBody, effectiveHeaders))
            }
            if (AutoTestType.SECURITY in testTypes) {
                testCases.addAll(generateHeaderSecurityTests(headerParams, effectiveBody, effectiveHeaders))
            }
        }

        return testCases
    }

    private fun findOperation(operationId: String): Operation? {
        openApi.paths?.values?.forEach { pathItem ->
            listOfNotNull(
                pathItem.get,
                pathItem.post,
                pathItem.put,
                pathItem.delete,
                pathItem.patch,
            ).forEach { operation ->
                if (operation.operationId == operationId) {
                    return operation
                }
            }
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractRequestBodySchema(operation: Operation): Schema<*>? {
        val requestBody = operation.requestBody ?: return null
        val content = requestBody.content ?: return null

        // Try JSON content types
        val mediaType = content["application/json"]
            ?: content.entries.firstOrNull()?.value
            ?: return null

        var schema = mediaType.schema ?: return null

        // Resolve $ref if present
        if (schema.`$ref` != null) {
            val refName = schema.`$ref`.substringAfterLast("/")
            schema = openApi.components?.schemas?.get(refName) ?: return null
        }

        return schema
    }

    /**
     * Generate invalid test cases based on schema constraints.
     */
    @Suppress("UNCHECKED_CAST")
    private fun generateInvalidTestCases(
        schema: Schema<*>,
        baseBody: Map<String, Any>,
    ): List<AutoTestCase> {
        val testCases = mutableListOf<AutoTestCase>()
        val properties = schema.properties ?: return testCases
        val requiredFields = schema.required ?: emptyList()

        properties.forEach { (fieldName, fieldSchema) ->
            val resolvedSchema = resolveSchema(fieldSchema as Schema<*>)

            // Generate constraint violation tests
            testCases.addAll(
                generateConstraintViolations(fieldName, resolvedSchema, baseBody, requiredFields)
            )
        }

        // Generate tests for missing required fields
        requiredFields.forEach { requiredField ->
            if (properties.containsKey(requiredField)) {
                testCases.add(
                    AutoTestCase(
                        type = AutoTestType.INVALID,
                        fieldName = requiredField,
                        invalidValue = null,
                        description = "Missing required field '$requiredField'",
                        body = baseBody.filterKeys { it != requiredField },
                        tag = "invalid",
                    )
                )
            }
        }

        return testCases
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveSchema(schema: Schema<*>): Schema<*> {
        if (schema.`$ref` != null) {
            val refName = schema.`$ref`.substringAfterLast("/")
            return openApi.components?.schemas?.get(refName) ?: schema
        }
        return schema
    }

    @Suppress("UNCHECKED_CAST")
    private fun generateConstraintViolations(
        fieldName: String,
        schema: Schema<*>,
        baseBody: Map<String, Any>,
        requiredFields: List<String>,
    ): List<AutoTestCase> {
        val testCases = mutableListOf<AutoTestCase>()
        val type = schema.type ?: "string"

        when (type) {
            "string" -> {
                // minLength violation
                schema.minLength?.let { minLen ->
                    if (minLen > 0) {
                        val invalidValue = "x".repeat((minLen - 1).coerceAtLeast(0))
                        testCases.add(createInvalidTestCase(fieldName, invalidValue,
                            "String shorter than minLength ($minLen)", baseBody))
                    }
                }

                // maxLength violation
                schema.maxLength?.let { maxLen ->
                    val invalidValue = "x".repeat(maxLen + 10)
                    testCases.add(createInvalidTestCase(fieldName, invalidValue,
                        "String longer than maxLength ($maxLen)", baseBody))
                }

                // pattern violation
                schema.pattern?.let { pattern ->
                    testCases.add(createInvalidTestCase(fieldName, "!!!invalid_pattern!!!",
                        "String not matching pattern ($pattern)", baseBody))
                }

                // format violations
                schema.format?.let { format ->
                    val invalidValue = when (format) {
                        "email" -> "not-an-email"
                        "uuid" -> "not-a-uuid"
                        "uri", "url" -> "not-a-url"
                        "date" -> "not-a-date"
                        "date-time" -> "not-a-datetime"
                        "ipv4" -> "not.an.ip"
                        "ipv6" -> "not:an:ipv6"
                        else -> null
                    }
                    invalidValue?.let {
                        testCases.add(createInvalidTestCase(fieldName, it,
                            "Invalid format ($format)", baseBody))
                    }
                }

                // enum violation
                schema.enum?.let { enumValues ->
                    if (enumValues.isNotEmpty()) {
                        testCases.add(createInvalidTestCase(fieldName, "INVALID_ENUM_VALUE_${UUID.randomUUID()}",
                            "Value not in enum", baseBody))
                    }
                }
            }

            "integer", "number" -> {
                // minimum violation
                schema.minimum?.let { min ->
                    val invalidValue = min.subtract(java.math.BigDecimal.ONE)
                    testCases.add(createInvalidTestCase(fieldName, invalidValue,
                        "Value below minimum ($min)", baseBody))
                }

                // maximum violation
                schema.maximum?.let { max ->
                    val invalidValue = max.add(java.math.BigDecimal.ONE)
                    testCases.add(createInvalidTestCase(fieldName, invalidValue,
                        "Value above maximum ($max)", baseBody))
                }

                // Type mismatch - string instead of number
                testCases.add(createInvalidTestCase(fieldName, "not-a-number",
                    "Invalid type (string instead of $type)", baseBody))
            }

            "boolean" -> {
                // Invalid boolean value
                testCases.add(createInvalidTestCase(fieldName, "not-a-boolean",
                    "Invalid boolean value", baseBody))
            }

            "array" -> {
                // minItems violation
                schema.minItems?.let { minItems ->
                    if (minItems > 0) {
                        testCases.add(createInvalidTestCase(fieldName, emptyList<Any>(),
                            "Array with fewer items than minItems ($minItems)", baseBody))
                    }
                }

                // maxItems violation
                schema.maxItems?.let { maxItems ->
                    val tooManyItems = (0..maxItems + 5).map { "item$it" }
                    testCases.add(createInvalidTestCase(fieldName, tooManyItems,
                        "Array with more items than maxItems ($maxItems)", baseBody))
                }
            }
        }

        return testCases
    }

    private fun createInvalidTestCase(
        fieldName: String,
        invalidValue: Any?,
        description: String,
        baseBody: Map<String, Any>,
        location: ParameterLocation = ParameterLocation.BODY,
        basePathParams: Map<String, Any?> = emptyMap(),
        baseHeaders: Map<String, String> = emptyMap(),
    ): AutoTestCase {
        return when (location) {
            ParameterLocation.BODY -> {
                val modifiedBody = baseBody.toMutableMap()
                if (invalidValue != null) {
                    modifiedBody[fieldName] = invalidValue
                } else {
                    modifiedBody.remove(fieldName)
                }
                AutoTestCase(
                    type = AutoTestType.INVALID,
                    fieldName = fieldName,
                    invalidValue = invalidValue,
                    description = description,
                    location = ParameterLocation.BODY,
                    body = modifiedBody,
                    tag = "invalid",
                )
            }
            ParameterLocation.PATH -> {
                val modifiedPathParams = basePathParams.toMutableMap()
                modifiedPathParams[fieldName] = invalidValue
                AutoTestCase(
                    type = AutoTestType.INVALID,
                    fieldName = fieldName,
                    invalidValue = invalidValue,
                    description = description,
                    location = ParameterLocation.PATH,
                    body = baseBody,
                    pathParams = modifiedPathParams,
                    tag = "invalid",
                )
            }
            ParameterLocation.HEADER -> {
                val modifiedHeaders = baseHeaders.toMutableMap()
                modifiedHeaders[fieldName] = invalidValue?.toString() ?: ""
                AutoTestCase(
                    type = AutoTestType.INVALID,
                    fieldName = fieldName,
                    invalidValue = invalidValue,
                    description = description,
                    location = ParameterLocation.HEADER,
                    body = baseBody,
                    headers = modifiedHeaders,
                    tag = "invalid",
                )
            }
            ParameterLocation.QUERY -> {
                AutoTestCase(
                    type = AutoTestType.INVALID,
                    fieldName = fieldName,
                    invalidValue = invalidValue,
                    description = description,
                    location = ParameterLocation.QUERY,
                    body = baseBody,
                    tag = "invalid",
                )
            }
        }
    }

    /**
     * Generate security test cases with common attack patterns.
     */
    @Suppress("UNCHECKED_CAST")
    private fun generateSecurityTestCases(
        schema: Schema<*>,
        baseBody: Map<String, Any>,
    ): List<AutoTestCase> {
        val testCases = mutableListOf<AutoTestCase>()
        val properties = schema.properties ?: return testCases

        // Find string fields to test
        val stringFields = properties
            .filter { (_, fieldSchema) ->
                val resolved = resolveSchema(fieldSchema as Schema<*>)
                resolved.type == "string" || resolved.type == null
            }
            .keys

        stringFields.forEach { fieldName ->
            SecurityTestPatterns.getAllPatterns().forEach { pattern ->
                val modifiedBody = baseBody.toMutableMap()
                modifiedBody[fieldName] = pattern.payload
                testCases.add(
                    AutoTestCase(
                        type = AutoTestType.SECURITY,
                        fieldName = fieldName,
                        invalidValue = pattern.payload,
                        description = "${pattern.category}: ${pattern.name}",
                        body = modifiedBody,
                        tag = "security",
                    )
                )
            }
        }

        return testCases
    }

    /**
     * Generate invalid tests for path parameters.
     */
    private fun generatePathParamInvalidTests(
        pathParams: List<io.swagger.v3.oas.models.parameters.Parameter>,
        baseBody: Map<String, Any>,
        basePathParams: Map<String, Any?>,
    ): List<AutoTestCase> {
        val testCases = mutableListOf<AutoTestCase>()

        pathParams.forEach { param ->
            val schema = param.schema ?: return@forEach
            val resolvedSchema = resolveSchema(schema)
            val type = resolvedSchema.type ?: "string"

            when (type) {
                "integer", "number" -> {
                    // Invalid type - string instead of number
                    testCases.add(createInvalidTestCase(
                        fieldName = param.name,
                        invalidValue = "not-a-number",
                        description = "Invalid type (string instead of $type)",
                        baseBody = baseBody,
                        location = ParameterLocation.PATH,
                        basePathParams = basePathParams,
                    ))
                    // Negative value
                    testCases.add(createInvalidTestCase(
                        fieldName = param.name,
                        invalidValue = -1,
                        description = "Negative value",
                        baseBody = baseBody,
                        location = ParameterLocation.PATH,
                        basePathParams = basePathParams,
                    ))
                }
                "string" -> {
                    // Empty string
                    testCases.add(createInvalidTestCase(
                        fieldName = param.name,
                        invalidValue = "",
                        description = "Empty string",
                        baseBody = baseBody,
                        location = ParameterLocation.PATH,
                        basePathParams = basePathParams,
                    ))
                }
            }
        }

        return testCases
    }

    /**
     * Generate security tests for path parameters.
     */
    private fun generatePathParamSecurityTests(
        pathParams: List<io.swagger.v3.oas.models.parameters.Parameter>,
        baseBody: Map<String, Any>,
        basePathParams: Map<String, Any?>,
    ): List<AutoTestCase> {
        val testCases = mutableListOf<AutoTestCase>()

        pathParams.forEach { param ->
            SecurityTestPatterns.getPathTraversalPatterns().forEach { pattern ->
                testCases.add(
                    AutoTestCase(
                        type = AutoTestType.SECURITY,
                        fieldName = param.name,
                        invalidValue = pattern.payload,
                        description = "${pattern.category}: ${pattern.name}",
                        location = ParameterLocation.PATH,
                        body = baseBody,
                        pathParams = basePathParams.toMutableMap().apply { this[param.name] = pattern.payload },
                        tag = "security",
                    )
                )
            }
        }

        return testCases
    }

    /**
     * Generate invalid tests for header parameters.
     */
    private fun generateHeaderInvalidTests(
        headerParams: List<io.swagger.v3.oas.models.parameters.Parameter>,
        baseBody: Map<String, Any>,
        baseHeaders: Map<String, String>,
    ): List<AutoTestCase> {
        val testCases = mutableListOf<AutoTestCase>()

        headerParams.forEach { param ->
            val schema = param.schema ?: return@forEach
            val resolvedSchema = resolveSchema(schema)
            val type = resolvedSchema.type ?: "string"

            // Empty header value
            testCases.add(createInvalidTestCase(
                fieldName = param.name,
                invalidValue = "",
                description = "Empty header value",
                baseBody = baseBody,
                location = ParameterLocation.HEADER,
                baseHeaders = baseHeaders,
            ))

            // Invalid format if format is specified
            resolvedSchema.format?.let { format ->
                val invalidValue = when (format) {
                    "uuid" -> "not-a-uuid"
                    "date" -> "not-a-date"
                    "date-time" -> "not-a-datetime"
                    else -> null
                }
                invalidValue?.let {
                    testCases.add(createInvalidTestCase(
                        fieldName = param.name,
                        invalidValue = it,
                        description = "Invalid format ($format)",
                        baseBody = baseBody,
                        location = ParameterLocation.HEADER,
                        baseHeaders = baseHeaders,
                    ))
                }
            }
        }

        return testCases
    }

    /**
     * Generate security tests for header parameters.
     */
    private fun generateHeaderSecurityTests(
        headerParams: List<io.swagger.v3.oas.models.parameters.Parameter>,
        baseBody: Map<String, Any>,
        baseHeaders: Map<String, String>,
    ): List<AutoTestCase> {
        val testCases = mutableListOf<AutoTestCase>()

        headerParams.forEach { param ->
            // Only test string-type headers
            SecurityTestPatterns.getHeaderPatterns().forEach { pattern ->
                testCases.add(
                    AutoTestCase(
                        type = AutoTestType.SECURITY,
                        fieldName = param.name,
                        invalidValue = pattern.payload,
                        description = "${pattern.category}: ${pattern.name}",
                        location = ParameterLocation.HEADER,
                        body = baseBody,
                        headers = baseHeaders.toMutableMap().apply { this[param.name] = pattern.payload },
                        tag = "security",
                    )
                )
            }
        }

        return testCases
    }
}

/**
 * Location of the parameter being tested.
 */
enum class ParameterLocation {
    BODY,
    PATH,
    QUERY,
    HEADER,
}

/**
 * Represents a generated test case.
 */
data class AutoTestCase(
    /** Type of test (invalid/security) */
    val type: AutoTestType,
    /** Field being tested */
    val fieldName: String,
    /** The invalid/malicious value */
    val invalidValue: Any?,
    /** Human-readable description */
    val description: String,
    /** Location of the parameter (body, path, header, query) */
    val location: ParameterLocation = ParameterLocation.BODY,
    /** Complete request body for this test (for body parameters) */
    val body: Map<String, Any?> = emptyMap(),
    /** Path parameters for this test (for path parameters) */
    val pathParams: Map<String, Any?> = emptyMap(),
    /** Headers for this test (for header parameters) */
    val headers: Map<String, String> = emptyMap(),
    /** Tag to apply to this test */
    val tag: String,
)

/**
 * Security test patterns for common web vulnerabilities.
 */
object SecurityTestPatterns {
    data class SecurityPattern(
        val category: String,
        val name: String,
        val payload: String,
    )

    private val sqlInjectionPatterns = listOf(
        SecurityPattern("SQL Injection", "Single quote", "' OR '1'='1"),
        SecurityPattern("SQL Injection", "Union select", "' UNION SELECT * FROM users--"),
        SecurityPattern("SQL Injection", "Comment bypass", "admin'--"),
        SecurityPattern("SQL Injection", "Boolean-based", "1' AND '1'='1"),
        SecurityPattern("SQL Injection", "Stacked queries", "'; DROP TABLE users;--"),
    )

    private val xssPatterns = listOf(
        SecurityPattern("XSS", "Script tag", "<script>alert('XSS')</script>"),
        SecurityPattern("XSS", "Event handler", "<img src=x onerror=alert('XSS')>"),
        SecurityPattern("XSS", "SVG onload", "<svg onload=alert('XSS')>"),
        SecurityPattern("XSS", "JavaScript URL", "javascript:alert('XSS')"),
        SecurityPattern("XSS", "HTML injection", "<h1>Injected</h1>"),
    )

    private val pathTraversalPatterns = listOf(
        SecurityPattern("Path Traversal", "Unix relative", "../../../etc/passwd"),
        SecurityPattern("Path Traversal", "Windows relative", "..\\..\\..\\windows\\system32\\config\\sam"),
        SecurityPattern("Path Traversal", "URL encoded", "..%2F..%2F..%2Fetc%2Fpasswd"),
        SecurityPattern("Path Traversal", "Double encoded", "..%252F..%252F..%252Fetc%252Fpasswd"),
    )

    private val commandInjectionPatterns = listOf(
        SecurityPattern("Command Injection", "Unix semicolon", "; ls -la"),
        SecurityPattern("Command Injection", "Unix pipe", "| cat /etc/passwd"),
        SecurityPattern("Command Injection", "Unix backtick", "`id`"),
        SecurityPattern("Command Injection", "Unix subshell", "$(whoami)"),
        SecurityPattern("Command Injection", "Windows ampersand", "& dir"),
    )

    private val ldapInjectionPatterns = listOf(
        SecurityPattern("LDAP Injection", "Wildcard", "*"),
        SecurityPattern("LDAP Injection", "Filter bypass", "admin)(&)"),
    )

    private val xmlInjectionPatterns = listOf(
        SecurityPattern("XXE", "External entity", "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"),
        SecurityPattern("XML Injection", "CDATA", "<![CDATA[<script>alert('XSS')</script>]]>"),
    )

    fun getAllPatterns(): List<SecurityPattern> =
        sqlInjectionPatterns +
            xssPatterns +
            pathTraversalPatterns +
            commandInjectionPatterns +
            ldapInjectionPatterns +
            xmlInjectionPatterns

    /**
     * Get patterns suitable for path parameter testing.
     * Path traversal and some injection patterns are most relevant here.
     */
    fun getPathTraversalPatterns(): List<SecurityPattern> =
        pathTraversalPatterns + commandInjectionPatterns.take(3)

    /**
     * Get patterns suitable for header parameter testing.
     * Injection patterns that could affect header-based processing.
     */
    fun getHeaderPatterns(): List<SecurityPattern> =
        xssPatterns.take(2) +
            listOf(
                SecurityPattern("Header Injection", "CRLF injection", "value\r\nX-Injected: header"),
                SecurityPattern("Header Injection", "Null byte", "value\u0000injection"),
            )
}
