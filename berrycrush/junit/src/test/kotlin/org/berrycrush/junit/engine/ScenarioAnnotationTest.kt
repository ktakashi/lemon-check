package org.berrycrush.junit.engine

import org.berrycrush.dsl.BerryCrushSuite
import org.berrycrush.junit.BerryCrushSpec
import org.berrycrush.junit.ScenarioTest
import org.berrycrush.model.Scenario
import org.junit.jupiter.api.Test
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for @ScenarioTest annotation discovery and descriptor creation.
 */
class ScenarioAnnotationTest {
    @Test
    fun `ScenarioMethodDiscoverer finds methods annotated with @ScenarioTest`() {
        val engineId = UniqueId.forEngine("berrycrush")
        val engineDescriptor = object : EngineDescriptor(engineId, "test") {}

        ScenarioMethodDiscoverer.discoverScenariosForClass(
            engineDescriptor,
            TestClassWithScenarios::class.java,
        )

        // Should have one class descriptor
        assertEquals(1, engineDescriptor.children.size, "Should have one class descriptor")

        val classDescriptor = engineDescriptor.children.first() as ClassTestDescriptor
        assertEquals(TestClassWithScenarios::class.java, classDescriptor.testClass)

        // Should have two @ScenarioTest method descriptors
        val scenarioDescriptors = classDescriptor.children.filterIsInstance<ScenarioMethodDescriptor>()
        assertEquals(2, scenarioDescriptors.size, "Should have two @ScenarioTest methods")

        // Check names
        val names = scenarioDescriptors.map { it.displayName }.toSet()
        assertTrue("Create Pet" in names, "Should find createPet method with display name 'Create Pet'")
        assertTrue("List all pets" in names, "Should find 'list all pets' method with display name 'List all pets'")
    }

    @Test
    fun `ScenarioMethodDescriptor invokes method and returns Scenario`() {
        val engineId = UniqueId.forEngine("berrycrush")
        val engineDescriptor = object : EngineDescriptor(engineId, "test") {}

        ScenarioMethodDiscoverer.discoverScenariosForClass(
            engineDescriptor,
            TestClassWithScenarios::class.java,
        )

        val classDescriptor = engineDescriptor.children.first() as ClassTestDescriptor
        val scenarioDescriptor =
            classDescriptor.children
                .filterIsInstance<ScenarioMethodDescriptor>()
                .first { it.displayName == "Create Pet" }

        val testInstance = TestClassWithScenarios()
        val suite = BerryCrushSuite.create()

        val scenario = scenarioDescriptor.invokeMethod(testInstance, suite)

        assertNotNull(scenario, "Should return a Scenario")
        assertEquals("Create a new pet", scenario.name)
    }

    @Test
    fun `formatDisplayName converts camelCase to spaced words`() {
        val engineId = UniqueId.forEngine("berrycrush")
        val engineDescriptor = object : EngineDescriptor(engineId, "test") {}

        ScenarioMethodDiscoverer.discoverScenariosForClass(
            engineDescriptor,
            TestClassWithScenarios::class.java,
        )

        val classDescriptor = engineDescriptor.children.first() as ClassTestDescriptor
        val scenarioDescriptor =
            classDescriptor.children
                .filterIsInstance<ScenarioMethodDescriptor>()
                .first { it.method.name == "createPet" }

        // camelCase should be converted to "Create Pet"
        assertEquals("Create Pet", scenarioDescriptor.displayName)
    }

    @Test
    fun `formatDisplayName handles method names with spaces`() {
        val engineId = UniqueId.forEngine("berrycrush")
        val engineDescriptor = object : EngineDescriptor(engineId, "test") {}

        ScenarioMethodDiscoverer.discoverScenariosForClass(
            engineDescriptor,
            TestClassWithScenarios::class.java,
        )

        val classDescriptor = engineDescriptor.children.first() as ClassTestDescriptor
        val scenarioDescriptor =
            classDescriptor.children
                .filterIsInstance<ScenarioMethodDescriptor>()
                .find { it.displayName == "List all pets" }

        assertNotNull(scenarioDescriptor, "Should find method with spaces in name")
    }
}

/**
 * Test class with @ScenarioTest methods for testing discovery.
 */
@BerryCrushSpec
class TestClassWithScenarios {
    @ScenarioTest
    fun createPet(): Scenario =
        BerryCrushSuite.create().scenario("Create a new pet") {
            whenever("I create a pet") {}
            afterwards("I should see the pet") {}
        }

    @ScenarioTest
    fun `list all pets`(): Scenario =
        BerryCrushSuite.create().scenario("List all pets") {
            whenever("I list pets") {}
            afterwards("I should see all pets") {}
        }

    // This method should NOT be discovered (no @ScenarioTest annotation)
    fun notAScenario(): Scenario = BerryCrushSuite.create().scenario("Should not be discovered") {}

    // This method should NOT be discovered (wrong return type)
    @ScenarioTest
    fun wrongReturnType(): String = "not a scenario"
}
