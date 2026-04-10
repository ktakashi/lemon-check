package io.github.ktakashi.lemoncheck.junit.engine

import io.github.ktakashi.lemoncheck.context.ExecutionContext
import io.github.ktakashi.lemoncheck.dsl.LemonCheckSuite
import io.github.ktakashi.lemoncheck.executor.ScenarioExecutor
import io.github.ktakashi.lemoncheck.junit.DefaultBindings
import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings
import io.github.ktakashi.lemoncheck.junit.LemonCheckConfiguration
import io.github.ktakashi.lemoncheck.junit.discovery.FragmentDiscovery
import io.github.ktakashi.lemoncheck.junit.spi.BindingsProvider
import io.github.ktakashi.lemoncheck.model.FragmentRegistry
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.model.ScenarioResult
import io.github.ktakashi.lemoncheck.plugin.PluginRegistry
import io.github.ktakashi.lemoncheck.runner.ScenarioRunner
import io.github.ktakashi.lemoncheck.scenario.ScenarioLoader
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestExecutionResult
import java.io.File

/**
 * Responsible for executing scenario tests and reporting results.
 *
 * This class handles:
 * - Initializing execution context (bindings, plugins, fragments)
 * - Executing scenario files with proper lifecycle management
 * - Reporting results to JUnit execution listener
 */
class ScenarioTestExecutor(
    private val bindingsProviders: List<BindingsProvider>,
) {
    /**
     * Execute all tests for a class descriptor.
     */
    fun executeClassTests(
        classDescriptor: ClassTestDescriptor,
        listener: EngineExecutionListener,
    ) {
        val provider = findBindingsProvider(classDescriptor.testClass)

        try {
            provider?.initialize(classDescriptor.testClass)
            executeWithContext(classDescriptor, listener, provider)
        } finally {
            runCatching { provider?.cleanup(classDescriptor.testClass) }
                .onFailure { System.err.println("Warning: BindingsProvider cleanup failed: ${it.message}") }
        }
    }

    private fun findBindingsProvider(testClass: Class<*>): BindingsProvider? = bindingsProviders.firstOrNull { it.supports(testClass) }

    private fun executeWithContext(
        classDescriptor: ClassTestDescriptor,
        listener: EngineExecutionListener,
        provider: BindingsProvider?,
    ) {
        val context = buildExecutionContext(classDescriptor, provider)

        context.runner.beginExecution()
        try {
            executeFileDescriptors(classDescriptor, context, listener)
        } finally {
            context.runner.endExecution()
        }
    }

    private fun executeFileDescriptors(
        classDescriptor: ClassTestDescriptor,
        context: TestExecutionContext,
        listener: EngineExecutionListener,
    ) {
        classDescriptor.children
            .filterIsInstance<ScenarioFileDescriptor>()
            .forEach { fileDescriptor ->
                executeFileDescriptor(fileDescriptor, classDescriptor, context, listener)
            }
    }

    private fun executeFileDescriptor(
        fileDescriptor: ScenarioFileDescriptor,
        classDescriptor: ClassTestDescriptor,
        context: TestExecutionContext,
        listener: EngineExecutionListener,
    ) {
        listener.executionStarted(fileDescriptor)

        val result =
            runCatching {
                val fileContext = buildFileContext(fileDescriptor, context)
                executeFileChildren(fileDescriptor, classDescriptor, fileContext, listener)
            }

        result.fold(
            onSuccess = { hasFailure ->
                val testResult =
                    if (hasFailure) {
                        TestExecutionResult.failed(AssertionError("One or more scenarios failed"))
                    } else {
                        TestExecutionResult.successful()
                    }
                listener.executionFinished(fileDescriptor, testResult)
            },
            onFailure = { e ->
                listener.executionFinished(fileDescriptor, TestExecutionResult.failed(e))
            },
        )
    }

    private fun executeFileChildren(
        fileDescriptor: ScenarioFileDescriptor,
        classDescriptor: ClassTestDescriptor,
        fileContext: FileExecutionContext,
        listener: EngineExecutionListener,
    ): Boolean {
        if (fileDescriptor.children.isEmpty()) {
            throw IllegalStateException("No scenarios found in ${fileDescriptor.scenarioPath}")
        }

        return fileDescriptor.children
            .map { child ->
                when (child) {
                    is IndividualScenarioDescriptor ->
                        executeScenario(child, classDescriptor, fileContext, listener)
                    is FeatureDescriptor ->
                        executeFeature(child, classDescriptor, fileContext, listener)
                    else -> false
                }
            }.any { it }
    }

    private fun executeFeature(
        featureDescriptor: FeatureDescriptor,
        classDescriptor: ClassTestDescriptor,
        fileContext: FileExecutionContext,
        listener: EngineExecutionListener,
    ): Boolean {
        listener.executionStarted(featureDescriptor)

        val hasFailure =
            featureDescriptor.children
                .filterIsInstance<IndividualScenarioDescriptor>()
                .map { executeScenario(it, classDescriptor, fileContext, listener) }
                .any { it }

        val result =
            if (hasFailure) {
                TestExecutionResult.failed(
                    AssertionError("One or more scenarios in feature '${featureDescriptor.featureName}' failed"),
                )
            } else {
                TestExecutionResult.successful()
            }
        listener.executionFinished(featureDescriptor, result)

        return hasFailure
    }

    /**
     * Execute a single scenario and report results.
     * @return true if the scenario failed
     */
    private fun executeScenario(
        scenarioDescriptor: IndividualScenarioDescriptor,
        classDescriptor: ClassTestDescriptor,
        fileContext: FileExecutionContext,
        listener: EngineExecutionListener,
    ): Boolean {
        // Check if scenario should be skipped based on tags
        if (!classDescriptor.shouldExecuteScenario(scenarioDescriptor.scenario.tags)) {
            listener.executionStarted(scenarioDescriptor)
            listener.executionFinished(scenarioDescriptor, TestExecutionResult.aborted(null))
            return false
        }

        listener.executionStarted(scenarioDescriptor)

        return runCatching {
            val sourceFile = File(fileContext.scenarioPath)

            // Add example row values to context if this is an outline scenario
            initializeContextWithExamples(scenarioDescriptor.scenario, fileContext.sharedContext)

            fileContext.executor.execute(scenarioDescriptor.scenario, fileContext.sharedContext, sourceFile)
        }.fold(
            onSuccess = { result -> handleScenarioResult(scenarioDescriptor, result, listener) },
            onFailure = { e ->
                listener.executionFinished(scenarioDescriptor, TestExecutionResult.failed(e))
                true
            },
        )
    }

    /**
     * Initialize execution context with example row values for scenario outlines.
     * For non-outline scenarios, this is a no-op.
     */
    private fun initializeContextWithExamples(
        scenario: io.github.ktakashi.lemoncheck.model.Scenario,
        context: ExecutionContext?,
    ) {
        context ?: return
        val examples = scenario.examples ?: return
        if (examples.isEmpty()) return

        // Use the first (and only) example row - outlines are expanded to one row per scenario
        val row = examples.first()
        row.values.forEach { (key, value) ->
            // Interpolate any variables in example values using existing context
            val resolvedValue =
                when (value) {
                    is String -> context.interpolate(value)
                    else -> value
                }
            context[key] = resolvedValue
        }
    }

    private fun handleScenarioResult(
        scenarioDescriptor: IndividualScenarioDescriptor,
        result: ScenarioResult,
        listener: EngineExecutionListener,
    ): Boolean =
        when (result.status) {
            ResultStatus.PASSED -> {
                listener.executionFinished(scenarioDescriptor, TestExecutionResult.successful())
                false
            }
            ResultStatus.SKIPPED -> {
                listener.executionFinished(scenarioDescriptor, TestExecutionResult.aborted(null))
                false
            }
            else -> {
                val message = buildFailedStepsMessage(scenarioDescriptor.scenario.name, result)
                listener.executionFinished(
                    scenarioDescriptor,
                    TestExecutionResult.failed(AssertionError(message)),
                )
                true
            }
        }

    // Context building methods

    private fun buildExecutionContext(
        classDescriptor: ClassTestDescriptor,
        provider: BindingsProvider?,
    ): TestExecutionContext {
        val suite = LemonCheckSuite.create()
        val bindings = createBindings(classDescriptor, provider)

        configureSpec(suite, bindings, classDescriptor)

        val pluginRegistry = createPluginRegistry(classDescriptor)
        val fragmentRegistry = loadFragments(classDescriptor)
        val runner = ScenarioRunner(suite.specRegistry, suite.configuration, pluginRegistry, fragmentRegistry)

        return TestExecutionContext(
            suite = suite,
            bindings = bindings,
            pluginRegistry = pluginRegistry,
            fragmentRegistry = fragmentRegistry,
            runner = runner,
        )
    }

    private fun buildFileContext(
        fileDescriptor: ScenarioFileDescriptor,
        context: TestExecutionContext,
    ): FileExecutionContext {
        val scenarioLoader = ScenarioLoader()
        val fileContent = ScenarioTestDiscoverer.loadScenarioFromUrl(scenarioLoader, fileDescriptor.scenarioSource)

        val fileConfig =
            if (fileContent.parameters.isNotEmpty()) {
                context.suite.configuration.withParameters(fileContent.parameters)
            } else {
                context.suite.configuration
            }

        // Apply per-spec base URL overrides from file parameters
        fileContent.parameters
            .filterKeys { it.startsWith("baseUrl.") }
            .forEach { (key, value) ->
                val specName = key.removePrefix("baseUrl.")
                if (context.suite.specRegistry
                        .specNames()
                        .contains(specName)
                ) {
                    context.suite.specRegistry.updateBaseUrl(specName, value.toString())
                }
            }

        val executor =
            ScenarioExecutor(
                context.suite.specRegistry,
                fileConfig,
                context.pluginRegistry,
                context.fragmentRegistry,
            )

        val sharedContext =
            if (fileConfig.shareVariablesAcrossScenarios) ExecutionContext() else null

        return FileExecutionContext(
            executor = executor,
            sharedContext = sharedContext,
            scenarioPath = fileDescriptor.scenarioPath,
        )
    }

    private fun configureSpec(
        suite: LemonCheckSuite,
        bindings: LemonCheckBindings,
        classDescriptor: ClassTestDescriptor,
    ) {
        bindings.configure(suite.configuration)

        bindings.getBindings()["baseUrl"]?.let {
            suite.configuration.baseUrl = it.toString()
        }

        val specBaseUrls = bindings.getSpecBaseUrls()
        val specPath = bindings.getOpenApiSpec() ?: classDescriptor.openApiSpec

        if (!specPath.isNullOrBlank()) {
            suite.spec(specPath) {
                specBaseUrls["default"]?.let { baseUrl = it }
            }
        }

        bindings.getAdditionalSpecs().forEach { (name, path) ->
            suite.spec(name, path) {
                specBaseUrls[name]?.let { baseUrl = it }
            }
        }
    }

    private fun createBindings(
        classDescriptor: ClassTestDescriptor,
        provider: BindingsProvider?,
    ): LemonCheckBindings {
        val bindingsClass = classDescriptor.bindingsClass ?: DefaultBindings::class.java

        return provider?.let {
            runCatching { it.createBindings(classDescriptor.testClass, bindingsClass) }
                .getOrElse { e ->
                    throw IllegalStateException(
                        "BindingsProvider failed to create bindings for class: ${bindingsClass.name}. " +
                            "Cause: ${e.message}",
                        e,
                    )
                }
        } ?: runCatching { bindingsClass.getDeclaredConstructor().newInstance() }.getOrElse { e ->
            throw IllegalStateException(
                "Cannot instantiate bindings class: ${bindingsClass.name}. " +
                    "Ensure it has a public no-arg constructor.",
                e,
            )
        }
    }

    private fun createPluginRegistry(classDescriptor: ClassTestDescriptor): PluginRegistry {
        val registry = PluginRegistry()
        val config =
            classDescriptor.testClass.getAnnotation(LemonCheckConfiguration::class.java)
                ?: return registry

        config.pluginClasses.forEach { pluginClass ->
            runCatching { registry.register(pluginClass) }
                .onFailure {
                    System.err.println("Warning: Failed to register plugin class ${pluginClass.qualifiedName}: ${it.message}")
                }
        }

        config.plugins.forEach { pluginName ->
            runCatching { registry.registerByName(pluginName) }
                .onFailure {
                    System.err.println("Warning: Failed to register plugin '$pluginName': ${it.message}")
                }
        }

        return registry
    }

    private fun loadFragments(classDescriptor: ClassTestDescriptor): FragmentRegistry {
        val registry = FragmentRegistry()
        val fragmentLocations = classDescriptor.fragmentLocations

        if (fragmentLocations.isEmpty()) return registry

        val loader = ScenarioLoader()
        FragmentDiscovery
            .discoverFragments(classDescriptor.testClass.classLoader, fragmentLocations)
            .forEach { fragment ->
                runCatching {
                    fragment.url.openStream().use { input ->
                        val content = input.bufferedReader().readText()
                        val fragments = loader.loadFragmentsFromString(content, fragment.name)
                        registry.registerAll(fragments)
                    }
                }.onFailure {
                    System.err.println("Warning: Failed to load fragment from ${fragment.path}: ${it.message}")
                }
            }

        return registry
    }

    companion object {
        /**
         * Builds a formatted error message for failed steps in a scenario.
         */
        fun buildFailedStepsMessage(
            scenarioName: String,
            result: ScenarioResult,
        ): String {
            val failedSteps =
                result.stepResults
                    .filter { it.status != ResultStatus.PASSED }
                    .mapIndexed { index, step ->
                        val keyword =
                            step.step.type.name
                                .lowercase()
                        val locationInfo =
                            step.step.sourceLocation?.let { " at $it" } ?: ""
                        val header = "  Step ${index + 1} ($keyword): ${step.step.description}$locationInfo"
                        val details =
                            step.assertionResults
                                .filter { !it.passed }
                                .takeIf { it.isNotEmpty() }
                                ?.joinToString("\n") { "      - ${it.message}" }
                                ?: "      - ${step.error?.message ?: "Assertion failed"}"
                        "$header\n$details"
                    }.joinToString("\n")

            return "Scenario '$scenarioName' failed:\n$failedSteps"
        }
    }
}

/**
 * Holds the execution context for a test class.
 */
private data class TestExecutionContext(
    val suite: LemonCheckSuite,
    val bindings: LemonCheckBindings,
    val pluginRegistry: PluginRegistry,
    val fragmentRegistry: FragmentRegistry,
    val runner: ScenarioRunner,
)

/**
 * Holds the execution context for a scenario file.
 */
private data class FileExecutionContext(
    val executor: ScenarioExecutor,
    val sharedContext: ExecutionContext?,
    val scenarioPath: String,
)
