package io.github.ktakashi.lemoncheck.autotest

import io.github.ktakashi.lemoncheck.autotest.provider.AutoTestProviderRegistry
import io.github.ktakashi.lemoncheck.openapi.LoadedSpec
import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
import io.github.ktakashi.lemoncheck.scenario.AutoTestType
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.Schema

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
 * @property registry The provider registry for extensibility
 * @see AutoTestCase The data class representing a generated test case
 * @see AutoTestProviderRegistry Provider registration for custom test types
 */
class AutoTestGenerator(
    private val openApi: OpenAPI,
    private val registry: AutoTestProviderRegistry = AutoTestProviderRegistry.default,
) {
    companion object {
        /**
         * Create an AutoTestGenerator from a SpecRegistry using the default spec.
         */
        fun fromRegistry(
            specRegistry: SpecRegistry,
            providerRegistry: AutoTestProviderRegistry = AutoTestProviderRegistry.default,
        ): AutoTestGenerator = AutoTestGenerator(specRegistry.getDefault().openApi, providerRegistry)

        /**
         * Create an AutoTestGenerator from a SpecRegistry for a specific spec.
         */
        fun fromRegistry(
            specRegistry: SpecRegistry,
            specName: String,
            providerRegistry: AutoTestProviderRegistry = AutoTestProviderRegistry.default,
        ): AutoTestGenerator = AutoTestGenerator(specRegistry.get(specName).openApi, providerRegistry)

        /**
         * Create an AutoTestGenerator from a LoadedSpec.
         */
        fun fromSpec(
            spec: LoadedSpec,
            providerRegistry: AutoTestProviderRegistry = AutoTestProviderRegistry.default,
        ): AutoTestGenerator = AutoTestGenerator(spec.openApi, providerRegistry)
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
        val mediaType =
            content["application/json"]
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
                generateConstraintViolations(fieldName, resolvedSchema, baseBody, requiredFields),
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
                        tag = "Invalid request - required",
                    ),
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

    /**
     * Generate constraint violation tests using registered providers.
     */
    private fun generateConstraintViolations(
        fieldName: String,
        schema: Schema<*>,
        baseBody: Map<String, Any>,
        requiredFields: List<String>,
    ): List<AutoTestCase> =
        registry
            .getInvalidTestProviders()
            .filter { it.canHandle(schema) }
            .flatMap { provider ->
                provider.generateInvalidValues(fieldName, schema).map { invalidValue ->
                    createInvalidTestCase(
                        fieldName = fieldName,
                        invalidValue = invalidValue.value,
                        description = invalidValue.description,
                        baseBody = baseBody,
                        invalidType = provider.testType,
                    )
                }
            }

    private fun createInvalidTestCase(
        fieldName: String,
        invalidValue: Any?,
        description: String,
        baseBody: Map<String, Any>,
        location: ParameterLocation = ParameterLocation.BODY,
        basePathParams: Map<String, Any?> = emptyMap(),
        baseHeaders: Map<String, String> = emptyMap(),
        invalidType: String = deriveInvalidType(description),
    ): AutoTestCase {
        val tag = "Invalid request - $invalidType"
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
                    tag = tag,
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
                    tag = tag,
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
                    tag = tag,
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
                    tag = tag,
                )
            }
        }
    }

    /**
     * Derive the invalid type from the description for tagging purposes.
     */
    private fun deriveInvalidType(description: String): String {
        val lowerDesc = description.lowercase()
        return when {
            lowerDesc.contains("minlength") || lowerDesc.contains("shorter than") -> "minLength"
            lowerDesc.contains("maxlength") || lowerDesc.contains("longer than") -> "maxLength"
            lowerDesc.contains("pattern") -> "pattern"
            lowerDesc.contains("format") -> "format"
            lowerDesc.contains("enum") -> "enum"
            lowerDesc.contains("minimum") || lowerDesc.contains("below minimum") -> "minimum"
            lowerDesc.contains("maximum") || lowerDesc.contains("above maximum") -> "maximum"
            lowerDesc.contains("type") || lowerDesc.contains("not-a-") -> "type"
            lowerDesc.contains("required") || lowerDesc.contains("missing") -> "required"
            lowerDesc.contains("minitems") || lowerDesc.contains("fewer items") -> "minItems"
            lowerDesc.contains("maxitems") || lowerDesc.contains("more items") -> "maxItems"
            lowerDesc.contains("boolean") -> "type"
            lowerDesc.contains("empty") -> "empty"
            else -> "constraint"
        }
    }

    /**
     * Generate security test cases using registered providers.
     */
    @Suppress("UNCHECKED_CAST")
    private fun generateSecurityTestCases(
        schema: Schema<*>,
        baseBody: Map<String, Any>,
    ): List<AutoTestCase> {
        val properties = schema.properties ?: return emptyList()

        // Find string fields to test
        val stringFields =
            properties
                .filter { (_, fieldSchema) ->
                    val resolved = resolveSchema(fieldSchema as Schema<*>)
                    resolved.type == "string" || resolved.type == null
                }.keys

        return stringFields.flatMap { fieldName ->
            registry
                .getSecurityTestProviders()
                .filter { ParameterLocation.BODY in it.applicableLocations() }
                .flatMap { provider ->
                    provider.generatePayloads().map { payload ->
                        val modifiedBody = baseBody.toMutableMap()
                        modifiedBody[fieldName] = payload.payload
                        AutoTestCase(
                            type = AutoTestType.SECURITY,
                            fieldName = fieldName,
                            invalidValue = payload.payload,
                            description = "${provider.displayName}: ${payload.name}",
                            body = modifiedBody,
                            tag = "security - ${provider.displayName}",
                        )
                    }
                }
        }
    }

    /**
     * Generate invalid tests for path parameters using providers.
     */
    private fun generatePathParamInvalidTests(
        pathParams: List<io.swagger.v3.oas.models.parameters.Parameter>,
        baseBody: Map<String, Any>,
        basePathParams: Map<String, Any?>,
    ): List<AutoTestCase> =
        pathParams.flatMap { param ->
            val schema = param.schema ?: return@flatMap emptyList()
            val resolvedSchema = resolveSchema(schema)

            registry
                .getInvalidTestProviders()
                .filter { it.canHandle(resolvedSchema) }
                .flatMap { provider ->
                    provider.generateInvalidValues(param.name, resolvedSchema).map { invalidValue ->
                        createInvalidTestCase(
                            fieldName = param.name,
                            invalidValue = invalidValue.value,
                            description = invalidValue.description,
                            baseBody = baseBody,
                            location = ParameterLocation.PATH,
                            basePathParams = basePathParams,
                            invalidType = provider.testType,
                        )
                    }
                }
        }

    /**
     * Generate security tests for path parameters using providers.
     */
    private fun generatePathParamSecurityTests(
        pathParams: List<io.swagger.v3.oas.models.parameters.Parameter>,
        baseBody: Map<String, Any>,
        basePathParams: Map<String, Any?>,
    ): List<AutoTestCase> =
        pathParams.flatMap { param ->
            registry
                .getSecurityTestProviders()
                .filter { ParameterLocation.PATH in it.applicableLocations() }
                .flatMap { provider ->
                    provider.generatePayloads().map { payload ->
                        AutoTestCase(
                            type = AutoTestType.SECURITY,
                            fieldName = param.name,
                            invalidValue = payload.payload,
                            description = "${provider.displayName}: ${payload.name}",
                            location = ParameterLocation.PATH,
                            body = baseBody,
                            pathParams = basePathParams.toMutableMap().apply { this[param.name] = payload.payload },
                            tag = "security - ${provider.displayName}",
                        )
                    }
                }
        }

    /**
     * Generate invalid tests for header parameters using providers.
     */
    private fun generateHeaderInvalidTests(
        headerParams: List<io.swagger.v3.oas.models.parameters.Parameter>,
        baseBody: Map<String, Any>,
        baseHeaders: Map<String, String>,
    ): List<AutoTestCase> =
        headerParams.flatMap { param ->
            val schema = param.schema ?: return@flatMap emptyList()
            val resolvedSchema = resolveSchema(schema)

            registry
                .getInvalidTestProviders()
                .filter { it.canHandle(resolvedSchema) }
                .flatMap { provider ->
                    provider.generateInvalidValues(param.name, resolvedSchema).map { invalidValue ->
                        createInvalidTestCase(
                            fieldName = param.name,
                            invalidValue = invalidValue.value,
                            description = invalidValue.description,
                            baseBody = baseBody,
                            location = ParameterLocation.HEADER,
                            baseHeaders = baseHeaders,
                            invalidType = provider.testType,
                        )
                    }
                }
        }

    /**
     * Generate security tests for header parameters using providers.
     */
    private fun generateHeaderSecurityTests(
        headerParams: List<io.swagger.v3.oas.models.parameters.Parameter>,
        baseBody: Map<String, Any>,
        baseHeaders: Map<String, String>,
    ): List<AutoTestCase> =
        headerParams.flatMap { param ->
            registry
                .getSecurityTestProviders()
                .filter { ParameterLocation.HEADER in it.applicableLocations() }
                .flatMap { provider ->
                    provider.generatePayloads().map { payload ->
                        AutoTestCase(
                            type = AutoTestType.SECURITY,
                            fieldName = param.name,
                            invalidValue = payload.payload,
                            description = "${provider.displayName}: ${payload.name}",
                            location = ParameterLocation.HEADER,
                            body = baseBody,
                            headers = baseHeaders.toMutableMap().apply { this[param.name] = payload.payload },
                            tag = "security - ${provider.displayName}",
                        )
                    }
                }
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

    private val sqlInjectionPatterns =
        listOf(
            SecurityPattern("SQL Injection", "Single quote", "' OR '1'='1"),
            SecurityPattern("SQL Injection", "Union select", "' UNION SELECT * FROM users--"),
            SecurityPattern("SQL Injection", "Comment bypass", "admin'--"),
            SecurityPattern("SQL Injection", "Boolean-based", "1' AND '1'='1"),
            SecurityPattern("SQL Injection", "Stacked queries", "'; DROP TABLE users;--"),
        )

    private val xssPatterns =
        listOf(
            SecurityPattern("XSS", "Script tag", "<script>alert('XSS')</script>"),
            SecurityPattern("XSS", "Event handler", "<img src=x onerror=alert('XSS')>"),
            SecurityPattern("XSS", "SVG onload", "<svg onload=alert('XSS')>"),
            SecurityPattern("XSS", "JavaScript URL", "javascript:alert('XSS')"),
            SecurityPattern("XSS", "HTML injection", "<h1>Injected</h1>"),
        )

    private val pathTraversalPatterns =
        listOf(
            SecurityPattern("Path Traversal", "Unix relative", "../../../etc/passwd"),
            SecurityPattern("Path Traversal", "Windows relative", "..\\..\\..\\windows\\system32\\config\\sam"),
            SecurityPattern("Path Traversal", "URL encoded", "..%2F..%2F..%2Fetc%2Fpasswd"),
            SecurityPattern("Path Traversal", "Double encoded", "..%252F..%252F..%252Fetc%252Fpasswd"),
        )

    private val commandInjectionPatterns =
        listOf(
            SecurityPattern("Command Injection", "Unix semicolon", "; ls -la"),
            SecurityPattern("Command Injection", "Unix pipe", "| cat /etc/passwd"),
            SecurityPattern("Command Injection", "Unix backtick", "`id`"),
            SecurityPattern("Command Injection", "Unix subshell", "$(whoami)"),
            SecurityPattern("Command Injection", "Windows ampersand", "& dir"),
        )

    private val ldapInjectionPatterns =
        listOf(
            SecurityPattern("LDAP Injection", "Wildcard", "*"),
            SecurityPattern("LDAP Injection", "Filter bypass", "admin)(&)"),
        )

    private val xmlInjectionPatterns =
        listOf(
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
    fun getPathTraversalPatterns(): List<SecurityPattern> = pathTraversalPatterns + commandInjectionPatterns.take(3)

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
