package io.github.ktakashi.lemoncheck.junit.engine

import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.UriSource
import java.net.URL

/**
 * Test descriptor representing a single .scenario file to execute.
 *
 * Each scenario file discovered from the locations specified in
 * @LemonCheckScenarios becomes a ScenarioTestDescriptor.
 */
class ScenarioTestDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    val scenarioPath: String,
    val scenarioSource: URL,
) : AbstractTestDescriptor(uniqueId, displayName, UriSource.from(scenarioSource.toURI())) {
    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST
}
