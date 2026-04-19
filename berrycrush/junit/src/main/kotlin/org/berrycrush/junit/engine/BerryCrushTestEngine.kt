package org.berrycrush.junit.engine

import org.berrycrush.junit.BerryCrushScenarios
import org.berrycrush.junit.BerryCrushSpec
import org.berrycrush.junit.ScenarioTest
import org.berrycrush.junit.spi.BindingsProvider
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
 * JUnit 5 TestEngine implementation for BerryCrush scenarios.
 *
 * This engine discovers and executes .scenario files based on the
 * @BerryCrushScenarios annotation. It integrates with the JUnit Platform
 *
 * Usage:
 * ```
 * @IncludeEngines("berrycrush")
 * @BerryCrushScenarios(locations = "scenarios/test.scenario")
 * public class MyApiTest {
 * }
 * ```
 *
 * The engine delegates to:
 * - [ScenarioTestDiscoverer] for discovering test classes and scenario files
 * - [ScenarioTestExecutor] for executing scenarios and reporting results
 */
class BerryCrushTestEngine : TestEngine {
    companion object {
        const val ENGINE_ID = "berrycrush"
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
        val engineDescriptor = BerryCrushEngineDescriptor(uniqueId)

        // Collect test classes from selectors
        val testClasses =
            collectFromClassSelectors(discoveryRequest) +
                collectFromPackageSelectors(discoveryRequest)

        // Discover scenarios for each unique test class with @BerryCrushScenarios
        testClasses
            .distinct()
            .filter { it.isAnnotationPresent(BerryCrushScenarios::class.java) }
            .forEach { ScenarioTestDiscoverer.discoverScenariosForClass(engineDescriptor, it) }

        // Discover @ScenarioTest methods for classes with @BerryCrushSpec
        testClasses
            .distinct()
            .filter { it.isAnnotationPresent(BerryCrushSpec::class.java) }
            .filter { hasScenarioMethods(it) }
            .forEach { ScenarioMethodDiscoverer.discoverScenariosForClass(engineDescriptor, it) }

        return engineDescriptor
    }

    /**
     * Check if a class has any methods annotated with @ScenarioTest.
     */
    private fun hasScenarioMethods(testClass: Class<*>): Boolean =
        testClass.declaredMethods.any { it.isAnnotationPresent(ScenarioTest::class.java) }

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
                    selector.javaClass.takeIf { it.isAnnotationPresent(BerryCrushScenarios::class.java) },
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
