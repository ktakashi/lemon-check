package org.berrycrush.executor.fragment

import org.berrycrush.context.ExecutionContext
import org.berrycrush.exception.ConfigurationException
import org.berrycrush.executor.withIncludeParameters
import org.berrycrush.model.Fragment
import org.berrycrush.model.FragmentRegistry
import org.berrycrush.model.Step
import org.berrycrush.model.StepType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultFragmentExecutorTest {
    // --- expand() Tests ---

    @Test
    fun `expand - step without fragment returns original step`() {
        val executor = DefaultFragmentExecutor(null)
        val step = createStep(operationId = "getPets")

        val expanded = executor.expand(step)

        assertEquals(1, expanded.size)
        assertEquals(step, expanded[0])
    }

    @Test
    fun `expand - step with fragment returns fragment steps`() {
        val fragmentSteps =
            listOf(
                createStep(operationId = "login"),
                createStep(operationId = "getProfile"),
            )
        val fragment = Fragment(name = "authenticate", steps = fragmentSteps)
        val registry = FragmentRegistry()
        registry.register(fragment)

        val executor = DefaultFragmentExecutor(registry)
        val step = createStep(fragmentName = "authenticate")

        val expanded = executor.expand(step)

        assertEquals(2, expanded.size)
        assertEquals("login", expanded[0].operationId)
        assertEquals("getProfile", expanded[1].operationId)
    }

    @Test
    fun `expand - missing fragment throws ConfigurationException`() {
        val registry = FragmentRegistry()
        val executor = DefaultFragmentExecutor(registry)
        val step = createStep(fragmentName = "nonexistent")

        val exception =
            assertFailsWith<ConfigurationException> {
                executor.expand(step)
            }

        assertTrue(exception.message!!.contains("nonexistent"))
        assertTrue(exception.message!!.contains("not found"))
    }

    @Test
    fun `expand - null registry throws ConfigurationException for fragment reference`() {
        val executor = DefaultFragmentExecutor(null)
        val step = createStep(fragmentName = "someFragment")

        assertFailsWith<ConfigurationException> {
            executor.expand(step)
        }
    }

    // --- injectParameters() Tests ---

    @Test
    fun `ExecutionContext#withIncludeParameters - injects simple string parameters`() {
        val step =
            createStep(
                fragmentName = "createEntity",
                includeParameters =
                    mapOf(
                        "entityName" to "Pet",
                        "userId" to $$"${currentUserId}",
                        "name" to "NewValue",
                        "message" to $$"Hello, ${userName}!",
                    ),
            )
        val context = ExecutionContext()
        context["currentUserId"] = "user123"
        context["name"] = "OldValue"
        context["existing"] = "value"
        context["userName"] = "World"

        context.withIncludeParameters(step) {
            assertEquals("Pet", context.get<String>("entityName"))
            assertEquals("user123", context.get<String>("userId"))
            assertEquals("NewValue", context.get<String>("name"))
            assertEquals("value", context.get<String>("existing"))
            assertEquals("Hello, World!", context.get<String>("message"))
        }
        assertEquals("OldValue", context.get<String>("name"))
    }

    // --- Helper Functions ---

    private fun createStep(
        operationId: String? = null,
        fragmentName: String? = null,
        includeParameters: Map<String, Any> = emptyMap(),
    ): Step =
        Step(
            type = StepType.WHEN,
            description = "test step",
            operationId = operationId,
            fragmentName = fragmentName,
            includeParameters = includeParameters,
        )
}
