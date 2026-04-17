package org.berrycrush.executor

import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.context.ExecutionContext
import org.berrycrush.step.StepContextImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [StepContextImpl].
 */
@DisplayName("StepContextImpl")
class StepContextImplTest {
    private lateinit var executionContext: ExecutionContext
    private lateinit var configuration: BerryCrushConfiguration
    private lateinit var stepContext: StepContextImpl

    @BeforeEach
    fun setUp() {
        executionContext = ExecutionContext()
        configuration = BerryCrushConfiguration()
        stepContext =
            StepContextImpl(
                executionContext = executionContext,
                configuration = configuration,
                sharedVariables = null,
                sharingEnabled = false,
            )
    }

    @Nested
    @DisplayName("variable()")
    inner class VariableTests {
        @Test
        @DisplayName("should return variable from execution context")
        fun returnVariableFromContext() {
            executionContext.set("username", "john")

            val result = stepContext.variable("username")

            assertEquals("john", result)
        }

        @Test
        @DisplayName("should return null when variable not found")
        fun returnNullWhenNotFound() {
            val result = stepContext.variable("missing")

            assertNull(result)
        }

        @Test
        @DisplayName("should return typed variable")
        fun returnTypedVariable() {
            executionContext.set("count", 42)

            val result = stepContext.variable("count", Int::class.java)

            assertEquals(42, result)
        }
    }

    @Nested
    @DisplayName("setVariable()")
    inner class SetVariableTests {
        @Test
        @DisplayName("should set variable in execution context")
        fun setVariableInContext() {
            stepContext.setVariable("userId", 123)

            assertEquals(123, executionContext.get("userId"))
        }

        @Test
        @DisplayName("should set string variable")
        fun setStringVariable() {
            stepContext.setVariable("name", "alice")

            assertEquals("alice", executionContext.get("name"))
        }

        @Test
        @DisplayName("should overwrite existing variable")
        fun overwriteExistingVariable() {
            stepContext.setVariable("key", "value1")
            stepContext.setVariable("key", "value2")

            assertEquals("value2", executionContext.get("key"))
        }
    }

    @Nested
    @DisplayName("setSharedVariable()")
    inner class SetSharedVariableTests {
        @Test
        @DisplayName("should set variable in shared map when sharing is enabled")
        fun setVariableWhenSharingEnabled() {
            val sharedVars = mutableMapOf<String, Any?>()
            val ctxWithSharing =
                StepContextImpl(
                    executionContext = executionContext,
                    configuration = configuration,
                    sharedVariables = sharedVars,
                    sharingEnabled = true,
                )

            ctxWithSharing.setSharedVariable("token", "abc123")

            // Variable should be in shared map, not execution context
            assertEquals("abc123", sharedVars["token"])
            assertNull(executionContext.get("token"))
        }

        @Test
        @DisplayName("should set variable as regular variable when sharing is disabled")
        fun setVariableWhenSharingDisabled() {
            stepContext.setSharedVariable("token", "xyz789")

            assertEquals("xyz789", executionContext.get("token"))
        }
    }

    @Nested
    @DisplayName("lastResponse")
    inner class LastResponseTests {
        @Test
        @DisplayName("should return null when no response is available")
        fun returnNullWhenNoResponse() {
            val result = stepContext.lastResponse

            assertNull(result)
        }
    }

    @Nested
    @DisplayName("configuration")
    inner class ConfigurationTests {
        @Test
        @DisplayName("should return configuration")
        fun returnConfiguration() {
            val result = stepContext.configuration

            assertEquals(configuration, result)
        }
    }

    @Nested
    @DisplayName("variable scoping with shared variables")
    inner class VariableScopingTests {
        @Test
        @DisplayName("should prioritize scenario variables over shared variables")
        fun prioritizeScenarioVariables() {
            val sharedVars = mutableMapOf<String, Any?>("key" to "shared")
            val ctxWithSharing =
                StepContextImpl(
                    executionContext = executionContext,
                    configuration = configuration,
                    sharedVariables = sharedVars,
                    sharingEnabled = true,
                )

            // Set scenario variable
            executionContext.set("key", "scenario")

            val result = ctxWithSharing.variable("key")

            assertEquals("scenario", result)
        }

        @Test
        @DisplayName("should fall back to shared variable when scenario variable is null")
        fun fallBackToSharedVariable() {
            val sharedVars = mutableMapOf<String, Any?>("key" to "shared")
            val ctxWithSharing =
                StepContextImpl(
                    executionContext = executionContext,
                    configuration = configuration,
                    sharedVariables = sharedVars,
                    sharingEnabled = true,
                )

            // Scenario variable does not exist
            val result = ctxWithSharing.variable("key")

            assertEquals("shared", result)
        }

        @Test
        @DisplayName("should return null when variable not found in any scope")
        fun returnNullWhenNotFoundInAnyScope() {
            val sharedVars = mutableMapOf<String, Any?>()
            val ctxWithSharing =
                StepContextImpl(
                    executionContext = executionContext,
                    configuration = configuration,
                    sharedVariables = sharedVars,
                    sharingEnabled = true,
                )

            val result = ctxWithSharing.variable("missing")

            assertNull(result)
        }

        @Test
        @DisplayName("should ignore shared variables when sharing is disabled")
        fun ignoreSharedVariablesWhenDisabled() {
            val sharedVars = mutableMapOf<String, Any?>("key" to "shared")
            val ctxWithoutSharing =
                StepContextImpl(
                    executionContext = executionContext,
                    configuration = configuration,
                    sharedVariables = sharedVars, // Map provided but sharing disabled
                    sharingEnabled = false,
                )

            // Variable should not be accessible because sharing is disabled
            val result = ctxWithoutSharing.variable("key")

            assertNull(result)
        }
    }

    @Nested
    @DisplayName("allVariables()")
    inner class AllVariablesTests {
        @Test
        @DisplayName("should return scenario variables when sharing is disabled")
        fun returnScenarioVariablesOnly() {
            executionContext.set("name", "john")
            executionContext.set("count", 5)

            val result = stepContext.allVariables()

            assertEquals("john", result["name"])
            assertEquals(5, result["count"])
        }

        @Test
        @DisplayName("should combine scenario and shared variables when sharing is enabled")
        fun combineScenarioAndSharedVariables() {
            val sharedVars = mutableMapOf<String, Any?>("shared" to "sharedValue")
            val ctxWithSharing =
                StepContextImpl(
                    executionContext = executionContext,
                    configuration = configuration,
                    sharedVariables = sharedVars,
                    sharingEnabled = true,
                )
            executionContext.set("scenario", "scenarioValue")

            val result = ctxWithSharing.allVariables()

            assertEquals("scenarioValue", result["scenario"])
            assertEquals("sharedValue", result["shared"])
        }

        @Test
        @DisplayName("should prioritize scenario variables in allVariables")
        fun prioritizeScenarioVariablesInAll() {
            val sharedVars = mutableMapOf<String, Any?>("key" to "shared")
            val ctxWithSharing =
                StepContextImpl(
                    executionContext = executionContext,
                    configuration = configuration,
                    sharedVariables = sharedVars,
                    sharingEnabled = true,
                )
            executionContext.set("key", "scenario")

            val result = ctxWithSharing.allVariables()

            // Scenario variable should take precedence
            assertEquals("scenario", result["key"])
        }
    }
}
