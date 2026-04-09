package io.github.ktakashi.lemoncheck.junit.spi

import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings

/**
 * Service Provider Interface for creating LemonCheckBindings instances.
 *
 * Implementations are discovered via Java ServiceLoader.
 * When a test class is annotated with framework-specific annotations
 * (e.g., @LemonCheckContextConfiguration), the corresponding provider
 * handles bindings creation with proper dependency injection.
 *
 * The default binding creation (direct instantiation via reflection) is used
 * when no provider supports the given test class.
 *
 * To implement a custom BindingsProvider:
 * 1. Create a class implementing this interface
 * 2. Register it in META-INF/services/io.github.ktakashi.lemoncheck.junit.spi.BindingsProvider
 *
 * Example for Spring integration:
 * ```kotlin
 * class SpringBindingsProvider : BindingsProvider {
 *     override fun supports(testClass: Class<*>) =
 *         testClass.isAnnotationPresent(LemonCheckContextConfiguration::class.java)
 *
 *     override fun createBindings(testClass: Class<*>, bindingsClass: Class<out LemonCheckBindings>) =
 *         applicationContext.getBean(bindingsClass)
 * }
 * ```
 */
interface BindingsProvider {
    /**
     * Determines if this provider supports the given test class.
     *
     * @param testClass The test class being executed
     * @return true if this provider can handle bindings for this test class
     */
    fun supports(testClass: Class<*>): Boolean

    /**
     * Priority of this provider. Higher values indicate higher priority.
     * When multiple providers support a test class, the one with highest
     * priority is used.
     *
     * @return Priority value (default implementations should return 0)
     */
    fun priority(): Int = 0

    /**
     * Initializes the provider for the given test class.
     * Called once before any scenarios are executed.
     *
     * For Spring integration, this starts the ApplicationContext.
     *
     * @param testClass The test class being executed
     */
    fun initialize(testClass: Class<*>)

    /**
     * Creates a LemonCheckBindings instance for the given test class.
     *
     * @param testClass The test class being executed
     * @param bindingsClass The bindings class to instantiate (from @LemonCheckConfiguration)
     * @return The bindings instance
     * @throws IllegalStateException if bindings cannot be created
     */
    fun createBindings(
        testClass: Class<*>,
        bindingsClass: Class<out LemonCheckBindings>,
    ): LemonCheckBindings

    /**
     * Cleans up resources after test execution completes.
     * Called once after all scenarios have executed.
     *
     * For Spring integration, this releases the ApplicationContext.
     *
     * @param testClass The test class that was executed
     */
    fun cleanup(testClass: Class<*>)
}
