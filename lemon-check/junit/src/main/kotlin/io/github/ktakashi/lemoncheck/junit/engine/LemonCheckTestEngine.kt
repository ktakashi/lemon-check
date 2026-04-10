package io.github.ktakashi.lemoncheck.junit.engine

import io.github.ktakashi.lemoncheck.dsl.LemonCheckSuite
import io.github.ktakashi.lemoncheck.executor.ScenarioExecutor
import io.github.ktakashi.lemoncheck.junit.DefaultBindings
import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings
import io.github.ktakashi.lemoncheck.junit.LemonCheckConfiguration
import io.github.ktakashi.lemoncheck.junit.LemonCheckScenarios
import io.github.ktakashi.lemoncheck.junit.discovery.FragmentDiscovery
import io.github.ktakashi.lemoncheck.junit.discovery.ScenarioDiscovery
import io.github.ktakashi.lemoncheck.junit.plugin.ConsoleOutputPlugin
import io.github.ktakashi.lemoncheck.junit.spi.BindingsProvider
import io.github.ktakashi.lemoncheck.model.FragmentRegistry
import io.github.ktakashi.lemoncheck.plugin.PluginRegistry
import io.github.ktakashi.lemoncheck.runner.ScenarioRunner
import io.github.ktakashi.lemoncheck.scenario.ScenarioFileContent
import io.github.ktakashi.lemoncheck.scenario.ScenarioLoader
import io.github.ktakashi.lemoncheck.step.AnnotationStepScanner
import io.github.ktakashi.lemoncheck.step.DefaultStepRegistry
import io.github.ktakashi.lemoncheck.step.PackageStepScanner
import io.github.ktakashi.lemoncheck.step.StepRegistry
import org.junit.jupiter.api.Disabled
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.ClassSelector
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

        // Get per-spec base URLs
        val specBaseUrls = bindings.getSpecBaseUrls()

        // Load OpenAPI spec if specified
        val specPath = bindings.getOpenApiSpec() ?: classDescriptor.openApiSpec
        if (!specPath.isNullOrBlank()) {
            val defaultBaseUrl = specBaseUrls["default"]
            suite.spec(specPath) {
                if (defaultBaseUrl != null) {
                    baseUrl = defaultBaseUrl
                }
            }
        }

        // Register additional named specs with their base URLs
        bindings.getAdditionalSpecs().forEach { (name, path) ->
            val specBaseUrl = specBaseUrls[name]
            suite.spec(name, path) {
                if (specBaseUrl != null) {
                    baseUrl = specBaseUrl
                }
            }
        }

        // Create plugin registry with user plugins
        val pluginRegistry = createPluginRegistry(classDescriptor)
        val scenarioLoader = ScenarioLoader()

        // Load fragments from 'fragments/' directory (if exists)
        val fragmentRegistry = loadFragmentsForClass(classDescriptor, scenarioLoader)

        // Create runner for executing scenarios
        val runner = ScenarioRunner(suite.specRegistry, suite.configuration, pluginRegistry, fragmentRegistry)

        // Begin test execution lifecycle (invokes onTestExecutionStart on plugins)
        runner.beginExecution()

        for (scenarioDescriptor in classDescriptor.children) {
            if (scenarioDescriptor !is ScenarioTestDescriptor) continue

            listener.executionStarted(scenarioDescriptor)

            try {
                val fileContent = loadScenarioFromUrl(scenarioLoader, scenarioDescriptor.scenarioSource)
                val scenarios = fileContent.scenarios
                val fileParameters = fileContent.parameters

                if (scenarios.isEmpty()) {
                    listener.executionFinished(
                        scenarioDescriptor,
                        TestExecutionResult.failed(
                            IllegalStateException("No scenarios found in ${scenarioDescriptor.scenarioPath}"),
                        ),
                    )
                    continue
                }

                // Create new console plugin per scenarioDescriptor with JUnit listener
                val consolePlugin =
                    ConsoleOutputPlugin(
                        listener = listener,
                        scenarioDescriptor = scenarioDescriptor,
                    )
                pluginRegistry.replace(consolePlugin)

                // Apply file-level parameters if present
                if (fileParameters.isNotEmpty()) {
                    // Create a modified executor and shared context for this file
                    val fileConfig = suite.configuration.withParameters(fileParameters)

                    // Apply per-spec base URL overrides from parameters
                    fileParameters.forEach { (key, value) ->
                        if (key.startsWith("baseUrl.")) {
                            val specName = key.removePrefix("baseUrl.")
                            if (suite.specRegistry.specNames().contains(specName)) {
                                suite.specRegistry.updateBaseUrl(specName, value.toString())
                            }
                        }
                    }

                    val fileExecutor = ScenarioExecutor(suite.specRegistry, fileConfig, pluginRegistry, fragmentRegistry)

                    // Initialize shared context if shareVariablesAcrossScenarios is enabled
                    val sharedContext =
                        if (fileConfig.shareVariablesAcrossScenarios) {
                            io.github.ktakashi.lemoncheck.context.ExecutionContext()
                        } else {
                            null
                        }

                    for (scenario in scenarios) {
                        fileExecutor.execute(scenario, sharedContext)
                        if (!consolePlugin.isAllPassed()) {
                            break
                        }
                    }
                } else {
                    // No file-level parameters - use the default runner
                    for (scenario in scenarios) {
                        runner.executeScenario(scenario)
                        if (!consolePlugin.isAllPassed()) {
                            break
                        }
                    }
                }

                // Report result via plugin (fires JUnit executionFinished)
                consolePlugin.reportResult()
            } catch (e: Exception) {
                listener.executionFinished(
                    scenarioDescriptor,
                    TestExecutionResult.failed(e),
                )
            }
        }

        // End test execution lifecycle (invokes onTestExecutionEnd on plugins)
        runner.endExecution()
    }

    /**
     * Creates a PluginRegistry with plugins configured via @LemonCheckConfiguration.
     *
     * @param classDescriptor The test class descriptor
     * @return The configured PluginRegistry
     */
    private fun createPluginRegistry(classDescriptor: ClassTestDescriptor): PluginRegistry {
        val registry = PluginRegistry()

        // Note: ConsoleOutputPlugin is created and replaced per scenarioDescriptor
        // in executeClassTests to integrate with JUnit listener

        val testClass = classDescriptor.testClass
        val config = testClass.getAnnotation(LemonCheckConfiguration::class.java)

        if (config != null) {
            // Register class-based plugins
            for (pluginClass in config.pluginClasses) {
                try {
                    registry.register(pluginClass)
                } catch (e: Exception) {
                    System.err.println("Warning: Failed to register plugin class ${pluginClass.qualifiedName}: ${e.message}")
                }
            }

            // Register name-based plugins
            for (pluginName in config.plugins) {
                try {
                    registry.registerByName(pluginName)
                } catch (e: NotImplementedError) {
                    // Name-based plugins not fully implemented for all types yet
                    System.err.println("Warning: Plugin '$pluginName' registration not yet supported: ${e.message}")
                } catch (e: Exception) {
                    System.err.println("Warning: Failed to register plugin '$pluginName': ${e.message}")
                }
            }
        }

        return registry
    }

    /**
     * Load fragments for a test class based on @LemonCheckScenarios annotation.
     *
     * Uses FragmentDiscovery to discover .fragment files matching the patterns
     * specified in the `fragments` property of the annotation.
     *
     * @param classDescriptor The test class descriptor
     * @param scenarioLoader ScenarioLoader for parsing fragment files
     * @return FragmentRegistry populated with loaded fragments
     */
    private fun loadFragmentsForClass(
        classDescriptor: ClassTestDescriptor,
        scenarioLoader: ScenarioLoader,
    ): FragmentRegistry {
        val registry = FragmentRegistry()
        val testClass = classDescriptor.testClass
        val classLoader = testClass.classLoader

        // Get fragment locations from annotation
        val fragmentLocations = classDescriptor.fragmentLocations
        if (fragmentLocations.isEmpty()) {
            return registry
        }

        // Discover fragments using FragmentDiscovery
        val discoveredFragments = FragmentDiscovery.discoverFragments(classLoader, fragmentLocations)

        for (fragment in discoveredFragments) {
            try {
                fragment.url.openStream().use { input ->
                    val content = input.bufferedReader().readText()
                    val fragments = scenarioLoader.loadFragmentsFromString(content, fragment.name)
                    registry.registerAll(fragments)
                }
            } catch (e: Exception) {
                System.err.println("Warning: Failed to load fragment from ${fragment.path}: ${e.message}")
            }
        }

        return registry
    }

    /**
     * Creates a StepRegistry with step definitions configured via @LemonCheckConfiguration.
     *
     * @param classDescriptor The test class descriptor
     * @return The configured StepRegistry
     */
    private fun createStepRegistry(classDescriptor: ClassTestDescriptor): StepRegistry {
        val registry = DefaultStepRegistry()
        val testClass = classDescriptor.testClass
        val config = testClass.getAnnotation(LemonCheckConfiguration::class.java)

        if (config != null) {
            val annotationScanner = AnnotationStepScanner()
            val packageScanner = PackageStepScanner()

            // Register step definitions from step classes
            for (stepClass in config.stepClasses) {
                try {
                    val definitions = annotationScanner.scan(stepClass.java)
                    registry.registerAll(definitions)
                } catch (e: Exception) {
                    System.err.println(
                        "Warning: Failed to scan step class ${stepClass.qualifiedName}: ${e.message}",
                    )
                }
            }

            // Register step definitions from packages
            for (packageName in config.stepPackages) {
                try {
                    val definitions = packageScanner.scan(packageName, testClass.classLoader)
                    registry.registerAll(definitions)
                } catch (e: Exception) {
                    System.err.println(
                        "Warning: Failed to scan package '$packageName' for steps: ${e.message}",
                    )
                }
            }
        }

        return registry
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
    ): ScenarioFileContent =
        try {
            url.openStream().use { input ->
                val content = InputStreamReader(input).readText()
                val fileName = url.path.substringAfterLast("/")
                loader.loadFileContentFromString(content, fileName)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to load scenario from ${url.path}: ${e.message}",
                e,
            )
        }
}
