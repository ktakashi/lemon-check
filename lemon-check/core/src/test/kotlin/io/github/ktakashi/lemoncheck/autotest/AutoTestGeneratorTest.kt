package io.github.ktakashi.lemoncheck.autotest

import io.github.ktakashi.lemoncheck.openapi.OpenApiLoader
import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
import io.github.ktakashi.lemoncheck.scenario.AutoTestType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AutoTestGeneratorTest {
    private val loader = OpenApiLoader()
    private val specPath =
        javaClass.getResource("/petstore.yaml")?.path
            ?: error("petstore.yaml not found in test resources")
    private val openApi = loader.load(specPath)

    @Test
    fun `should create generator from OpenAPI directly`() {
        val generator = AutoTestGenerator(openApi)
        assertNotNull(generator)
    }

    @Test
    fun `should create generator from SpecRegistry default`() {
        val registry = SpecRegistry()
        registry.registerDefault(specPath)

        val generator = AutoTestGenerator.fromRegistry(registry)
        assertNotNull(generator)
    }

    @Test
    fun `should create generator from SpecRegistry by name`() {
        val registry = SpecRegistry()
        registry.register("petstore", specPath)

        val generator = AutoTestGenerator.fromRegistry(registry, "petstore")
        assertNotNull(generator)
    }

    @Test
    fun `should create generator from LoadedSpec`() {
        val registry = SpecRegistry()
        registry.registerDefault(specPath)

        val generator = AutoTestGenerator.fromSpec(registry.getDefault())
        assertNotNull(generator)
    }

    @Test
    fun `should generate invalid test cases for createPet operation`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.INVALID),
            )

        assertTrue(testCases.isNotEmpty(), "Should generate at least one invalid test case")
        assertTrue(testCases.all { it.type == AutoTestType.INVALID })
        assertTrue(testCases.all { it.tag.startsWith("Invalid request - ") })
    }

    @Test
    fun `should generate security test cases for createPet operation`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.SECURITY),
            )

        assertTrue(testCases.isNotEmpty(), "Should generate at least one security test case")
        assertTrue(testCases.all { it.type == AutoTestType.SECURITY })
        assertTrue(testCases.all { it.tag == "security" })
    }

    @Test
    fun `should generate both invalid and security test cases`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.INVALID, AutoTestType.SECURITY),
            )

        assertTrue(testCases.isNotEmpty())
        assertTrue(testCases.any { it.type == AutoTestType.INVALID })
        assertTrue(testCases.any { it.type == AutoTestType.SECURITY })
    }

    @Test
    fun `should generate test for missing required field`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.INVALID),
            )

        // NewPet schema has 'name' as required
        val missingNameTest =
            testCases.find {
                it.fieldName == "name" && it.invalidValue == null
            }
        assertNotNull(missingNameTest, "Should generate test for missing required 'name' field")
        assertTrue(missingNameTest.description.contains("required", ignoreCase = true))
    }

    @Test
    fun `should generate test for minLength violation`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.INVALID),
            )

        // NewPet.name has minLength: 1
        val minLengthTest =
            testCases.find {
                it.fieldName == "name" && it.description.contains("minLength", ignoreCase = true)
            }
        assertNotNull(minLengthTest, "Should generate test for minLength violation on 'name' field")
        assertEquals("", minLengthTest.invalidValue, "Invalid value should be empty string for minLength=1")
    }

    @Test
    fun `should generate test for maxLength violation`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.INVALID),
            )

        // NewPet.name has maxLength: 100
        val maxLengthTest =
            testCases.find {
                it.fieldName == "name" && it.description.contains("maxLength", ignoreCase = true)
            }
        assertNotNull(maxLengthTest, "Should generate test for maxLength violation on 'name' field")
        assertTrue(
            (maxLengthTest.invalidValue as String).length > 100,
            "Invalid value should exceed maxLength",
        )
    }

    @Test
    fun `should generate test for enum violation`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.INVALID),
            )

        // NewPet.status has enum: [available, pending, sold]
        val enumTest =
            testCases.find {
                it.fieldName == "status" && it.description.contains("enum", ignoreCase = true)
            }
        assertNotNull(enumTest, "Should generate test for enum violation on 'status' field")
        assertTrue(
            (enumTest.invalidValue as String).startsWith("INVALID_ENUM_VALUE_"),
            "Invalid value should be invalid enum",
        )
    }

    @Test
    fun `should generate test for minimum violation`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.INVALID),
            )

        // NewPet.price has minimum: 0
        val minimumTest =
            testCases.find {
                it.fieldName == "price" && it.description.contains("minimum", ignoreCase = true)
            }
        assertNotNull(minimumTest, "Should generate test for minimum violation on 'price' field")
    }

    @Test
    fun `should include base body values in generated tests`() {
        val generator = AutoTestGenerator(openApi)
        val baseBody = mapOf("category" to "dog", "tags" to listOf("cute"))

        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.INVALID),
                baseBody = baseBody,
            )

        // Find a test case that modifies name but should keep category and tags
        val nameTest =
            testCases.find {
                it.fieldName == "name" && it.invalidValue != null
            }
        assertNotNull(nameTest, "Should have a test modifying name")
        assertEquals("dog", nameTest.body["category"], "Should preserve base body 'category'")
        assertEquals(listOf("cute"), nameTest.body["tags"], "Should preserve base body 'tags'")
    }

    @Test
    fun `should return empty list for operation without request body`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "listPets",
                testTypes = setOf(AutoTestType.INVALID),
            )

        assertTrue(testCases.isEmpty(), "GET listPets has no request body, should return empty list")
    }

    @Test
    fun `should return empty list for non-existent operation`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "nonExistentOperation",
                testTypes = setOf(AutoTestType.INVALID),
            )

        assertTrue(testCases.isEmpty(), "Non-existent operation should return empty list")
    }

    @Test
    fun `should generate SQL injection test cases`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.SECURITY),
            )

        val sqlInjectionTests =
            testCases.filter {
                it.description.contains("SQL Injection", ignoreCase = true)
            }
        assertTrue(sqlInjectionTests.isNotEmpty(), "Should generate SQL injection tests")
    }

    @Test
    fun `should generate XSS test cases`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.SECURITY),
            )

        val xssTests =
            testCases.filter {
                it.description.contains("XSS", ignoreCase = true)
            }
        assertTrue(xssTests.isNotEmpty(), "Should generate XSS tests")
    }

    @Test
    fun `should generate path traversal test cases`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.SECURITY),
            )

        val pathTraversalTests =
            testCases.filter {
                it.description.contains("Path Traversal", ignoreCase = true)
            }
        assertTrue(pathTraversalTests.isNotEmpty(), "Should generate path traversal tests")
    }

    @Test
    fun `should generate command injection test cases`() {
        val generator = AutoTestGenerator(openApi)
        val testCases =
            generator.generateTestCases(
                operationId = "createPet",
                testTypes = setOf(AutoTestType.SECURITY),
            )

        val commandInjectionTests =
            testCases.filter {
                it.description.contains("Command Injection", ignoreCase = true)
            }
        assertTrue(commandInjectionTests.isNotEmpty(), "Should generate command injection tests")
    }

    @Test
    fun `SecurityTestPatterns should have all categories`() {
        val patterns = SecurityTestPatterns.getAllPatterns()

        val categories = patterns.map { it.category }.toSet()
        assertTrue("SQL Injection" in categories)
        assertTrue("XSS" in categories)
        assertTrue("Path Traversal" in categories)
        assertTrue("Command Injection" in categories)
        assertTrue("LDAP Injection" in categories)
        assertTrue("XXE" in categories)
    }

    @Test
    fun `AutoTestCase should have correct structure`() {
        val testCase =
            AutoTestCase(
                type = AutoTestType.INVALID,
                fieldName = "testField",
                invalidValue = "testValue",
                description = "Test description",
                body = mapOf("testField" to "testValue"),
                tag = "invalid",
            )

        assertEquals(AutoTestType.INVALID, testCase.type)
        assertEquals("testField", testCase.fieldName)
        assertEquals("testValue", testCase.invalidValue)
        assertEquals("Test description", testCase.description)
        assertEquals(mapOf("testField" to "testValue"), testCase.body)
        assertEquals("invalid", testCase.tag)
    }
}
