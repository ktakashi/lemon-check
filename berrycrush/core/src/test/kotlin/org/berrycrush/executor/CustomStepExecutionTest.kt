package org.berrycrush.executor

import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.model.ResultStatus
import org.berrycrush.model.Scenario
import org.berrycrush.model.Step
import org.berrycrush.model.StepType
import org.berrycrush.openapi.SpecRegistry
import org.berrycrush.step.DefaultStepRegistry
import org.berrycrush.step.StepContext
import org.berrycrush.step.StepDefinition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.berrycrush.step.Step as StepAnnotation

/**
 * Unit tests for custom step invocation in BerryCrushScenarioExecutor.
 */
class CustomStepExecutionTest {
    private lateinit var specRegistry: SpecRegistry
    private lateinit var config: BerryCrushConfiguration
    private lateinit var stepRegistry: DefaultStepRegistry

    @BeforeEach
    fun setup() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        specRegistry = SpecRegistry()
        specRegistry.registerDefault(specPath)

        config = BerryCrushConfiguration()

        stepRegistry = DefaultStepRegistry()
    }

    private fun createExecutor(): BerryCrushScenarioExecutor =
        BerryCrushScenarioExecutor(
            specRegistry = specRegistry,
            configuration = config,
            stepRegistry = stepRegistry,
        )

    @Nested
    @DisplayName("Custom step registration and invocation")
    inner class CustomStepInvocation {
        @Test
        @DisplayName("should invoke custom step when description matches")
        fun invokeCustomStepWhenDescriptionMatches() {
            // Register a simple custom step
            val stepInstance =
                object {
                    var invoked = false

                    @StepAnnotation("I do something simple")
                    fun doSomething() {
                        invoked = true
                    }
                }

            val definition =
                StepDefinition(
                    pattern = "I do something simple",
                    method = stepInstance.javaClass.getMethod("doSomething"),
                    instance = stepInstance,
                )
            stepRegistry.register(definition)

            val executor = createExecutor()
            val scenario =
                Scenario(
                    name = "Custom step test",
                    steps =
                        listOf(
                            Step(
                                type = StepType.WHEN,
                                description = "I do something simple",
                            ),
                        ),
                )

            val result = executor.execute(scenario)

            assertEquals(ResultStatus.PASSED, result.status)
            assertTrue(stepInstance.invoked, "Custom step should have been invoked")
        }

        @Test
        @DisplayName("should extract string parameter from pattern")
        fun extractStringParameter() {
            val stepInstance =
                object {
                    var capturedValue: String? = null

                    @StepAnnotation("I have a pet named {string}")
                    fun havePet(name: String) {
                        capturedValue = name
                    }
                }

            val definition =
                StepDefinition(
                    pattern = "I have a pet named {string}",
                    method = stepInstance.javaClass.getMethod("havePet", String::class.java),
                    instance = stepInstance,
                )
            stepRegistry.register(definition)

            val executor = createExecutor()
            val scenario =
                Scenario(
                    name = "String parameter test",
                    steps =
                        listOf(
                            Step(
                                type = StepType.GIVEN,
                                description = "I have a pet named \"Fluffy\"",
                            ),
                        ),
                )

            val result = executor.execute(scenario)

            assertEquals(ResultStatus.PASSED, result.status)
            assertEquals("Fluffy", stepInstance.capturedValue)
        }

        @Test
        @DisplayName("should extract int parameter from pattern")
        fun extractIntParameter() {
            val stepInstance =
                object {
                    var capturedValue: Int? = null

                    @StepAnnotation("I have {int} pets")
                    fun havePets(count: Int) {
                        capturedValue = count
                    }
                }

            val definition =
                StepDefinition(
                    pattern = "I have {int} pets",
                    method = stepInstance.javaClass.getMethod("havePets", Int::class.java),
                    instance = stepInstance,
                )
            stepRegistry.register(definition)

            val executor = createExecutor()
            val scenario =
                Scenario(
                    name = "Int parameter test",
                    steps =
                        listOf(
                            Step(
                                type = StepType.GIVEN,
                                description = "I have 5 pets",
                            ),
                        ),
                )

            val result = executor.execute(scenario)

            assertEquals(ResultStatus.PASSED, result.status)
            assertEquals(5, stepInstance.capturedValue)
        }

        @Test
        @DisplayName("should extract multiple parameters from pattern")
        fun extractMultipleParameters() {
            val stepInstance =
                object {
                    var capturedName: String? = null
                    var capturedStatus: String? = null

                    @StepAnnotation("I have a pet named {string} with status {string}")
                    fun havePetWithStatus(
                        name: String,
                        status: String,
                    ) {
                        capturedName = name
                        capturedStatus = status
                    }
                }

            val definition =
                StepDefinition(
                    pattern = "I have a pet named {string} with status {string}",
                    method =
                        stepInstance.javaClass.getMethod(
                            "havePetWithStatus",
                            String::class.java,
                            String::class.java,
                        ),
                    instance = stepInstance,
                )
            stepRegistry.register(definition)

            val executor = createExecutor()
            val scenario =
                Scenario(
                    name = "Multiple parameters test",
                    steps =
                        listOf(
                            Step(
                                type = StepType.GIVEN,
                                description = "I have a pet named \"Max\" with status \"available\"",
                            ),
                        ),
                )

            val result = executor.execute(scenario)

            assertEquals(ResultStatus.PASSED, result.status)
            assertEquals("Max", stepInstance.capturedName)
            assertEquals("available", stepInstance.capturedStatus)
        }

        @Test
        @DisplayName("should pass if custom step not found and step has no operationId")
        fun passWhenNoCustomStepAndNoOperationId() {
            // No custom step registered
            val executor = createExecutor()
            val scenario =
                Scenario(
                    name = "No custom step test",
                    steps =
                        listOf(
                            Step(
                                type = StepType.GIVEN,
                                description = "something is true",
                            ),
                        ),
                )

            val result = executor.execute(scenario)

            // Should pass as a no-op step
            assertEquals(ResultStatus.PASSED, result.status)
        }
    }

    @Nested
    @DisplayName("StepContext injection")
    inner class StepContextInjection {
        @Test
        @DisplayName("should inject StepContext when method accepts it as last parameter")
        fun injectStepContext() {
            val stepInstance =
                object {
                    var receivedContext: StepContext? = null

                    @StepAnnotation("I need context")
                    fun needContext(context: StepContext) {
                        receivedContext = context
                    }
                }

            val definition =
                StepDefinition(
                    pattern = "I need context",
                    method = stepInstance.javaClass.getMethod("needContext", StepContext::class.java),
                    instance = stepInstance,
                )
            stepRegistry.register(definition)

            val executor = createExecutor()
            val scenario =
                Scenario(
                    name = "StepContext injection test",
                    steps =
                        listOf(
                            Step(
                                type = StepType.WHEN,
                                description = "I need context",
                            ),
                        ),
                )

            val result = executor.execute(scenario)

            assertEquals(ResultStatus.PASSED, result.status)
            assertNotNull(stepInstance.receivedContext, "StepContext should have been injected")
        }

        @Test
        @DisplayName("should inject StepContext along with other parameters")
        fun injectStepContextWithOtherParameters() {
            val stepInstance =
                object {
                    var capturedName: String? = null
                    var receivedContext: StepContext? = null

                    @StepAnnotation("I set name to {string}")
                    fun setName(
                        name: String,
                        context: StepContext,
                    ) {
                        capturedName = name
                        receivedContext = context
                    }
                }

            val definition =
                StepDefinition(
                    pattern = "I set name to {string}",
                    method =
                        stepInstance.javaClass.getMethod(
                            "setName",
                            String::class.java,
                            StepContext::class.java,
                        ),
                    instance = stepInstance,
                )
            stepRegistry.register(definition)

            val executor = createExecutor()
            val scenario =
                Scenario(
                    name = "StepContext with params test",
                    steps =
                        listOf(
                            Step(
                                type = StepType.WHEN,
                                description = "I set name to \"Test\"",
                            ),
                        ),
                )

            val result = executor.execute(scenario)

            assertEquals(ResultStatus.PASSED, result.status)
            assertEquals("Test", stepInstance.capturedName)
            assertNotNull(stepInstance.receivedContext)
        }

        @Test
        @DisplayName("should allow custom step to set variables via StepContext")
        fun customStepCanSetVariables() {
            val stepInstance =
                object {
                    @StepAnnotation("I set a variable")
                    fun setVariable(context: StepContext) {
                        context.setVariable("myVar", "myValue")
                    }

                    @StepAnnotation("the variable should be set")
                    fun checkVariable(context: StepContext) {
                        val value = context.variable("myVar")
                        if (value != "myValue") {
                            throw AssertionError("Expected 'myValue' but got '$value'")
                        }
                    }
                }

            stepRegistry.register(
                StepDefinition(
                    pattern = "I set a variable",
                    method = stepInstance.javaClass.getMethod("setVariable", StepContext::class.java),
                    instance = stepInstance,
                ),
            )
            stepRegistry.register(
                StepDefinition(
                    pattern = "the variable should be set",
                    method = stepInstance.javaClass.getMethod("checkVariable", StepContext::class.java),
                    instance = stepInstance,
                ),
            )

            val executor = createExecutor()
            val scenario =
                Scenario(
                    name = "Variable setting test",
                    steps =
                        listOf(
                            Step(type = StepType.WHEN, description = "I set a variable"),
                            Step(type = StepType.THEN, description = "the variable should be set"),
                        ),
                )

            val result = executor.execute(scenario)

            assertEquals(ResultStatus.PASSED, result.status)
        }
    }

    @Nested
    @DisplayName("Error handling")
    inner class ErrorHandling {
        @Test
        @DisplayName("should report ERROR status when custom step throws exception")
        fun reportErrorWhenStepThrows() {
            val stepInstance =
                object {
                    @StepAnnotation("I fail")
                    fun fail(): Unit = throw RuntimeException("Step failure")
                }

            val definition =
                StepDefinition(
                    pattern = "I fail",
                    method = stepInstance.javaClass.getMethod("fail"),
                    instance = stepInstance,
                )
            stepRegistry.register(definition)

            val executor = createExecutor()
            val scenario =
                Scenario(
                    name = "Failing step test",
                    steps =
                        listOf(
                            Step(type = StepType.WHEN, description = "I fail"),
                        ),
                )

            val result = executor.execute(scenario)

            assertEquals(ResultStatus.ERROR, result.status)
            assertNotNull(result.stepResults[0].error)
            assertTrue(
                result.stepResults[0]
                    .error!!
                    .message
                    ?.contains("Step failure") == true,
            )
        }

        @Test
        @DisplayName("should report FAILED status when custom step throws AssertionError")
        fun reportFailedWhenAssertionFails() {
            val stepInstance =
                object {
                    @StepAnnotation("I assert false")
                    fun assertFalse(): Unit = throw AssertionError("Assertion failed")
                }

            val definition =
                StepDefinition(
                    pattern = "I assert false",
                    method = stepInstance.javaClass.getMethod("assertFalse"),
                    instance = stepInstance,
                )
            stepRegistry.register(definition)

            val executor = createExecutor()
            val scenario =
                Scenario(
                    name = "Assertion failure test",
                    steps =
                        listOf(
                            Step(type = StepType.THEN, description = "I assert false"),
                        ),
                )

            val result = executor.execute(scenario)

            assertEquals(ResultStatus.FAILED, result.status)
            assertNotNull(result.stepResults[0].error)
            assertTrue(
                result.stepResults[0]
                    .error!!
                    .message
                    ?.contains("Assertion failed") == true,
            )
        }
    }
}
