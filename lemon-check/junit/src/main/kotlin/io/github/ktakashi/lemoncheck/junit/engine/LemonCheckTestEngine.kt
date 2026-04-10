package io.github.ktakashi.lemoncheck.junit.engine

import io.github.ktakashi.lemoncheck.junit.LemonCheckScenarios
import io.github.ktakashi.lemoncheck.junit.spi.BindingsProvider
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.ClassSelector
import org.junit.platform.engine.discovery.PackageSelector
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
 *
 * The engine delegates to:
 * - [ScenarioTestDiscoverer] for discovering test classes and scenario files
 * - [ScenarioTestExecutor] for executing scenarios and reporting results
 */
class LemonCheckTestEngine : TestEngine {
    companion object {
        const val ENGINE_ID = "lemoncheck"
    }

    private val bindingsProviders: List<BindingsProvider> by lazy {
        ServiceLoader
            .load(BindingsProvider::class.java)
            .toList()
            .sortedByDescending { it.priority() }
    }

    private val executor: ScenarioTestExecutor by lazy {
        ScenarioTestExecutor(bindingsProviders)
    }

    override fun getId(): String = ENGINE_ID

    override fun discover(
        discoveryRequest: EngineDiscoveryRequest,
        uniqueId: UniqueId,
    ): TestDescriptor {
        val engineDescriptor = LemonCheckEngineDescriptor(uniqueId)

        // Collect test classes from selectors
        val testClasses =
            collectFromClassSelectors(discoveryRequest) +
                collectFromPackageSelectors(discoveryRequest)

        // Discover scenarios for each unique test class
        testClasses
            .distinct()
            .filter { it.isAnnotationPresent(LemonCheckScenarios::class.java) }
            .forEach { ScenarioTestDiscoverer.discoverScenariosForClass(engineDescriptor, it) }

        return engineDescriptor
    }

    override fun execute(request: ExecutionRequest) {
        val engineDescriptor = request.rootTestDescriptor
        val listener = request.engineExecutionListener

        listener.executionStarted(engineDescriptor)

        engineDescriptor.children
            .filterIsInstance<ClassTestDescriptor>()
            .forEach { classDescriptor ->
                executeClassDescriptor(classDescriptor, listener)
            }

        listener.executionFinished(engineDescriptor, TestExecutionResult.successful())
    }

    private fun collectFromClassSelectors(request: EngineDiscoveryRequest): List<Class<*>> =
        request
            .getSelectorsByType(ClassSelector::class.java)
            .flatMap { selector ->
                listOfNotNull(
                    selector.javaClass.takeIf { it.isAnnotationPresent(LemonCheckScenarios::class.java) },
                    runCatching { Class.forName(selector.javaClass.name) }.getOrNull(),
                )
            }

    private fun collectFromPackageSelectors(request: EngineDiscoveryRequest): List<Class<*>> =
        // Package scanning requires classpath scanning which is complex
        // For now, rely on explicit class selectors
        request
            .getSelectorsByType(PackageSelector::class.java)
            .flatMap { emptyList() }

    private fun executeClassDescriptor(
        classDescriptor: ClassTestDescriptor,
        listener: org.junit.platform.engine.EngineExecutionListener,
    ) {
        listener.executionStarted(classDescriptor)

        val result =
            runCatching {
                executor.executeClassTests(classDescriptor, listener)
            }

        result.fold(
            onSuccess = {
                listener.executionFinished(classDescriptor, TestExecutionResult.successful())
            },
            onFailure = { e ->
                listener.executionFinished(classDescriptor, TestExecutionResult.failed(e))
            },
        )
    }
}
