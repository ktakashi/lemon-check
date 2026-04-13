package io.github.ktakashi.lemoncheck.junit.engine

import io.github.ktakashi.lemoncheck.junit.LemonCheckScenarios
import io.github.ktakashi.lemoncheck.junit.discovery.DiscoveredScenario
import io.github.ktakashi.lemoncheck.junit.discovery.ScenarioDiscovery
import io.github.ktakashi.lemoncheck.scenario.FeatureGroup
import io.github.ktakashi.lemoncheck.scenario.ScenarioFileContent
import io.github.ktakashi.lemoncheck.scenario.ScenarioLoader
import org.junit.jupiter.api.Disabled
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import java.io.InputStreamReader
import java.net.URL

/**
 * Responsible for discovering scenario tests from annotated test classes.
 *
 * This class handles:
 * - Finding classes annotated with @LemonCheckScenarios
 * - Loading .scenario files from specified locations
 * - Building the test descriptor hierarchy (Class -> File -> Feature -> Scenario)
 */
object ScenarioTestDiscoverer {
    /**
     * Discovers scenarios for a test class and adds them to the engine descriptor.
     */
    fun discoverScenariosForClass(
        engineDescriptor: EngineDescriptor,
        testClass: Class<*>,
    ) {
        val annotation = testClass.getAnnotation(LemonCheckScenarios::class.java) ?: return

        if (testClass.isAnnotationPresent(Disabled::class.java)) return
        if (alreadyDiscovered(engineDescriptor, testClass)) return
        if (annotation.locations.isEmpty()) return

        val discoveredFiles = discoverScenarioFiles(testClass.classLoader, annotation.locations)
        val classDescriptor = createClassDescriptor(engineDescriptor.uniqueId, testClass, discoveredFiles)

        if (classDescriptor.children.isNotEmpty() || discoveredFiles.isEmpty()) {
            engineDescriptor.addChild(classDescriptor)
        }
    }

    private fun alreadyDiscovered(
        engineDescriptor: EngineDescriptor,
        testClass: Class<*>,
    ): Boolean = engineDescriptor.children.any { it is ClassTestDescriptor && it.testClass == testClass }

    private fun discoverScenarioFiles(
        classLoader: ClassLoader,
        locations: Array<out String>,
    ): List<DiscoveredScenario> =
        ScenarioDiscovery
            .discoverScenarios(classLoader, locations)
            .sortedBy { it.name }

    private fun createClassDescriptor(
        parentId: UniqueId,
        testClass: Class<*>,
        files: List<DiscoveredScenario>,
    ): ClassTestDescriptor {
        val classUniqueId = parentId.append("class", testClass.name)
        val classDescriptor = ClassTestDescriptor(classUniqueId, testClass)
        val scenarioLoader = ScenarioLoader()

        files
            .mapNotNull { file -> createFileDescriptor(classUniqueId, file, scenarioLoader) }
            .forEach { classDescriptor.addChild(it) }

        return classDescriptor
    }

    private fun createFileDescriptor(
        parentId: UniqueId,
        file: DiscoveredScenario,
        loader: ScenarioLoader,
    ): ScenarioFileDescriptor? {
        val fileId = parentId.append("file", file.name.removeSuffix(".scenario"))
        val fileDescriptor =
            ScenarioFileDescriptor(
                uniqueId = fileId,
                displayName = file.name,
                scenarioPath = file.path,
                scenarioSource = file.url,
            )

        return try {
            val fileContent = loadScenarioFromUrl(loader, file.url)
            populateFileDescriptor(fileDescriptor, fileContent)
            fileDescriptor
        } catch (e: Exception) {
            System.err.println("Warning: Failed to parse ${file.path} during discovery: ${e.message}")
            fileDescriptor
        }
    }

    private fun populateFileDescriptor(
        fileDescriptor: ScenarioFileDescriptor,
        content: ScenarioFileContent,
    ) {
        // Add standalone scenarios (expanding outlines)
        content.standaloneScenarios
            .flatMap { scenario -> expandScenarioIfOutline(scenario) }
            .map { scenario -> createScenarioDescriptor(fileDescriptor.uniqueId, scenario) }
            .forEach { fileDescriptor.addChild(it) }

        // Add feature groups
        content.features
            .map { feature -> createFeatureDescriptor(fileDescriptor.uniqueId, feature) }
            .forEach { fileDescriptor.addChild(it) }
    }

    /**
     * Expand a scenario outline into individual scenarios per example row.
     * Non-outline scenarios are returned as-is.
     */
    private fun expandScenarioIfOutline(
        scenario: io.github.ktakashi.lemoncheck.model.Scenario,
    ): List<io.github.ktakashi.lemoncheck.model.Scenario> {
        val examples = scenario.examples
        if (examples.isNullOrEmpty()) {
            return listOf(scenario)
        }

        // Expand into one scenario per example row
        return examples.mapIndexed { index, row ->
            scenario.copy(
                name = "${scenario.name} (Example ${index + 1})",
                examples = listOf(row), // Keep only this row's data
            )
        }
    }

    private fun createScenarioDescriptor(
        parentId: UniqueId,
        scenario: io.github.ktakashi.lemoncheck.model.Scenario,
    ): IndividualScenarioDescriptor {
        val scenarioId = parentId.append("scenario", scenario.name)
        val hasAutoTests = scenario.steps.any { it.autoTestConfig != null }

        return IndividualScenarioDescriptor(
            uniqueId = scenarioId,
            displayName = scenario.name,
            scenario = scenario,
            hasAutoTests = hasAutoTests,
        )
        // Note: No placeholder children are added during discovery
        // Auto-tests are added dynamically during execution
    }

    private fun createFeatureDescriptor(
        parentId: UniqueId,
        feature: FeatureGroup,
    ): FeatureDescriptor {
        val featureId = parentId.append("feature", feature.name)
        val featureDescriptor =
            FeatureDescriptor(
                uniqueId = featureId,
                displayName = feature.name,
                featureName = feature.name,
            )

        feature.scenarios
            .map { scenario -> createScenarioDescriptor(featureId, scenario) }
            .forEach { featureDescriptor.addChild(it) }

        return featureDescriptor
    }

    fun loadScenarioFromUrl(
        loader: ScenarioLoader,
        url: URL,
    ): ScenarioFileContent =
        url.openStream().use { input ->
            val content = InputStreamReader(input).readText()
            val fileName = url.path.substringAfterLast("/")
            loader.loadFileContentFromString(content, fileName)
        }
}
