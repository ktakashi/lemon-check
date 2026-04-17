package org.berrycrush.junit

import org.berrycrush.assertion.AnnotationAssertionScanner
import org.berrycrush.assertion.AssertionRegistry
import org.berrycrush.assertion.DefaultAssertionRegistry
import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.dsl.BerryCrushSuite
import org.berrycrush.exception.ConfigurationException
import org.berrycrush.executor.BerryCrushScenarioExecutor
import org.berrycrush.model.Scenario
import org.berrycrush.step.AnnotationStepScanner
import org.berrycrush.step.DefaultStepRegistry
import org.berrycrush.step.PackageStepScanner
import org.berrycrush.step.StepRegistry
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import java.util.stream.Stream
import org.berrycrush.junit.BerryCrushConfiguration as BerryCrushConfigAnnotation

/**
 * JUnit 5 extension for BerryCrush scenarios.
 *
 * This extension integrates BerryCrush scenarios with JUnit 5's test framework,
 * enabling scenario execution as JUnit tests with full IDE and CI support.
 *
 * The extension creates a [BerryCrushSuite] in `beforeAll` and the
 * [BerryCrushScenarioExecutor] is created lazily when first requested. This allows
 * configuration changes (e.g., dynamic port from Spring Boot's `@LocalServerPort`)
 * to be applied before the executor is created.
 *
 * ## Usage with static configuration
 *
 * ```kotlin
 * @ExtendWith(BerryCrushExtension::class)
 * @BerryCrushSpec("api-spec.yaml", baseUrl = "http://localhost:8080")
 * class ApiTest {
 *     @Test
 *     fun testApi(
 *         suite: BerryCrushSuite,
 *         executor: BerryCrushScenarioExecutor,
 *     ) {
 *         val scenario = suite.scenario("Test") { ... }
 *         val result = executor.execute(scenario)
 *     }
 * }
 * ```
 *
 * ## Usage with dynamic configuration (e.g., Spring Boot)
 *
 * For Spring Boot tests with random ports, inject config in `@BeforeEach` and
 * suite/executor in `@Test` methods:
 *
 * ```kotlin
 * @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
 * @TestInstance(TestInstance.Lifecycle.PER_CLASS)
 * @ExtendWith(BerryCrushExtension::class)
 * @BerryCrushSpec("classpath:/api-spec.yaml")
 * class ApiTest {
 *     @LocalServerPort
 *     private var port: Int = 0
 *
 *     @BeforeEach
 *     fun setup(config: BerryCrushConfiguration) {
 *         // Set dynamic port - Configuration is shared, changes affect executor
 *         config.baseUrl = "http://localhost:$port/api"
 *     }
 *
 *     @Test
 *     fun testApi(
 *         suite: BerryCrushSuite,
 *         executor: BerryCrushScenarioExecutor,
 *     ) {
 *         val scenario = suite.scenario("Test") { ... }
 *         val result = executor.execute(scenario)
 *     }
 * }
 * ```
 *
 * ## Nested Test Classes
 *
 * The extension automatically shares the suite with `@Nested` inner test classes.
 * Configuration set in the outer class's `@BeforeEach` is inherited by nested classes.
 *
 * ## Supported Parameter Types
 *
 * - [BerryCrushSuite]: The test suite containing the OpenAPI spec
 * - [BerryCrushConfiguration]: Configuration object for setting baseUrl, timeout, etc.
 * - [BerryCrushScenarioExecutor]: Executor for running scenarios
 */
class BerryCrushExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    ParameterResolver,
    TestTemplateInvocationContextProvider {
    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(BerryCrushExtension::class.java)
        private const val SUITE_KEY = "berryCrushSuite"
        private const val EXECUTOR_KEY = "scenarioExecutor"
        private const val STEP_REGISTRY_KEY = "stepRegistry"
        private const val ASSERTION_REGISTRY_KEY = "assertionRegistry"
        private const val CLASSPATH_PREFIX = "classpath:"
    }

    override fun beforeAll(context: ExtensionContext) {
        // Check if parent context already has a suite (for nested test classes)
        val parentSuite = findParentSuite(context)
        if (parentSuite != null) {
            // Nested class - reuse parent's suite
            context.getStore(NAMESPACE).put(SUITE_KEY, parentSuite)
            return
        }

        val testClass = context.requiredTestClass
        val specAnnotation = testClass.getAnnotation(BerryCrushSpec::class.java)

        val suite = BerryCrushSuite.create()

        // Load spec from annotation
        specAnnotation?.paths?.forEach { path ->
            val resolvedPath = resolvePath(path, testClass)
            suite.spec(resolvedPath)
        }

        // Apply initial configuration from annotation
        specAnnotation?.baseUrl?.takeIf { it.isNotBlank() }?.let {
            suite.configuration.baseUrl = it
        }

        context.getStore(NAMESPACE).put(SUITE_KEY, suite)
    }

    /**
     * Resolve a spec path, supporting both file paths and classpath resources.
     *
     * Paths prefixed with `classpath:` are resolved from the test class's classloader.
     * Example: `classpath:/petstore.yaml` or `classpath:specs/api.yaml`
     */
    private fun resolvePath(
        path: String,
        testClass: Class<*>,
    ): String {
        if (!path.startsWith(CLASSPATH_PREFIX)) {
            return path
        }

        val resourcePath = path.removePrefix(CLASSPATH_PREFIX)
        val resource =
            testClass.getResource(resourcePath)
                ?: testClass.classLoader.getResource(resourcePath.removePrefix("/"))
                ?: throw ConfigurationException(
                    "Classpath resource not found: $resourcePath. " +
                        "Make sure the file exists in src/test/resources or src/main/resources.",
                )

        return resource.path
    }

    /**
     * Find parent context's suite for nested test classes.
     */
    private fun findParentSuite(context: ExtensionContext): BerryCrushSuite? {
        var parent = context.parent.orElse(null)
        while (parent != null) {
            val suite = parent.getStore(NAMESPACE).get(SUITE_KEY, BerryCrushSuite::class.java)
            if (suite != null) return suite
            parent = parent.parent.orElse(null)
        }
        return null
    }

    override fun beforeEach(context: ExtensionContext) {
        // Clear executor so it gets recreated with latest config when requested
        // This allows test's @BeforeEach to configure dynamic values (e.g., port)
        // before the executor is created
        context.getStore(NAMESPACE).remove(EXECUTOR_KEY)
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean {
        val paramType = parameterContext.parameter.type
        return paramType == BerryCrushSuite::class.java ||
            paramType == BerryCrushScenarioExecutor::class.java ||
            paramType == BerryCrushConfiguration::class.java
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any =
        when (val paramType = parameterContext.parameter.type) {
            BerryCrushSuite::class.java -> getSuite(extensionContext)
            BerryCrushScenarioExecutor::class.java -> getOrCreateExecutor(extensionContext)
            BerryCrushConfiguration::class.java -> getSuite(extensionContext).configuration
            else -> throw ConfigurationException("Unsupported parameter type: $paramType")
        }

    override fun supportsTestTemplate(context: ExtensionContext): Boolean =
        context.requiredTestMethod.isAnnotationPresent(BerryCrushScenarios::class.java) ||
            context.requiredTestClass.isAnnotationPresent(BerryCrushScenarios::class.java)

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> =
        getSuite(context).allScenarios().stream().map { scenario ->
            ScenarioInvocationContext(scenario, getOrCreateExecutor(context))
        }

    private fun getSuite(context: ExtensionContext): BerryCrushSuite {
        // Walk up the context hierarchy to find the suite (handles nested test classes)
        var current: ExtensionContext? = context
        while (current != null) {
            val suite = current.getStore(NAMESPACE).get(SUITE_KEY, BerryCrushSuite::class.java)
            if (suite != null) return suite
            current = current.parent.orElse(null)
        }
        throw ConfigurationException("BerryCrushSuite not initialized. Is @ExtendWith(BerryCrushExtension::class) present?")
    }

    /**
     * Get or create the executor. Creating lazily allows test's @BeforeEach
     * to configure dynamic values before the executor is created.
     */
    private fun getOrCreateExecutor(context: ExtensionContext): BerryCrushScenarioExecutor {
        var executor = context.getStore(NAMESPACE).get(EXECUTOR_KEY, BerryCrushScenarioExecutor::class.java)
        if (executor == null) {
            val suite = getSuite(context)
            val stepRegistry = getOrCreateStepRegistry(context)
            val assertionRegistry = getOrCreateAssertionRegistry(context)
            executor =
                BerryCrushScenarioExecutor(
                    specRegistry = suite.specRegistry,
                    configuration = suite.configuration,
                    stepRegistry = stepRegistry,
                    assertionRegistry = assertionRegistry,
                )
            context.getStore(NAMESPACE).put(EXECUTOR_KEY, executor)
        }
        return executor
    }

    /**
     * Get or create the step registry by scanning step classes from configuration.
     */
    private fun getOrCreateStepRegistry(context: ExtensionContext): StepRegistry? {
        // Check if already created
        var registry = context.getStore(NAMESPACE).get(STEP_REGISTRY_KEY, StepRegistry::class.java)
        if (registry != null) {
            return registry
        }

        // Get the test class to read configuration annotation
        val testClass = findRootTestClass(context)
        val configAnnotation =
            testClass?.getAnnotation(
                BerryCrushConfigAnnotation::class.java,
            )

        if (configAnnotation == null) {
            return null
        }

        val stepClasses = configAnnotation.stepClasses
        val stepPackages = configAnnotation.stepPackages

        if (stepClasses.isEmpty() && stepPackages.isEmpty()) {
            return null
        }

        // Create registry and scan classes
        registry = DefaultStepRegistry()
        val scanner = AnnotationStepScanner()

        // Scan step classes
        for (klass in stepClasses) {
            val definitions = scanner.scan(klass.java)
            registry.registerAll(definitions)
        }

        // Scan step packages
        if (stepPackages.isNotEmpty()) {
            val packageScanner = PackageStepScanner()
            for (packageName in stepPackages) {
                val definitions = packageScanner.scan(packageName)
                registry.registerAll(definitions)
            }
        }

        context.getStore(NAMESPACE).put(STEP_REGISTRY_KEY, registry)
        return registry
    }

    /**
     * Get or create the assertion registry for custom assertions.
     *
     * Scans for `@Assertion` annotated methods in classes specified by
     * `assertionClasses` and `assertionPackages` in `@BerryCrushConfiguration`.
     *
     * @param context The JUnit extension context
     * @return The assertion registry, or null if no assertion classes configured
     */
    private fun getOrCreateAssertionRegistry(context: ExtensionContext): AssertionRegistry? {
        // Check if already exists
        var registry = context.getStore(NAMESPACE).get(ASSERTION_REGISTRY_KEY, AssertionRegistry::class.java)
        if (registry != null) {
            return registry
        }

        // Get the test class to read configuration annotation
        val testClass = findRootTestClass(context)
        val configAnnotation =
            testClass?.getAnnotation(
                BerryCrushConfigAnnotation::class.java,
            )

        if (configAnnotation == null) {
            return null
        }

        val assertionClasses = configAnnotation.assertionClasses
        val assertionPackages = configAnnotation.assertionPackages

        if (assertionClasses.isEmpty() && assertionPackages.isEmpty()) {
            return null
        }

        // Create registry and scan classes
        registry = DefaultAssertionRegistry()
        val scanner = AnnotationAssertionScanner()

        // Scan assertion classes
        for (klass in assertionClasses) {
            val definitions = scanner.scan(klass.java)
            registry.registerAll(definitions)
        }

        // Scan assertion packages
        if (assertionPackages.isNotEmpty()) {
            // TODO: Implement PackageAssertionScanner if needed
            // For now, only class-based registration is supported
        }

        context.getStore(NAMESPACE).put(ASSERTION_REGISTRY_KEY, registry)
        return registry
    }

    /**
     * Find the root test class (handles nested classes).
     */
    private fun findRootTestClass(context: ExtensionContext): Class<*>? {
        var current: ExtensionContext? = context
        var rootClass: Class<*>? = null
        while (current != null) {
            current.testClass.ifPresent { rootClass = it }
            current = current.parent.orElse(null)
        }
        return rootClass
    }

    /**
     * Context for a single scenario invocation.
     */
    private class ScenarioInvocationContext(
        private val scenario: Scenario,
        private val executor: BerryCrushScenarioExecutor,
    ) : TestTemplateInvocationContext {
        override fun getDisplayName(invocationIndex: Int): String = scenario.name

        override fun getAdditionalExtensions(): List<org.junit.jupiter.api.extension.Extension> =
            listOf(
                ScenarioParameterResolver(scenario, executor),
            )
    }

    /**
     * Parameter resolver for individual scenarios.
     */
    private class ScenarioParameterResolver(
        private val scenario: Scenario,
        private val executor: BerryCrushScenarioExecutor,
    ) : ParameterResolver {
        override fun supportsParameter(
            parameterContext: ParameterContext,
            extensionContext: ExtensionContext,
        ): Boolean = parameterContext.parameter.type == Scenario::class.java

        override fun resolveParameter(
            parameterContext: ParameterContext,
            extensionContext: ExtensionContext,
        ): Any = scenario
    }
}
