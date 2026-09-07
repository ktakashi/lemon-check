package org.berrycrush.junit.engine

import org.berrycrush.junit.ScenarioTest
import org.berrycrush.model.Scenario
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestDescriptorsTest {
    @Test
    fun `scenario file descriptor should be a container`() {
        val resource = requireNotNull(javaClass.getResource("/scenarios/simple.scenario"))
        val descriptor =
            ScenarioFileDescriptor(
                uniqueId = UniqueId.forEngine("berrycrush").append("file", "simple"),
                displayName = "simple.scenario",
                scenarioPath = "scenarios/simple.scenario",
                scenarioSource = resource,
            )

        assertEquals(TestDescriptor.Type.CONTAINER, descriptor.type)
        assertEquals("scenarios/simple.scenario", descriptor.scenarioPath)
    }

    @Test
    fun `individual scenario descriptor should expose correct test types`() {
        val scenario = Scenario(name = "descriptor scenario")

        val regular =
            IndividualScenarioDescriptor(
                uniqueId = UniqueId.forEngine("berrycrush").append("scenario", "regular"),
                displayName = "regular",
                scenario = scenario,
            )
        val withAutoTests =
            IndividualScenarioDescriptor(
                uniqueId = UniqueId.forEngine("berrycrush").append("scenario", "auto"),
                displayName = "auto",
                scenario = scenario,
            )

        assertEquals(TestDescriptor.Type.TEST, regular.type)
        assertEquals(TestDescriptor.Type.TEST, withAutoTests.type)
    }

    @Test
    fun `scenario method discoverer should skip disabled classes`() {
        val engineDescriptor = object : EngineDescriptor(UniqueId.forEngine("berrycrush"), "engine") {}

        ScenarioMethodDiscoverer.discoverScenariosForClass(engineDescriptor, DisabledScenarioClass::class)

        assertTrue(engineDescriptor.children.isEmpty())
    }

    @Test
    fun `scenario method discoverer should reuse existing class descriptor`() {
        val engineDescriptor = object : EngineDescriptor(UniqueId.forEngine("berrycrush"), "engine") {}
        val classDescriptor =
            ClassTestDescriptor(
                uniqueId = engineDescriptor.uniqueId.append("class", DiscovererScenarioClass::class.java.name),
                testClass = DiscovererScenarioClass::class,
            )
        engineDescriptor.addChild(classDescriptor)

        ScenarioMethodDiscoverer.discoverScenariosForClass(engineDescriptor, DiscovererScenarioClass::class)

        assertEquals(1, engineDescriptor.children.filterIsInstance<ClassTestDescriptor>().size)
        assertTrue(classDescriptor.children.filterIsInstance<ScenarioMethodDescriptor>().isNotEmpty())
    }
}

@Disabled("for discoverer branch coverage")
private class DisabledScenarioClass {
    @ScenarioTest
    fun shouldNotBeDiscovered(): Scenario = Scenario(name = "disabled")
}

private class DiscovererScenarioClass {
    @ScenarioTest
    fun validScenario(): Scenario = Scenario(name = "discoverer")
}
