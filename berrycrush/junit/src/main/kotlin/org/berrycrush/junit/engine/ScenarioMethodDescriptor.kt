package org.berrycrush.junit.engine

import org.berrycrush.dsl.BerryCrushSuite
import org.berrycrush.model.Scenario
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.MethodSource
import java.lang.reflect.Method

/**
 * Test descriptor representing a method annotated with @Scenario.
 *
 * This descriptor wraps a method that returns a [Scenario] object when invoked.
 * The scenario is executed by the [ScenarioTestExecutor] when this test runs.
 *
 * The method can accept a [BerryCrushSuite] parameter which will be injected
 * by the executor with the configured OpenAPI specs.
 *
 * Example:
 * ```kotlin
 * @Scenario
 * fun createPet(suite: BerryCrushSuite): Scenario =
 *     suite.scenario("Create a pet") {
 *         whenever("I create a pet") { ... }
 *         afterwards("it is created") { ... }
 *     }
 * ```
 */
class ScenarioMethodDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    /**
     * The method annotated with @Scenario.
     */
    val method: Method,
    /**
     * The test class containing the method.
     */
    val testClass: Class<*>,
) : AbstractTestDescriptor(uniqueId, displayName, MethodSource.from(method)) {
    /**
     * DSL scenarios are always leaf tests (not containers).
     */
    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST

    /**
     * Invoke the method to get the Scenario.
     *
     * @param testInstance The test class instance
     * @param suite The BerryCrushSuite to pass to the method (if required)
     * @return The Scenario returned by the method
     * @throws IllegalStateException If the method doesn't return a Scenario
     */
    fun invokeMethod(
        testInstance: Any,
        suite: BerryCrushSuite,
    ): Scenario {
        method.isAccessible = true

        // Determine which parameters to pass
        val params = method.parameters
        val args =
            params
                .map { param ->
                    when (param.type) {
                        BerryCrushSuite::class.java -> suite
                        else -> throw IllegalArgumentException(
                            "Unsupported parameter type ${param.type.name} in @Scenario method ${method.name}. " +
                                "Only BerryCrushSuite is supported.",
                        )
                    }
                }.toTypedArray()

        val result = method.invoke(testInstance, *args)

        return result as? Scenario
            ?: throw IllegalStateException(
                "@Scenario method ${method.name} must return Scenario, but returned ${result?.javaClass?.name ?: "null"}",
            )
    }
}
