package io.github.ktakashi.lemoncheck.junit.engine

import io.github.ktakashi.lemoncheck.dsl.LemonCheckSuite
import io.github.ktakashi.lemoncheck.executor.ScenarioExecutor
import io.github.ktakashi.lemoncheck.junit.DefaultBindings
import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings
import io.github.ktakashi.lemoncheck.junit.LemonCheckScenarios
import io.github.ktakashi.lemoncheck.junit.discovery.ScenarioDiscovery
import io.github.ktakashi.lemoncheck.junit.spi.BindingsProvider
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.scenario.ScenarioLoader
import org.junit.jupiter.api.Disabled
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.ClasspathRootSelector
import org.junit.platform.engine.discovery.PackageSelector
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import java.io.InputStreamReader
import java.net.URL
import java.util.ServiceLoader

/**
 * JUnit 5 TestEngine implementation for LemonCheck scenarios.
 *
 * This engine discovers and executes .scenario files based on the
 * @LemonCheckScenarios annotation. It integrates with the JUnit Platform
 * to provide IDE support, CI/CD integration, and build tool compatibility.
 *
 * Usage:
 * ```
 * @IncludeEngines("lemoncheck")
 * @LemonCheckScenarios(locations = "scenarios/test.scenario")
 * public class MyApiTest {
 * }
 * ```
 */
class LemonCheckTestEngine : TestEngine {
    companion object {
        const val ENGINE_ID = "lemoncheck"
    }

    // Lazy-loaded BindingsProvider instances discovered via ServiceLoader
    private val bindingsProviders: List<BindingsProvider> by lazy {
        ServiceLoader
            .load(BindingsProvider::class.java)
            .toList()
            .sortedByDescending { it.priority() }
    }

    override fun getId(): String = ENGINE_ID

    override fun discover(
        discoveryRequest: EngineDiscoveryRequest,
        uniqueId: UniqueId,
    ): TestDescriptor {
        val engineDescriptor = LemonCheckEngineDescriptor(uniqueId)

        // Find classes with @LemonCheckScenarios annotation
        val classSelectors = discoveryRequest.getSelectorsByType(ClassSelector::class.java)
        val packageSelectors = discoveryRequest.getSelectorsByType(PackageSelector::class.java)
        val classpathSelectors = discoveryRequest.getSelectorsByType(ClasspathRootSelector::class.java)

        // Process class selectors
        for (selector in classSelectors) {
            val testClass = selector.javaClass
            if (testClass.isAnnotationPresent(LemonCheckScenarios::class.java)) {
                discoverScenariosForClass(engineDescriptor, selector.javaClass)
            }
        }

        // Process explicit class selectors (when @IncludeEngines is used)
        for (selector in classSelectors) {
            val clazz = selector.javaClass
            try {
                val loadedClass = Class.forName(clazz.name)
                if (loadedClass.isAnnotationPresent(LemonCheckScenarios::class.java)) {
                    discoverScenariosForClass(engineDescriptor, loadedClass)
                }
            } catch (e: ClassNotFoundException) {
                // Class not found, skip
            }
        }

        // For package selectors, scan for annotated classes
        for (selector in packageSelectors) {
            discoverClassesInPackage(engineDescriptor, selector.packageName)
        }

        return engineDescriptor
    }

    private fun discoverScenariosForClass(
        engineDescriptor: EngineDescriptor,
        testClass: Class<*>,
    ) {
        val annotation = testClass.getAnnotation(LemonCheckScenarios::class.java) ?: return

        // Skip classes marked with @Disabled
        if (testClass.isAnnotationPresent(Disabled::class.java)) {
            return
        }

        // Check if class descriptor already exists
        val existingDescriptor =
            engineDescriptor.children.find {
                it is ClassTestDescriptor && it.testClass == testClass
            }
        if (existingDescriptor != null) return

        val classUniqueId = engineDescriptor.uniqueId.append("class", testClass.name)
        val classDescriptor = ClassTestDescriptor(classUniqueId, testClass)

        // Configuration is read from @LemonCheckConfiguration in ClassTestDescriptor init

        // Discover scenario files
        val classLoader = testClass.classLoader
        val locations = annotation.locations

        if (locations.isEmpty()) {
            // No locations specified, nothing to discover
            return
        }

        val scenarios = ScenarioDiscovery.discoverScenarios(classLoader, locations)

        if (scenarios.isEmpty()) {
            // No scenarios found - we'll report this during execution
            // but still add the class descriptor for visibility
        }

        for (scenario in scenarios) {
            val scenarioId = classUniqueId.append("scenario", scenario.name.removeSuffix(".scenario"))
            val scenarioDescriptor =
                ScenarioTestDescriptor(
                    uniqueId = scenarioId,
                    displayName = scenario.name,
                    scenarioPath = scenario.path,
                    scenarioSource = scenario.url,
                )
            classDescriptor.addChild(scenarioDescriptor)
        }

        if (classDescriptor.children.isNotEmpty() || scenarios.isEmpty()) {
            engineDescriptor.addChild(classDescriptor)
        }
    }

    private fun discoverClassesInPackage(
        engineDescriptor: EngineDescriptor,
        packageName: String,
    ) {
        // This would require classpath scanning which is complex
        // For now, rely on explicit class selectors
        // Users can use @SelectClasses or similar
    }

    override fun execute(request: ExecutionRequest) {
        val engineDescriptor = request.rootTestDescriptor
        val listener = request.engineExecutionListener

        listener.executionStarted(engineDescriptor)

        for (classDescriptor in engineDescriptor.children) {
            if (classDescriptor !is ClassTestDescriptor) continue

            listener.executionStarted(classDescriptor)

            // Find a BindingsProvider that supports this test class
            val provider = findBindingsProvider(classDescriptor.testClass)

            try {
                // Initialize the provider before test execution
                provider?.initialize(classDescriptor.testClass)

                executeClassTests(classDescriptor, listener, provider)
                listener.executionFinished(classDescriptor, TestExecutionResult.successful())
            } catch (e: Exception) {
                listener.executionFinished(
                    classDescriptor,
                    TestExecutionResult.failed(e),
                )
            } finally {
                // Cleanup the provider after test execution
                try {
                    provider?.cleanup(classDescriptor.testClass)
                } catch (e: Exception) {
                    // Log cleanup error but don't fail the test
                    System.err.println("Warning: BindingsProvider cleanup failed: ${e.message}")
                }
            }
        }

        listener.executionFinished(engineDescriptor, TestExecutionResult.successful())
    }

    /**
     * Finds a BindingsProvider that supports the given test class.
     * Returns null if no provider supports the class (fallback to reflection).
     */
    private fun findBindingsProvider(testClass: Class<*>): BindingsProvider? = bindingsProviders.firstOrNull { it.supports(testClass) }

    private fun executeClassTests(
        classDescriptor: ClassTestDescriptor,
        listener: org.junit.platform.engine.EngineExecutionListener,
        provider: BindingsProvider?,
    ) {
        // Initialize suite and executor
        val suite = LemonCheckSuite.create()
        val bindings = createBindings(classDescriptor, provider)

        // Apply bindings configuration
        bindings.configure(suite.configuration)
        val bindingsMap = bindings.getBindings()
        bindingsMap["baseUrl"]?.let {
            suite.configuration.baseUrl = it.toString()
        }

        // Load OpenAPI spec if specified
        val specPath = bindings.getOpenApiSpec() ?: classDescriptor.openApiSpec
        if (!specPath.isNullOrBlank()) {
            suite.spec(specPath)
        }

        val executor = ScenarioExecutor(suite.specRegistry, suite.configuration)
        val scenarioLoader = ScenarioLoader()

        for (scenarioDescriptor in classDescriptor.children) {
            if (scenarioDescriptor !is ScenarioTestDescriptor) continue

            listener.executionStarted(scenarioDescriptor)

            try {
                val scenarios = loadScenarioFromUrl(scenarioLoader, scenarioDescriptor.scenarioSource)

                if (scenarios.isEmpty()) {
                    listener.executionFinished(
                        scenarioDescriptor,
                        TestExecutionResult.failed(
                            IllegalStateException("No scenarios found in ${scenarioDescriptor.scenarioPath}"),
                        ),
                    )
                    continue
                }

                var allPassed = true
                var failureReason: Throwable? = null

                for (scenario in scenarios) {
                    val result = executor.execute(scenario)

                    // Report step results for visibility
                    println("\n=== Scenario: ${scenario.name} ===")
                    for (stepResult in result.stepResults) {
                        val statusIcon =
                            when (stepResult.status) {
                                ResultStatus.PASSED -> "✓"
                                ResultStatus.FAILED -> "✗"
                                ResultStatus.ERROR -> "!"
                                ResultStatus.SKIPPED -> "-"
                                ResultStatus.PENDING -> "?"
                            }
                        println("  $statusIcon ${stepResult.step.description}: ${stepResult.status}")

                        // Show HTTP status if available
                        stepResult.statusCode?.let { status ->
                            println("    HTTP Status: $status")
                        }

                        // Show assertion results
                        for (assertion in stepResult.assertionResults) {
                            val assertIcon = if (assertion.passed) "✓" else "✗"
                            println("    $assertIcon ${assertion.message}")
                        }

                        // Show error if present
                        stepResult.error?.let { error ->
                            println("    Error: ${error.message}")
                        }
                    }
                    println("  Result: ${result.status} (${result.duration.toMillis()}ms)")
                    println()

                    if (result.status != ResultStatus.PASSED) {
                        allPassed = false
                        val failedSteps =
                            result.stepResults
                                .filter { it.status != ResultStatus.PASSED }
                                .joinToString("\n") { step ->
                                    "  - ${step.step.description}: ${step.status}" +
                                        (step.error?.let { " - ${it.message}" } ?: "") +
                                        step.assertionResults
                                            .filter { !it.passed }
                                            .joinToString("") { "\n      ✗ ${it.message}" }
                                }
                        failureReason =
                            AssertionError(
                                "Scenario '${scenario.name}' failed:\n$failedSteps",
                            )
                        break
                    }
                }

                if (allPassed) {
                    listener.executionFinished(scenarioDescriptor, TestExecutionResult.successful())
                } else {
                    listener.executionFinished(
                        scenarioDescriptor,
                        TestExecutionResult.failed(failureReason!!),
                    )
                }
            } catch (e: Exception) {
                listener.executionFinished(
                    scenarioDescriptor,
                    TestExecutionResult.failed(e),
                )
            }
        }
    }

    /**
     * Creates a LemonCheckBindings instance for the given class descriptor.
     *
     * If a BindingsProvider is available and supports the test class, it will be used
     * to create the bindings (enabling dependency injection frameworks like Spring).
     * Otherwise, falls back to direct instantiation via reflection.
     *
     * @param classDescriptor The test class descriptor containing bindings class info
     * @param provider The BindingsProvider to use, or null for reflection fallback
     * @return The created LemonCheckBindings instance
     */
    private fun createBindings(
        classDescriptor: ClassTestDescriptor,
        provider: BindingsProvider?,
    ): LemonCheckBindings {
        val bindingsClass = classDescriptor.bindingsClass ?: DefaultBindings::class.java

        // If a provider is available, use it to create bindings
        if (provider != null) {
            return try {
                provider.createBindings(classDescriptor.testClass, bindingsClass)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "BindingsProvider failed to create bindings for class: ${bindingsClass.name}. " +
                        "Cause: ${e.message}",
                    e,
                )
            }
        }

        // Fallback: direct instantiation via reflection
        return try {
            bindingsClass.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            throw IllegalStateException(
                "Cannot instantiate bindings class: ${bindingsClass.name}. " +
                    "Ensure it has a public no-arg constructor.",
                e,
            )
        }
    }

    private fun loadScenarioFromUrl(
        loader: ScenarioLoader,
        url: URL,
    ): List<io.github.ktakashi.lemoncheck.model.Scenario> =
        try {
            url.openStream().use { input ->
                val content = InputStreamReader(input).readText()
                val fileName = url.path.substringAfterLast("/")
                loader.loadScenariosFromString(content, fileName)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to load scenario from ${url.path}: ${e.message}",
                e,
            )
        }
}
