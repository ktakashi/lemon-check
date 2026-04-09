package io.github.ktakashi.lemoncheck.spring

import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings
import io.github.ktakashi.lemoncheck.junit.spi.BindingsProvider
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.ConcurrentHashMap

/**
 * BindingsProvider implementation that integrates with Spring TestContext.
 *
 * This provider enables Spring dependency injection for LemonCheckBindings
 * classes when the test class is annotated with `@LemonCheckContextConfiguration`.
 *
 * When activated, it:
 * - Starts the Spring ApplicationContext before scenario execution
 * - Retrieves bindings instances from Spring's bean container
 * - Enables `@LocalServerPort`, `@Autowired`, and other Spring annotations
 * - Cleans up Spring context after test completion
 *
 * The provider is automatically discovered via ServiceLoader when the
 * lemon-check-spring module is on the classpath.
 */
class SpringBindingsProvider : BindingsProvider {
    /**
     * Cache of SpringContextAdapter instances per test class.
     * This enables context reuse within a single test class execution.
     */
    private val contextAdapters = ConcurrentHashMap<Class<*>, SpringContextAdapter>()

    /**
     * Returns true if the test class has `@LemonCheckContextConfiguration` annotation.
     */
    override fun supports(testClass: Class<*>): Boolean = testClass.isAnnotationPresent(LemonCheckContextConfiguration::class.java)

    /**
     * Returns priority of 100 to ensure Spring provider takes precedence
     * over default reflection-based binding creation.
     */
    override fun priority(): Int = 100

    /**
     * Initializes the Spring context for the test class.
     *
     * This validates that `@SpringBootTest` is present and starts the
     * Spring ApplicationContext via SpringContextAdapter.
     *
     * @param testClass The test class to initialize Spring context for
     * @throws IllegalStateException if @SpringBootTest is missing or context fails to start
     */
    override fun initialize(testClass: Class<*>) {
        // Validate that @SpringBootTest is present
        if (!testClass.isAnnotationPresent(SpringBootTest::class.java)) {
            throw IllegalStateException(
                "Test class '${testClass.name}' has @LemonCheckContextConfiguration but is missing @SpringBootTest. " +
                    "Add @SpringBootTest annotation to enable Spring context integration.",
            )
        }

        // Create and initialize the Spring context adapter
        val adapter = SpringContextAdapter(testClass)
        adapter.initializeContext()
        contextAdapters[testClass] = adapter
    }

    /**
     * Creates a LemonCheckBindings instance by retrieving it from Spring's ApplicationContext.
     *
     * The bindings class must be a Spring bean (annotated with `@Component` or
     * registered via `@Bean` method) for this to succeed.
     *
     * @param testClass The test class being executed
     * @param bindingsClass The bindings class to retrieve from Spring context
     * @return The Spring-managed bindings instance with dependencies injected
     * @throws IllegalStateException if context not initialized or bean not found
     */
    override fun createBindings(
        testClass: Class<*>,
        bindingsClass: Class<out LemonCheckBindings>,
    ): LemonCheckBindings {
        val adapter =
            contextAdapters[testClass]
                ?: throw IllegalStateException(
                    "Spring context not initialized for test class: ${testClass.name}. " +
                        "Ensure initialize() was called before createBindings().",
                )

        return adapter.getBean(bindingsClass)
    }

    /**
     * Cleans up Spring context resources after test execution.
     *
     * This releases the ApplicationContext following standard Spring Test semantics.
     *
     * @param testClass The test class that was executed
     */
    override fun cleanup(testClass: Class<*>) {
        contextAdapters.remove(testClass)?.cleanup()
    }
}
