package io.github.ktakashi.lemoncheck.autotest.provider

import io.github.ktakashi.lemoncheck.autotest.AutoTestGenerator
import io.github.ktakashi.lemoncheck.autotest.ParameterLocation
import io.github.ktakashi.lemoncheck.openapi.OpenApiLoader
import io.github.ktakashi.lemoncheck.scenario.AutoTestType
import io.swagger.v3.oas.models.media.Schema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutoTestProviderRegistryTest {
    private val loader = OpenApiLoader()
    private val specPath =
        javaClass.getResource("/petstore.yaml")?.path
            ?: error("petstore.yaml not found in test resources")
    private val openApi = loader.load(specPath)

    @Test
    fun `default registry should have all built-in invalid providers`() {
        val registry = AutoTestProviderRegistry.default

        val expectedTypes =
            setOf(
                "minLength",
                "maxLength",
                "pattern",
                "format",
                "enum",
                "minimum",
                "maximum",
                "type",
                "required",
                "minItems",
                "maxItems",
            )

        expectedTypes.forEach { type ->
            assertTrue(
                registry.hasInvalidTestType(type),
                "Registry should have invalid test type: $type",
            )
        }
    }

    @Test
    fun `default registry should have all built-in security providers`() {
        val registry = AutoTestProviderRegistry.default

        val expectedTypes =
            setOf(
                "SQLInjection",
                "XSS",
                "PathTraversal",
                "CommandInjection",
                "LDAPInjection",
                "XXE",
                "HeaderInjection",
            )

        expectedTypes.forEach { type ->
            assertTrue(
                registry.hasSecurityTestType(type),
                "Registry should have security test type: $type",
            )
        }
    }

    @Test
    fun `custom invalid provider should override built-in with same testType`() {
        val registry = AutoTestProviderRegistry.withDefaults()

        // Verify built-in provider exists
        val builtInProvider = registry.getInvalidTestProvider("minLength")
        assertNotNull(builtInProvider, "Built-in minLength provider should exist")

        // Create custom provider with same testType but higher priority
        val customProvider =
            object : InvalidTestProvider {
                override val testType: String = "minLength"
                override val priority: Int = 100

                override fun canHandle(schema: Schema<*>): Boolean = schema.type == "string" && schema.minLength != null

                override fun generateInvalidValues(
                    fieldName: String,
                    schema: Schema<*>,
                ): List<InvalidTestValue> =
                    listOf(
                        InvalidTestValue(
                            value = "CUSTOM_MIN_LENGTH_VALUE",
                            description = "Custom minLength violation",
                        ),
                    )
            }

        registry.registerInvalid(customProvider)

        val registeredProvider = registry.getInvalidTestProvider("minLength")
        assertEquals(customProvider, registeredProvider, "Custom provider should override built-in")
    }

    @Test
    fun `custom security provider should override built-in with same testType`() {
        val registry = AutoTestProviderRegistry.withDefaults()

        // Create custom provider with same testType but higher priority
        val customProvider =
            object : SecurityTestProvider {
                override val testType: String = "SQLInjection"
                override val displayName: String = "Custom SQL Injection"
                override val priority: Int = 100

                override fun applicableLocations(): Set<ParameterLocation> = setOf(ParameterLocation.BODY)

                override fun generatePayloads(): List<SecurityPayload> =
                    listOf(
                        SecurityPayload("Custom payload", "CUSTOM_SQL_INJECTION"),
                    )
            }

        registry.registerSecurity(customProvider)

        val registeredProvider = registry.getSecurityTestProvider("SQLInjection")
        assertEquals(customProvider, registeredProvider, "Custom provider should override built-in")
    }

    @Test
    fun `lower priority provider should not override higher priority`() {
        val registry = AutoTestProviderRegistry.empty()

        // Register high priority provider first
        val highPriority =
            object : InvalidTestProvider {
                override val testType: String = "custom"
                override val priority: Int = 100

                override fun canHandle(schema: Schema<*>): Boolean = true

                override fun generateInvalidValues(
                    fieldName: String,
                    schema: Schema<*>,
                ): List<InvalidTestValue> =
                    listOf(
                        InvalidTestValue("high", "High priority"),
                    )
            }

        // Register low priority provider second
        val lowPriority =
            object : InvalidTestProvider {
                override val testType: String = "custom"
                override val priority: Int = 50

                override fun canHandle(schema: Schema<*>): Boolean = true

                override fun generateInvalidValues(
                    fieldName: String,
                    schema: Schema<*>,
                ): List<InvalidTestValue> =
                    listOf(
                        InvalidTestValue("low", "Low priority"),
                    )
            }

        registry.registerInvalid(highPriority)
        registry.registerInvalid(lowPriority)

        val registered = registry.getInvalidTestProvider("custom")
        assertEquals(highPriority, registered, "Higher priority should remain registered")
    }

    @Test
    fun `empty registry should have no providers`() {
        val registry = AutoTestProviderRegistry.empty()

        assertTrue(
            registry.getInvalidTestProviders().isEmpty(),
            "Empty registry should have no invalid providers",
        )
        assertTrue(
            registry.getSecurityTestProviders().isEmpty(),
            "Empty registry should have no security providers",
        )
    }

    @Test
    fun `generator should use custom providers from registry`() {
        val registry = AutoTestProviderRegistry.empty()

        // Add a custom invalid provider
        registry.registerInvalid(
            object : InvalidTestProvider {
                override val testType: String = "customTest"
                override val priority: Int = 100

                override fun canHandle(schema: Schema<*>): Boolean = schema.type == "string"

                override fun generateInvalidValues(
                    fieldName: String,
                    schema: Schema<*>,
                ): List<InvalidTestValue> =
                    listOf(
                        InvalidTestValue(
                            value = "CUSTOM_INVALID_VALUE",
                            description = "Custom invalid test",
                        ),
                    )
            },
        )

        val generator = AutoTestGenerator(openApi, registry)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.INVALID),
            )

        assertTrue(testCases.isNotEmpty(), "Should generate test cases with custom provider")
        assertTrue(
            testCases.any { it.tag == "Invalid request - customTest" },
            "Should have test case from custom provider",
        )
    }

    @Test
    fun `generator should use custom security provider displayName`() {
        val registry = AutoTestProviderRegistry.empty()

        // Add a custom security provider
        registry.registerSecurity(
            object : SecurityTestProvider {
                override val testType: String = "customSecurity"
                override val displayName: String = "Custom Security Test"
                override val priority: Int = 100

                override fun applicableLocations(): Set<ParameterLocation> = setOf(ParameterLocation.BODY)

                override fun generatePayloads(): List<SecurityPayload> =
                    listOf(
                        SecurityPayload("Test payload", "CUSTOM_PAYLOAD"),
                    )
            },
        )

        val generator = AutoTestGenerator(openApi, registry)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.SECURITY),
            )

        assertTrue(testCases.isNotEmpty(), "Should generate security test cases")
        assertTrue(
            testCases.any { it.description.contains("Custom Security Test") },
            "Should use displayName in description",
        )
        assertTrue(
            testCases.any { it.tag == "security - Custom Security Test" },
            "Should use displayName in tag",
        )
    }

    @Test
    fun `getAllTestTypes should return all registered types`() {
        val registry = AutoTestProviderRegistry.withDefaults()

        val allTypes = registry.getAllTestTypes()

        assertTrue(allTypes.contains("minLength"), "Should contain minLength")
        assertTrue(allTypes.contains("SQLInjection"), "Should contain SQLInjection")
        assertTrue(allTypes.size >= 18, "Should have at least 18 test types (11 invalid + 7 security)")
    }

    @Test
    fun `withDefaults should create fresh registry with only built-in providers`() {
        val registry1 = AutoTestProviderRegistry.withDefaults()
        val registry2 = AutoTestProviderRegistry.withDefaults()

        // Add custom provider to registry1
        registry1.registerInvalid(
            object : InvalidTestProvider {
                override val testType: String = "custom"

                override fun canHandle(schema: Schema<*>): Boolean = true

                override fun generateInvalidValues(
                    fieldName: String,
                    schema: Schema<*>,
                ): List<InvalidTestValue> = emptyList()
            },
        )

        // registry2 should not have the custom provider
        assertTrue(
            registry1.hasInvalidTestType("custom"),
            "registry1 should have custom type",
        )
        assertTrue(
            !registry2.hasInvalidTestType("custom"),
            "registry2 should not have custom type",
        )
    }
}
