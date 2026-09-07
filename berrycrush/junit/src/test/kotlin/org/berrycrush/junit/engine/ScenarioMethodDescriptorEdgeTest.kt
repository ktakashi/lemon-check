package org.berrycrush.junit.engine

import org.berrycrush.junit.BerryCrushSuite
import org.berrycrush.junit.ScenarioTest
import org.berrycrush.model.Scenario
import org.berrycrush.model.Step
import org.berrycrush.model.StepType
import org.junit.jupiter.api.Test
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScenarioMethodDescriptorEdgeTest {
    @Test
    fun `invokeMethod should reject unsupported parameter types`() {
        val descriptor =
            ScenarioMethodDescriptor(
                uniqueId = UniqueId.forEngine("berrycrush").append("scenario", "unsupported"),
                displayName = "unsupported",
                method = InvalidScenarioMethods::class.java.getDeclaredMethod("unsupportedParam", String::class.java),
                testClass = InvalidScenarioMethods::class.java,
            )

        val error =
            assertFailsWith<IllegalArgumentException> {
                descriptor.invokeMethod(InvalidScenarioMethods(), BerryCrushSuite.create())
            }

        assertEquals(TestDescriptor.Type.TEST, descriptor.type)
        assertEquals(true, error.message?.contains("Unsupported parameter type"))
    }

    @Test
    fun `invokeMethod should reject non Scenario return types`() {
        val descriptor =
            ScenarioMethodDescriptor(
                uniqueId = UniqueId.forEngine("berrycrush").append("scenario", "wrong-return"),
                displayName = "wrong-return",
                method = InvalidScenarioMethods::class.java.getDeclaredMethod("wrongReturnType"),
                testClass = InvalidScenarioMethods::class.java,
            )

        val error =
            assertFailsWith<IllegalStateException> {
                descriptor.invokeMethod(InvalidScenarioMethods(), BerryCrushSuite.create())
            }

        assertEquals(true, error.message?.contains("must return Scenario"))
    }
}

private class InvalidScenarioMethods {
    @ScenarioTest
    @Suppress("UnusedParameter")
    fun unsupportedParam(unused: String) = Scenario(name = "invalid", steps = listOf(Step(type = StepType.WHEN, description = "noop")))

    @ScenarioTest
    @Suppress("FunctionOnlyReturningConstant")
    fun wrongReturnType(): String = "not-a-scenario"
}
