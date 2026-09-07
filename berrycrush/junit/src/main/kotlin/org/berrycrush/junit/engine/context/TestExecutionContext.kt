package org.berrycrush.junit.engine.context

import org.berrycrush.assertion.AssertionRegistry
import org.berrycrush.configuration.Configuration
import org.berrycrush.context.ExecutionContext
import org.berrycrush.executor.BerryCrushConfigurationProvider
import org.berrycrush.junit.BerryCrushBindings
import org.berrycrush.junit.BerryCrushSuite
import org.berrycrush.junit.ParallelExecutionMode
import org.berrycrush.junit.engine.ClassTestDescriptor
import org.berrycrush.junit.engine.ScenarioFileDescriptor
import org.berrycrush.junit.engine.ScenarioMethodDescriptor
import org.berrycrush.junit.spi.BindingsProvider
import org.berrycrush.model.FragmentRegistry
import org.berrycrush.model.ResultStatus
import org.berrycrush.model.ScenarioResult
import org.berrycrush.plugin.PluginRegistry
import org.berrycrush.runner.ScenarioRunner
import org.berrycrush.scenario.ScenarioLoader
import org.berrycrush.util.StepRegistry
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestExecutionResult
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.logging.Logger
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmName

private val logger = Logger.getLogger(TestExecutionContext::class.java.name)

/**
 * Holds the execution context for a test class.
 */
internal data class TestExecutionContext(
    val suite: BerryCrushSuite,
    val bindings: BerryCrushBindings,
    val pluginRegistry: PluginRegistry,
    val fragmentRegistry: FragmentRegistry,
    val stepRegistry: StepRegistry?,
    val assertionRegistry: AssertionRegistry?,
    val runner: ScenarioRunner,
) {
    fun beginExecution(): Unit = runner.beginExecution()

    fun endExecution(): Unit = runner.endExecution()

    /**
     * Execute @Scenario methods.
     */
    fun executeScenarioMethods(
        classDescriptor: ClassTestDescriptor,
        listener: EngineExecutionListener,
        provider: BindingsProvider?,
    ) {
        val scenarioDescriptors = classDescriptor.children.filterIsInstance<ScenarioMethodDescriptor>()
        if (scenarioDescriptors.isEmpty()) {
            return
        }

        val lifecycle =
            ScenarioMethodLifecycleController(
                classDescriptor = classDescriptor,
                provider = provider,
                suite = suite,
            )

        val beforeAllError = runCatching { lifecycle.beforeAll() }.exceptionOrNull()
        if (beforeAllError != null) {
            scenarioDescriptors.forEach { scenarioDescriptor ->
                listener.executionStarted(scenarioDescriptor)
                listener.executionFinished(scenarioDescriptor, TestExecutionResult.failed(beforeAllError))
            }
            return
        }

        try {
            scenarioDescriptors.forEach { scenarioDescriptor ->
                executeScenarioMethod(scenarioDescriptor, classDescriptor, listener, lifecycle)
            }
        } finally {
            runCatching { lifecycle.afterAll() }
                .onFailure { logger.warning { "Failed to execute @AfterAll for ${classDescriptor.testClass.jvmName}: ${it.message}" } }
        }
    }

    fun executeFileDescriptors(
        classDescriptor: ClassTestDescriptor,
        listener: EngineExecutionListener,
    ) {
        classDescriptor.children
            .filterIsInstance<ScenarioFileDescriptor>()
            .forEach { fileDescriptor ->
                executeFileDescriptor(fileDescriptor, classDescriptor, listener)
            }
    }
}

/**
 * Execute a single @Scenario method.
 */
private fun TestExecutionContext.executeScenarioMethod(
    scenarioDescriptor: ScenarioMethodDescriptor,
    classDescriptor: ClassTestDescriptor,
    listener: EngineExecutionListener,
    lifecycle: ScenarioMethodLifecycleController,
) {
    listener.executionStarted(scenarioDescriptor)
    runCatching {
        val testInstance = lifecycle.createTestInstance()

        lifecycle.beforeEach(testInstance)
        try {
            // Invoke the @Scenario method to get the Scenario
            val scenario = scenarioDescriptor.invokeMethod(testInstance, suite)

            // Check if scenario should be skipped based on tags
            if (!classDescriptor.shouldExecuteScenario(scenario.tags)) {
                ScenarioResult(scenario, ResultStatus.SKIPPED)
            } else {
                // Execute the scenario
                runner.executeScenario(scenario)
            }
        } finally {
            lifecycle.afterEach(testInstance)
        }
    }.onSuccess { result ->
        val testResult =
            when (result.status) {
                ResultStatus.PASSED -> TestExecutionResult.successful()
                ResultStatus.SKIPPED -> TestExecutionResult.aborted(null)
                ResultStatus.FAILED -> result.failed("Scenario failed")
                ResultStatus.ERROR -> result.failed("Scenario got error")
                ResultStatus.PENDING -> TestExecutionResult.aborted(null)
            }
        listener.executionFinished(scenarioDescriptor, testResult)
    }.onFailure { e ->
        listener.executionFinished(scenarioDescriptor, TestExecutionResult.failed(e))
    }
}

private fun ScenarioResult.failed(msg: String): TestExecutionResult {
    val error = stepResults.lastOrNull { it.status == status }?.error ?: AssertionError(msg)
    return TestExecutionResult.failed(error)
}

private fun TestExecutionContext.executeFileDescriptor(
    fileDescriptor: ScenarioFileDescriptor,
    classDescriptor: ClassTestDescriptor,
    listener: EngineExecutionListener,
) {
    listener.executionStarted(fileDescriptor)

    runCatching {
        val fileContext = buildFileContext(fileDescriptor, classDescriptor)
        fileContext.executeFileChildren(fileDescriptor, classDescriptor, listener)
    }.onSuccess { hasFailure ->
        val testResult =
            if (hasFailure) {
                TestExecutionResult.failed(AssertionError("One or more scenarios failed"))
            } else {
                TestExecutionResult.successful()
            }
        listener.executionFinished(fileDescriptor, testResult)
    }.onFailure { e ->
        listener.executionFinished(fileDescriptor, TestExecutionResult.failed(e))
    }
}

private fun TestExecutionContext.buildFileContext(
    fileDescriptor: ScenarioFileDescriptor,
    classDescriptor: ClassTestDescriptor,
): FileExecutionContext {
    val fileContent =
        ScenarioLoader.loadFileContent(
            path = fileDescriptor.scenarioSource.toURI(),
        )

    val fileConfig =
        if (fileContent.parameters.isNotEmpty()) {
            suite.configuration.withParameters(fileContent.parameters)
        } else {
            suite.configuration
        }
    val executionContext = ExecutionContext(fileConfig.shareVariablesAcrossScenarios, fileContent.parameters)
    val newRunner = runner.from(BerryCrushConfigurationProvider.from(fileConfig, suite.runtimeParameters))

    // Warn if sharing variables across scenarios with concurrent execution mode
    if (classDescriptor.parallelExecution == ParallelExecutionMode.CONCURRENT) {
        logger.warning {
            "File '${fileDescriptor.scenarioPath}' uses shareVariablesAcrossScenarios=true " +
                "but test class '${classDescriptor.testClass.jvmName}' has CONCURRENT parallel execution. " +
                "Consider using @BerryCrushConfiguration(parallelExecution = SAME_THREAD) " +
                "to ensure scenarios execute sequentially."
        }
    }

    return FileExecutionContext(
        runner = newRunner,
        fileContext = executionContext,
        scenarioPath = fileDescriptor.scenarioPath,
    )
}

private class ScenarioMethodLifecycleController(
    private val classDescriptor: ClassTestDescriptor,
    private val provider: BindingsProvider?,
    private val suite: BerryCrushSuite,
) {
    private val lifecycle =
        classDescriptor.testClass.java
            .getAnnotation(TestInstance::class.java)
            ?.value
            ?: TestInstance.Lifecycle.PER_METHOD

    private val beforeAllMethods =
        findLifecycleMethods(BeforeAll::class.java, topDown = true)

    private val beforeEachMethods =
        findLifecycleMethods(BeforeEach::class.java, topDown = true)

    private val afterEachMethods =
        findLifecycleMethods(AfterEach::class.java, topDown = false)

    private val afterAllMethods =
        findLifecycleMethods(AfterAll::class.java, topDown = false)

    private var beforeAllExecuted = false
    private var perClassInstance: Any? = null

    fun beforeAll() {
        if (beforeAllExecuted) {
            return
        }
        val instance = if (lifecycle == TestInstance.Lifecycle.PER_CLASS) getOrCreatePerClassInstance() else null
        invokeLifecycleMethods(beforeAllMethods, instance)
        beforeAllExecuted = true
    }

    fun afterAll() {
        if (!beforeAllExecuted) {
            return
        }
        val instance = if (lifecycle == TestInstance.Lifecycle.PER_CLASS) perClassInstance else null
        invokeLifecycleMethods(afterAllMethods, instance)
    }

    fun createTestInstance(): Any =
        if (lifecycle == TestInstance.Lifecycle.PER_CLASS) {
            getOrCreatePerClassInstance()
        } else {
            createTestInstanceInternal()
        }

    fun beforeEach(testInstance: Any) {
        invokeLifecycleMethods(beforeEachMethods, testInstance)
    }

    fun afterEach(testInstance: Any) {
        invokeLifecycleMethods(afterEachMethods, testInstance)
    }

    private fun getOrCreatePerClassInstance(): Any =
        perClassInstance ?: createTestInstanceInternal().also {
            perClassInstance = it
        }

    private fun createTestInstanceInternal(): Any =
        provider?.createTestInstance(classDescriptor.testClass.java)
            ?: classDescriptor.testClass.primaryConstructor?.call()
            ?: throw IllegalStateException("Test class ${classDescriptor.testClass.simpleName} not found")

    private fun invokeLifecycleMethods(
        methods: List<Method>,
        testInstance: Any?,
    ) {
        methods.forEach { method ->
            method.isAccessible = true
            val target = if (Modifier.isStatic(method.modifiers)) null else testInstance
            val args = resolveMethodArguments(method)
            method.invoke(target, *args)
        }
    }

    private fun resolveMethodArguments(method: Method): Array<Any> =
        method.parameters
            .map { parameter ->
                when {
                    BerryCrushSuite::class.java.isAssignableFrom(parameter.type) -> suite

                    Configuration::class.java.isAssignableFrom(parameter.type) -> suite.configuration

                    else -> throw IllegalArgumentException(
                        "Unsupported lifecycle parameter type ${parameter.type.name} in ${method.declaringClass.simpleName}.${method.name}",
                    )
                }
            }.toTypedArray()

    private fun findLifecycleMethods(
        annotationClass: Class<out Annotation>,
        topDown: Boolean,
    ): List<Method> {
        val classHierarchy = mutableListOf<Class<*>>()
        var current: Class<*>? = classDescriptor.testClass.java
        while (current != null && current != Any::class.java) {
            classHierarchy += current
            current = current.superclass
        }

        if (topDown) {
            classHierarchy.reverse()
        }

        return classHierarchy
            .flatMap { clazz ->
                clazz.declaredMethods.filter { method ->
                    method.isAnnotationPresent(annotationClass)
                }
            }.distinct()
    }
}
