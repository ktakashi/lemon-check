package io.github.ktakashi.lemoncheck.spring

import org.springframework.context.ApplicationContext
import org.springframework.test.context.TestContextManager

/**
 * Bridge between LemonCheck engine and Spring TestContext framework.
 *
 * This adapter manages the lifecycle of Spring's TestContextManager,
 * enabling proper application context initialization and bean retrieval
 * for lemon-check scenario tests.
 *
 * It handles:
 * - Creating and initializing the TestContextManager for a test class
 * - Starting the Spring application context
 * - Retrieving beans (like bindings classes) from the ApplicationContext
 * - Cleaning up resources after test execution
 *
 * @param testClass The test class to manage Spring context for
 */
class SpringContextAdapter(
    private val testClass: Class<*>,
) {
    private var testContextManager: TestContextManager? = null
    private var testInstance: Any? = null

    /**
     * Initializes the Spring context for the test class.
     *
     * This creates a TestContextManager, instantiates the test class,
     * and prepares it for dependency injection. After this call,
     * the Spring ApplicationContext is available and beans can be retrieved.
     *
     * @throws IllegalStateException if Spring context initialization fails
     */
    fun initializeContext() {
        try {
            // Create TestContextManager for the test class
            testContextManager = TestContextManager(testClass)

            // Create test instance for Spring's lifecycle
            val instance = testClass.getDeclaredConstructor().newInstance()
            testInstance = instance

            // Call beforeTestClass to initialize context
            testContextManager!!.beforeTestClass()

            // Prepare test instance triggers dependency injection
            testContextManager!!.prepareTestInstance(instance)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Failed to initialize Spring context for test class: ${testClass.name}. " +
                    "Ensure the class has @SpringBootTest annotation and a valid Spring configuration.",
                e,
            )
        }
    }

    /**
     * Retrieves a bean from the Spring ApplicationContext.
     *
     * @param beanClass The class of the bean to retrieve
     * @return The bean instance
     * @throws IllegalStateException if context not initialized or bean not found
     */
    fun <T> getBean(beanClass: Class<T>): T {
        val context = getApplicationContext()
        return try {
            context.getBean(beanClass)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Bindings class '${beanClass.name}' is not registered as a Spring bean. " +
                    "Add @Component annotation to the class or define a @Bean method.",
                e,
            )
        }
    }

    /**
     * Returns the Spring ApplicationContext.
     *
     * @return The application context
     * @throws IllegalStateException if context not initialized
     */
    fun getApplicationContext(): ApplicationContext {
        val manager =
            testContextManager
                ?: throw IllegalStateException(
                    "Spring context not initialized. Call initializeContext() first.",
                )

        return manager.testContext.applicationContext
    }

    /**
     * Cleans up the Spring context resources.
     *
     * This should be called after all scenarios have executed to properly
     * release Spring context resources following standard TestContext semantics.
     */
    fun cleanup() {
        try {
            testContextManager?.afterTestClass()
        } catch (e: Exception) {
            // Log but don't fail - cleanup exceptions shouldn't break test results
            System.err.println("Warning: Spring context cleanup warning: ${e.message}")
        } finally {
            testContextManager = null
            testInstance = null
        }
    }
}
