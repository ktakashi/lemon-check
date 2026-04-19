package org.berrycrush.spring

import org.berrycrush.exception.ConfigurationException
import org.berrycrush.junit.BerryCrushBindings
import org.berrycrush.junit.spi.BindingsProvider
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.ConcurrentHashMap

/**
 * BindingsProvider implementation that integrates with Spring TestContext.
 *
 * This provider enables Spring dependency injection for BerryCrushBindings
 * classes when the test class is annotated with `@BerryCrushContextConfiguration`.
 *
 * When activated, it:
 * - Starts the Spring ApplicationContext before scenario execution
 * - Retrieves bindings instances from Spring's bean container
 * - Enables `@LocalServerPort`, `@Autowired`, and other Spring annotations
 * - Cleans up Spring context after test completion
 *
 * The provider is automatically discovered via ServiceLoader when the
 * berrycrush-spring module is on the classpath.
 */
class SpringBindingsProvider : BindingsProvider {
    /**
     * Cache of SpringContextAdapter instances per test class.
     * This enables context reuse within a single test class execution.
     */
    private val contextAdapters = ConcurrentHashMap<Class<*>, SpringContextAdapter>()

    /**
     * Returns true if the test class has `@BerryCrushContextConfiguration` annotation.
     */
    override fun supports(testClass: Class<*>): Boolean = testClass.isAnnotationPresent(BerryCrushContextConfiguration::class.java)

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
     * @throws ConfigurationException if @SpringBootTest is missing or context fails to start
     */
    override fun initialize(testClass: Class<*>) {
        // Validate that @SpringBootTest is present
        if (!testClass.isAnnotationPresent(SpringBootTest::class.java)) {
            throw ConfigurationException(
                "Test class '${testClass.name}' has @BerryCrushContextConfiguration but is missing @SpringBootTest. " +
                    "Add @SpringBootTest annotation to enable Spring context integration.",
            )
        }

        // Create and initialize the Spring context adapter
        val adapter = SpringContextAdapter(testClass)
        adapter.initializeContext()
        contextAdapters[testClass] = adapter
    }

    /**
     * Creates a BerryCrushBindings instance by retrieving it from Spring's ApplicationContext.
     *
     * If the bindings class is a Spring bean (annotated with `@Component` or
     * registered via `@Bean` method), it will be retrieved from Spring context.
     * Otherwise, the bindings will be created via direct instantiation.
     *
     * @param testClass The test class being executed
     * @param bindingsClass The bindings class to retrieve from Spring context
     * @return The Spring-managed bindings instance with dependencies injected
     * @throws ConfigurationException if context not initialized
     */
    override fun createBindings(
        testClass: Class<*>,
        bindingsClass: Class<out BerryCrushBindings>,
    ): BerryCrushBindings {
        val adapter =
            contextAdapters[testClass]
                ?: throw ConfigurationException(
                    "Spring context not initialized for test class: ${testClass.name}. " +
                        "Ensure initialize() was called before createBindings().",
                )

        // Try to get from Spring context first, fall back to direct instantiation
        return adapter.getBeanOrNull(bindingsClass)
            ?: bindingsClass.getDeclaredConstructor().newInstance()
    }

    /**
     * Creates a Spring-prepared test instance for @ScenarioTest method execution.
     *
     * The returned instance has @LocalServerPort, @Autowired, and other
     * Spring annotations properly injected. This enables @ScenarioTest methods
     * in Spring Boot tests to access the dynamic server port.
     *
     * @param testClass The test class to create an instance of
     * @return The Spring-managed test instance with dependencies injected
     * @throws ConfigurationException if context not initialized
     */
    override fun createTestInstance(testClass: Class<*>): Any {
        val adapter =
            contextAdapters[testClass]
                ?: throw ConfigurationException(
                    "Spring context not initialized for test class: ${testClass.name}. " +
                        "Ensure initialize() was called before createTestInstance().",
                )

        return adapter.getTestInstance()
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
