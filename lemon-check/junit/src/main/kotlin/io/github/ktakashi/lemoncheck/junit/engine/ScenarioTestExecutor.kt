package io.github.ktakashi.lemoncheck.junit.engine

import io.github.ktakashi.lemoncheck.autotest.AutoTestCase
import io.github.ktakashi.lemoncheck.context.ExecutionContext
import io.github.ktakashi.lemoncheck.dsl.LemonCheckSuite
import io.github.ktakashi.lemoncheck.executor.ExecutionListener
import io.github.ktakashi.lemoncheck.executor.ScenarioExecutor
import io.github.ktakashi.lemoncheck.junit.DefaultBindings
import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings
import io.github.ktakashi.lemoncheck.junit.LemonCheckConfiguration
import io.github.ktakashi.lemoncheck.junit.discovery.FragmentDiscovery
import io.github.ktakashi.lemoncheck.junit.spi.BindingsProvider
import io.github.ktakashi.lemoncheck.model.AutoTestResult
import io.github.ktakashi.lemoncheck.model.FragmentRegistry
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.ScenarioResult
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepResult
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

        // Create execution listener adapter for real-time event reporting
        // This handles scenario start/end as well as auto-test events
        val executionListener = JUnitExecutionListenerAdapter(scenarioDescriptor, listener)

        return runCatching {
            val sourceFile = File(fileContext.scenarioPath)

            // Create execution context - use shared context if available,
            // or create one for outline scenarios with examples
            val executionContext =
                fileContext.sharedContext
                    ?: if (scenarioDescriptor.scenario.examples?.isNotEmpty() == true) {
                        ExecutionContext()
                    } else {
                        null
                    }

            // Add example row values to context if this is an outline scenario
            initializeContext(scenarioDescriptor.scenario, executionContext)

            // Execute with execution listener for real-time event reporting
            // All JUnit events (scenario start/end, auto-test start/end) are handled by the listener
            fileContext.executor.execute(
                scenarioDescriptor.scenario,
                executionContext,
                sourceFile,
                executionListener,
            )
        }.fold(
            onSuccess = { executionListener.hasFailure() },
            onFailure = { e ->
                // Ensure scenario is started before reporting failure
                if (!executionListener.scenarioStarted) {
                    listener.executionStarted(scenarioDescriptor)
                }
                listener.executionFinished(scenarioDescriptor, TestExecutionResult.failed(e))
                true
            },
        )
    }

    /**
     * Initialize execution context with example row values for scenario outlines.
     * For non-outline scenarios, this is a no-op.
     */
    private fun initializeContext(
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

    private fun buildAutoTestFailureMessage(autoResult: AutoTestResult): String =
        buildString {
            append(AutoTestDescriptor.createDisplayName(autoResult.testCase))
            append("\n")
            if (autoResult.error != null) {
                append("  Error: ${autoResult.error}")
            } else {
                append("  Status: ${autoResult.statusCode ?: "N/A"}")
                autoResult.assertionResults.filter { !it.passed }.forEach { assertion ->
                    append("\n  - ${assertion.message}")
                }
            }
            if (autoResult.responseBody != null) {
                append("\n  Response: ${autoResult.responseBody}")
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

    /**
     * Adapter that bridges [ExecutionListener] to JUnit's [EngineExecutionListener].
     *
     * This adapter centralizes all JUnit event reporting by implementing the
     * [ExecutionListener] interface and firing corresponding JUnit events:
     * - [onScenarioStarting] → `executionStarted(scenarioDescriptor)`
     * - [onScenarioCompleted] → `executionFinished(scenarioDescriptor, result)`
     * - [onAutoTestStarting] → `dynamicTestRegistered`, `executionStarted`
     * - [onAutoTestCompleted] → `executionFinished`
     *
     * This allows IDEs like IntelliJ to show test output in real-time.
     */
    private inner class JUnitExecutionListenerAdapter(
        private val scenarioDescriptor: IndividualScenarioDescriptor,
        private val listener: EngineExecutionListener,
    ) : ExecutionListener {
        private var testIndex = 0
        private val descriptors = mutableMapOf<AutoTestCase, AutoTestDescriptor>()
        private var autoTestFailureCount = 0
        private var lastScenarioResult: ScenarioResult? = null

        /**
         * Returns true if the scenario execution started.
         */
        var scenarioStarted = false
            private set

        /**
         * Returns true if there were any failures (auto-test or scenario).
         */
        fun hasFailure(): Boolean = autoTestFailureCount > 0 || lastScenarioResult?.status != ResultStatus.PASSED

        override fun onScenarioStarting(scenario: Scenario) {
            scenarioStarted = true
            listener.executionStarted(scenarioDescriptor)
        }

        override fun onScenarioCompleted(
            scenario: Scenario,
            result: ScenarioResult,
        ) {
            lastScenarioResult = result

            // Determine the appropriate result to report
            val autoTestResults = result.stepResults.flatMap { it.autoTestResults }

            val testResult =
                when {
                    // Auto-test failures
                    autoTestFailureCount > 0 -> {
                        TestExecutionResult.failed(
                            AssertionError("$autoTestFailureCount/${autoTestResults.size} auto-tests failed"),
                        )
                    }
                    // Scenario passed
                    result.status == ResultStatus.PASSED -> {
                        TestExecutionResult.successful()
                    }
                    // Scenario skipped
                    result.status == ResultStatus.SKIPPED -> {
                        TestExecutionResult.aborted(null)
                    }
                    // Scenario failed
                    else -> {
                        val message = buildFailedStepsMessage(scenario.name, result)
                        TestExecutionResult.failed(AssertionError(message))
                    }
                }

            listener.executionFinished(scenarioDescriptor, testResult)
        }

        override fun onStepStarting(step: Step) {
            // Step-level reporting not currently used in JUnit
            // Future: Could create step descriptors for fine-grained progress
        }

        override fun onStepCompleted(
            step: Step,
            result: StepResult,
        ) {
            // Step-level reporting not currently used in JUnit
        }

        override fun onAutoTestStarting(testCase: AutoTestCase) {
            val displayName = AutoTestDescriptor.createDisplayName(testCase)
            val testId = scenarioDescriptor.uniqueId.append("auto-test", "${++testIndex}")

            val descriptor =
                AutoTestDescriptor(
                    uniqueId = testId,
                    displayName = displayName,
                    testCase = testCase,
                    stepDescription = testCase.description,
                )

            // Register the dynamic test with JUnit
            scenarioDescriptor.addChild(descriptor)
            listener.dynamicTestRegistered(descriptor)

            // Start execution immediately for real-time output
            listener.executionStarted(descriptor)

            descriptors[testCase] = descriptor
        }

        override fun onAutoTestCompleted(
            testCase: AutoTestCase,
            result: AutoTestResult,
        ) {
            val descriptor =
                descriptors[testCase]
                    ?: throw IllegalStateException("onAutoTestCompleted called without matching onAutoTestStarting")

            if (result.passed) {
                listener.executionFinished(descriptor, TestExecutionResult.successful())
            } else {
                autoTestFailureCount++
                val errorMessage = buildAutoTestFailureMessage(result)
                listener.executionFinished(
                    descriptor,
                    TestExecutionResult.failed(AssertionError(errorMessage)),
                )
            }
        }
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
