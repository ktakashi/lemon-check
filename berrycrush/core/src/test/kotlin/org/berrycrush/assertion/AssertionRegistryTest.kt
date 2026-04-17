package org.berrycrush.assertion

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [DefaultAssertionRegistry] and [AnnotationAssertionScanner].
 */
@DisplayName("Assertion System")
class AssertionRegistryTest {
    private lateinit var registry: DefaultAssertionRegistry

    @BeforeEach
    fun setup() {
        registry = DefaultAssertionRegistry()
    }

    @Nested
    @DisplayName("DefaultAssertionRegistry")
    inner class RegistryTests {
        @Test
        @DisplayName("should register and find simple assertion")
        fun registerAndFindSimple() {
            val instance =
                object {
                    @Assertion("the result should be valid")
                    fun assertValid(): AssertionResult = AssertionResult.passed()
                }

            val definition =
                AssertionDefinition(
                    pattern = "the result should be valid",
                    method = instance.javaClass.getMethod("assertValid"),
                    instance = instance,
                )
            registry.register(definition)

            val match = registry.findMatch("the result should be valid")

            assertNotNull(match)
            assertEquals(definition, match.definition)
            assertTrue(match.parameters.isEmpty())
        }

        @Test
        @DisplayName("should extract string parameter")
        fun extractStringParameter() {
            val instance =
                object {
                    @Assertion("the name should be {string}")
                    fun assertName(expected: String): AssertionResult = AssertionResult.passed()
                }

            val definition =
                AssertionDefinition(
                    pattern = "the name should be {string}",
                    method = instance.javaClass.getMethod("assertName", String::class.java),
                    instance = instance,
                )
            registry.register(definition)

            val match = registry.findMatch("""the name should be "John"""")

            assertNotNull(match)
            assertEquals(1, match.parameters.size)
            assertEquals("John", match.parameters[0])
        }

        @Test
        @DisplayName("should extract int parameter")
        fun extractIntParameter() {
            val instance =
                object {
                    @Assertion("the count should be {int}")
                    fun assertCount(expected: Int): AssertionResult = AssertionResult.passed()
                }

            val definition =
                AssertionDefinition(
                    pattern = "the count should be {int}",
                    method = instance.javaClass.getMethod("assertCount", Int::class.java),
                    instance = instance,
                )
            registry.register(definition)

            val match = registry.findMatch("the count should be 42")

            assertNotNull(match)
            assertEquals(1, match.parameters.size)
            assertEquals(42, match.parameters[0])
        }

        @Test
        @DisplayName("should extract multiple parameters")
        fun extractMultipleParameters() {
            val instance =
                object {
                    @Assertion("the {word} with ID {int} should have status {string}")
                    fun assertStatus(
                        entity: String,
                        id: Int,
                        status: String,
                    ): AssertionResult = AssertionResult.passed()
                }

            val definition =
                AssertionDefinition(
                    pattern = "the {word} with ID {int} should have status {string}",
                    method =
                        instance.javaClass.getMethod(
                            "assertStatus",
                            String::class.java,
                            Int::class.java,
                            String::class.java,
                        ),
                    instance = instance,
                )
            registry.register(definition)

            val match = registry.findMatch("""the order with ID 123 should have status "completed"""")

            assertNotNull(match)
            assertEquals(3, match.parameters.size)
            assertEquals("order", match.parameters[0])
            assertEquals(123, match.parameters[1])
            assertEquals("completed", match.parameters[2])
        }

        @Test
        @DisplayName("should return null for no match")
        fun returnNullForNoMatch() {
            val match = registry.findMatch("some unregistered assertion")

            assertNull(match)
        }

        @Test
        @DisplayName("should register multiple assertions")
        fun registerMultipleAssertions() {
            val instance =
                object {
                    @Assertion("first assertion")
                    fun first(): AssertionResult = AssertionResult.passed()

                    @Assertion("second assertion")
                    fun second(): AssertionResult = AssertionResult.passed()
                }

            registry.register(
                AssertionDefinition(
                    pattern = "first assertion",
                    method = instance.javaClass.getMethod("first"),
                    instance = instance,
                ),
            )
            registry.register(
                AssertionDefinition(
                    pattern = "second assertion",
                    method = instance.javaClass.getMethod("second"),
                    instance = instance,
                ),
            )

            assertEquals(2, registry.allDefinitions().size)
            assertNotNull(registry.findMatch("first assertion"))
            assertNotNull(registry.findMatch("second assertion"))
        }

        @Test
        @DisplayName("should clear all assertions")
        fun clearAllAssertions() {
            val instance =
                object {
                    @Assertion("test assertion")
                    fun test(): AssertionResult = AssertionResult.passed()
                }

            registry.register(
                AssertionDefinition(
                    pattern = "test assertion",
                    method = instance.javaClass.getMethod("test"),
                    instance = instance,
                ),
            )
            assertEquals(1, registry.allDefinitions().size)

            registry.clear()

            assertEquals(0, registry.allDefinitions().size)
            assertNull(registry.findMatch("test assertion"))
        }
    }

    @Nested
    @DisplayName("AnnotationAssertionScanner")
    inner class ScannerTests {
        @Test
        @DisplayName("should scan class for @Assertion methods")
        fun scanClassForAssertions() {
            val scanner = AnnotationAssertionScanner()
            val definitions = scanner.scan(SampleAssertions::class.java)

            assertEquals(2, definitions.size)
            assertTrue(definitions.any { it.pattern == "the status should be {string}" })
            assertTrue(definitions.any { it.pattern == "the count should be greater than {int}" })
        }

        @Test
        @DisplayName("should scan class with instance")
        fun scanClassWithInstance() {
            val scanner = AnnotationAssertionScanner()
            val instance = SampleAssertions()
            val definitions = scanner.scan(SampleAssertions::class.java, instance)

            assertEquals(2, definitions.size)
            definitions.forEach { assertEquals(instance, it.instance) }
        }

        @Test
        @DisplayName("should ignore non-annotated methods")
        fun ignoreNonAnnotatedMethods() {
            val scanner = AnnotationAssertionScanner()
            val definitions = scanner.scan(MixedAssertions::class.java)

            assertEquals(1, definitions.size)
            assertEquals("annotated assertion", definitions[0].pattern)
        }

        @Test
        @DisplayName("should scan multiple classes")
        fun scanMultipleClasses() {
            val scanner = AnnotationAssertionScanner()
            val definitions = scanner.scanAll(SampleAssertions::class.java, SingleAssertion::class.java)

            assertEquals(3, definitions.size)
        }
    }

    @Nested
    @DisplayName("AssertionResult")
    inner class ResultTests {
        @Test
        @DisplayName("should create passed result")
        fun createPassedResult() {
            val result = AssertionResult.passed()

            assertTrue(result.passed)
            assertNull(result.message)
        }

        @Test
        @DisplayName("should create passed result with message")
        fun createPassedWithMessage() {
            val result = AssertionResult.passed("Validation successful")

            assertTrue(result.passed)
            assertEquals("Validation successful", result.message)
        }

        @Test
        @DisplayName("should create failed result")
        fun createFailedResult() {
            val result = AssertionResult.failed("Expected 10 but got 5")

            assertTrue(!result.passed)
            assertEquals("Expected 10 but got 5", result.message)
        }

        @Test
        @DisplayName("should create failed result with comparison values")
        fun createFailedWithComparison() {
            val result =
                AssertionResult.failed(
                    message = "Values don't match",
                    expectedValue = 10,
                    actualValue = 5,
                )

            assertTrue(!result.passed)
            assertEquals("Values don't match", result.message)
            assertEquals(10, result.expectedValue)
            assertEquals(5, result.actualValue)
        }
    }

    // Test classes for scanner tests

    class SampleAssertions {
        @Assertion("the status should be {string}")
        fun assertStatus(expected: String): AssertionResult = AssertionResult.passed()

        @Assertion("the count should be greater than {int}")
        fun assertCountGreater(min: Int): AssertionResult = AssertionResult.passed()
    }

    class MixedAssertions {
        @Assertion("annotated assertion")
        fun annotated(): AssertionResult = AssertionResult.passed()

        fun notAnnotated(): AssertionResult = AssertionResult.passed()

        fun helper(): Unit = Unit
    }

    class SingleAssertion {
        @Assertion("single assertion pattern")
        fun single(): AssertionResult = AssertionResult.passed()
    }
}
