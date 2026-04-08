package io.github.ktakashi.lemoncheck.junit.engine

import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings
import io.github.ktakashi.lemoncheck.junit.LemonCheckConfiguration
import io.github.ktakashi.lemoncheck.junit.LemonCheckScenarios
import io.github.ktakashi.lemoncheck.junit.LemonCheckSpec
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.ClassSource

/**
 * Test descriptor representing a test class annotated with @LemonCheckScenarios.
 *
 * This descriptor holds information about the test class including:
 * - The scenario file locations to search
 * - The custom bindings class (if configured)
 * - Configuration annotation settings
 */
class ClassTestDescriptor(
    uniqueId: UniqueId,
    val testClass: Class<*>,
) : AbstractTestDescriptor(uniqueId, testClass.simpleName, ClassSource.from(testClass)) {
    /**
     * Scenario file location patterns from @LemonCheckScenarios annotation.
     */
    val locations: Array<out String>
        get() = testClass.getAnnotation(LemonCheckScenarios::class.java)?.locations ?: emptyArray()

    /**
     * Optional custom bindings class from @LemonCheckConfiguration annotation.
     */
    val bindingsClass: Class<out LemonCheckBindings>?

    /**
     * OpenAPI spec path from configuration (if any).
     * Priority: @LemonCheckConfiguration.openApiSpec > @LemonCheckSpec.paths[0]
     */
    val openApiSpec: String?

    /**
     * Timeout in milliseconds for scenario execution.
     */
    val timeout: Long

    init {
        val config = testClass.getAnnotation(LemonCheckConfiguration::class.java)
        val spec = testClass.getAnnotation(LemonCheckSpec::class.java)

        bindingsClass = config?.bindings?.java
        timeout = config?.timeout ?: 30_000L

        // OpenAPI spec: prefer @LemonCheckConfiguration, fallback to @LemonCheckSpec
        openApiSpec = config?.openApiSpec?.takeIf { it.isNotBlank() }
            ?: spec?.paths?.firstOrNull()
    }

    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.CONTAINER
}
