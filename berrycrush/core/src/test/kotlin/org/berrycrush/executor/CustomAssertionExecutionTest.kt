package org.berrycrush.executor

import org.berrycrush.assertion.Assertion
import org.berrycrush.assertion.AssertionContext
import org.berrycrush.assertion.AssertionDefinition
import org.berrycrush.assertion.AssertionMatch
import org.berrycrush.assertion.AssertionResult
import org.berrycrush.assertion.DefaultAssertionRegistry
import org.berrycrush.config.BerryCrushConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for custom assertions in the AssertionRegistry.
 * These tests verify that assertion registration, pattern matching, and invocation
 * work correctly without requiring a running HTTP server.
 */
class CustomAssertionExecutionTest {
    private lateinit var assertionRegistry: DefaultAssertionRegistry
    private lateinit var config: BerryCrushConfiguration

    @BeforeEach
    fun setup() {
        assertionRegistry = DefaultAssertionRegistry()
        config = BerryCrushConfiguration()
    }

    @Nested
    @DisplayName("Assertion registration")
    inner class AssertionRegistration {
        @Test
        @DisplayName("should register and find simple assertion pattern")
        fun registerAndFindSimplePattern() {
            val assertionInstance = object {
                @Assertion("the system is ready")
                fun systemIsReady(context: AssertionContext): AssertionResult {
                    return AssertionResult.passed()
                }
            }

            val definition = AssertionDefinition(
                pattern = "the system is ready",
                method = assertionInstance.javaClass.getMethod("systemIsReady", AssertionContext::class.java),
                instance = assertionInstance,
            )
            assertionRegistry.register(definition)

            val match = assertionRegistry.findMatch("the system is ready")

            assertNotNull(match, "Should find registered assertion")
            assertEquals("the system is ready", match.definition.pattern)
        }

        @Test
        @DisplayName("should return null for unregistered pattern")
        fun returnNullForUnregisteredPattern() {
            val match = assertionRegistry.findMatch("unknown pattern")
            assertNull(match, "Should return null for unregistered pattern")
        }

        @Test
        @DisplayName("should extract string parameter from pattern")
        fun extractStringParameter() {
            val assertionInstance = object {
                @Assertion("the user {string} is logged in")
                fun userIsLoggedIn(name: String, context: AssertionContext): AssertionResult {
                    return AssertionResult.passed()
                }
            }

            val definition = AssertionDefinition(
                pattern = "the user {string} is logged in",
                method = assertionInstance.javaClass.getMethod(
                    "userIsLoggedIn",
                    String::class.java,
                    AssertionContext::class.java,
                ),
                instance = assertionInstance,
            )
            assertionRegistry.register(definition)

            val match = assertionRegistry.findMatch("the user \"admin\" is logged in")

            assertNotNull(match, "Should match pattern with string parameter")
            assertEquals(1, match.parameters.size)
            assertEquals("admin", match.parameters[0])
        }

        @Test
        @DisplayName("should extract int parameter from pattern")
        fun extractIntParameter() {
            val assertionInstance = object {
                @Assertion("there are {int} items")
                fun itemCount(count: Int, context: AssertionContext): AssertionResult {
                    return AssertionResult.passed()
                }
            }

            val definition = AssertionDefinition(
                pattern = "there are {int} items",
                method = assertionInstance.javaClass.getMethod(
                    "itemCount",
                    Int::class.java,
                    AssertionContext::class.java,
                ),
                instance = assertionInstance,
            )
            assertionRegistry.register(definition)

            val match = assertionRegistry.findMatch("there are 5 items")

            assertNotNull(match, "Should match pattern with int parameter")
            assertEquals(1, match.parameters.size)
            assertEquals(5, match.parameters[0])
        }

        @Test
        @DisplayName("should extract multiple parameters from pattern")
        fun extractMultipleParameters() {
            val assertionInstance = object {
                @Assertion("the {string} has {int} elements")
                fun collectionSize(name: String, size: Int, context: AssertionContext): AssertionResult {
                    return AssertionResult.passed()
                }
            }

            val definition = AssertionDefinition(
                pattern = "the {string} has {int} elements",
                method = assertionInstance.javaClass.getMethod(
                    "collectionSize",
                    String::class.java,
                    Int::class.java,
                    AssertionContext::class.java,
                ),
                instance = assertionInstance,
            )
            assertionRegistry.register(definition)

            val match = assertionRegistry.findMatch("the \"items\" has 10 elements")

            assertNotNull(match, "Should match pattern with multiple parameters")
            assertEquals(2, match.parameters.size)
            assertEquals("items", match.parameters[0])
            assertEquals(10, match.parameters[1])
        }
    }

    @Nested
    @DisplayName("Assertion invocation")
    inner class AssertionInvocation {
        @Test
        @DisplayName("should invoke assertion method and get passed result")
        fun invokeAssertionMethodPassed() {
            var invoked = false

            val assertionInstance = object {
                @Assertion("assertion passes")
                fun assertionPasses(context: AssertionContext): AssertionResult {
                    invoked = true
                    return AssertionResult.passed()
                }
            }

            val definition = AssertionDefinition(
                pattern = "assertion passes",
                method = assertionInstance.javaClass.getMethod("assertionPasses", AssertionContext::class.java),
                instance = assertionInstance,
            )
            assertionRegistry.register(definition)

            val match = assertionRegistry.findMatch("assertion passes")!!
            val context = createMockContext()

            val result = invokeAssertion(match, context)

            assertTrue(invoked, "Assertion method should be invoked")
            assertTrue(result.passed, "Assertion should pass")
        }

        @Test
        @DisplayName("should invoke assertion method and get failed result")
        fun invokeAssertionMethodFailed() {
            val assertionInstance = object {
                @Assertion("assertion fails")
                fun assertionFails(context: AssertionContext): AssertionResult {
                    return AssertionResult.failed("Expected failure", actualValue = 1, expectedValue = 2)
                }
            }

            val definition = AssertionDefinition(
                pattern = "assertion fails",
                method = assertionInstance.javaClass.getMethod("assertionFails", AssertionContext::class.java),
                instance = assertionInstance,
            )
            assertionRegistry.register(definition)

            val match = assertionRegistry.findMatch("assertion fails")!!
            val context = createMockContext()

            val result = invokeAssertion(match, context)

            assertTrue(!result.passed, "Assertion should fail")
            assertEquals("Expected failure", result.message)
            assertEquals(1, result.actualValue)
            assertEquals(2, result.expectedValue)
        }

        @Test
        @DisplayName("should pass extracted parameters to assertion method")
        fun passExtractedParameters() {
            var capturedName: String? = null
            var capturedCount: Int? = null

            val assertionInstance = object {
                @Assertion("user {string} has {int} items")
                fun userHasItems(name: String, count: Int, context: AssertionContext): AssertionResult {
                    capturedName = name
                    capturedCount = count
                    return AssertionResult.passed()
                }
            }

            val definition = AssertionDefinition(
                pattern = "user {string} has {int} items",
                method = assertionInstance.javaClass.getMethod(
                    "userHasItems",
                    String::class.java,
                    Int::class.java,
                    AssertionContext::class.java,
                ),
                instance = assertionInstance,
            )
            assertionRegistry.register(definition)

            val match = assertionRegistry.findMatch("user \"alice\" has 3 items")!!
            val context = createMockContext()

            invokeAssertion(match, context)

            assertEquals("alice", capturedName)
            assertEquals(3, capturedCount)
        }

        @Test
        @DisplayName("should handle assertion that throws exception")
        fun handleAssertionException() {
            val assertionInstance = object {
                @Assertion("this will throw")
                fun throwingAssertion(context: AssertionContext): AssertionResult {
                    throw RuntimeException("Unexpected error")
                }
            }

            val definition = AssertionDefinition(
                pattern = "this will throw",
                method = assertionInstance.javaClass.getMethod("throwingAssertion", AssertionContext::class.java),
                instance = assertionInstance,
            )
            assertionRegistry.register(definition)

            val match = assertionRegistry.findMatch("this will throw")!!
            val context = createMockContext()

            val result = invokeAssertionSafely(match, context)

            // When an exception is thrown, we should get a failed result
            assertTrue(!result.passed, "Assertion should fail when exception is thrown")
        }
    }

    @Nested
    @DisplayName("Multiple assertions")
    inner class MultipleAssertions {
        @Test
        @DisplayName("should register and match multiple assertions")
        fun registerMultipleAssertions() {
            val assertionInstance = object {
                @Assertion("first assertion")
                fun first(context: AssertionContext): AssertionResult = AssertionResult.passed()

                @Assertion("second assertion")
                fun second(context: AssertionContext): AssertionResult = AssertionResult.passed()

                @Assertion("third assertion")
                fun third(context: AssertionContext): AssertionResult = AssertionResult.passed()
            }

            assertionRegistry.register(
                AssertionDefinition(
                    pattern = "first assertion",
                    method = assertionInstance.javaClass.getMethod("first", AssertionContext::class.java),
                    instance = assertionInstance,
                ),
            )
            assertionRegistry.register(
                AssertionDefinition(
                    pattern = "second assertion",
                    method = assertionInstance.javaClass.getMethod("second", AssertionContext::class.java),
                    instance = assertionInstance,
                ),
            )
            assertionRegistry.register(
                AssertionDefinition(
                    pattern = "third assertion",
                    method = assertionInstance.javaClass.getMethod("third", AssertionContext::class.java),
                    instance = assertionInstance,
                ),
            )

            assertEquals(3, assertionRegistry.allDefinitions().size)
            assertNotNull(assertionRegistry.findMatch("first assertion"))
            assertNotNull(assertionRegistry.findMatch("second assertion"))
            assertNotNull(assertionRegistry.findMatch("third assertion"))
        }

        @Test
        @DisplayName("should clear all registered assertions")
        fun clearAssertions() {
            val assertionInstance = object {
                @Assertion("to be cleared")
                fun toBeCleared(context: AssertionContext): AssertionResult = AssertionResult.passed()
            }

            assertionRegistry.register(
                AssertionDefinition(
                    pattern = "to be cleared",
                    method = assertionInstance.javaClass.getMethod("toBeCleared", AssertionContext::class.java),
                    instance = assertionInstance,
                ),
            )

            assertEquals(1, assertionRegistry.allDefinitions().size)

            assertionRegistry.clear()

            assertEquals(0, assertionRegistry.allDefinitions().size)
            assertNull(assertionRegistry.findMatch("to be cleared"))
        }
    }

    // Helper functions

    private fun createMockContext(): AssertionContext {
        val executionContext = org.berrycrush.context.ExecutionContext()

        return org.berrycrush.assertion.AssertionContextImpl(
            executionContext = executionContext,
            configuration = config,
            sharedVariables = null,
            sharingEnabled = false,
        )
    }

    private fun invokeAssertion(match: AssertionMatch, context: AssertionContext): AssertionResult {
        val method = match.definition.method
        val parameters = match.parameters.toMutableList()

        // Check if method accepts AssertionContext
        val methodParams = method.parameters
        val args = if (methodParams.isNotEmpty() &&
            methodParams.last().type.isAssignableFrom(AssertionContext::class.java)
        ) {
            parameters.add(context)
            parameters.toTypedArray()
        } else {
            parameters.toTypedArray()
        }

        return method.invoke(match.definition.instance, *args) as AssertionResult
    }

    private fun invokeAssertionSafely(match: AssertionMatch, context: AssertionContext): AssertionResult {
        return try {
            invokeAssertion(match, context)
        } catch (e: Exception) {
            val actualException = when (e) {
                is java.lang.reflect.InvocationTargetException -> e.cause ?: e
                else -> e
            }
            AssertionResult.failed("Assertion threw exception: ${actualException.message}")
        }
    }
}

