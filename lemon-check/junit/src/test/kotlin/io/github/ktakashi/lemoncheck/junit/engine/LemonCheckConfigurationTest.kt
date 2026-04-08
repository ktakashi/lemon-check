package io.github.ktakashi.lemoncheck.junit.engine

import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings
import io.github.ktakashi.lemoncheck.junit.LemonCheckConfiguration
import org.junit.jupiter.api.Test
import org.junit.platform.engine.UniqueId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for @LemonCheckConfiguration annotation handling.
 */
class LemonCheckConfigurationTest {
    @Test
    fun `ClassTestDescriptor reads configuration annotation`() {
        val uniqueId = UniqueId.forEngine("lemoncheck").append("class", ConfiguredTestStub::class.java.name)
        val descriptor = ClassTestDescriptor(uniqueId, ConfiguredTestStub::class.java)

        assertNotNull(descriptor.bindingsClass)
        assertEquals(TestBindings::class.java, descriptor.bindingsClass)
        assertEquals("test-api.yaml", descriptor.openApiSpec)
        assertEquals(60_000L, descriptor.timeout)
    }

    @Test
    fun `ClassTestDescriptor uses defaults without configuration`() {
        val uniqueId = UniqueId.forEngine("lemoncheck").append("class", UnconfiguredTestStub::class.java.name)
        val descriptor = ClassTestDescriptor(uniqueId, UnconfiguredTestStub::class.java)

        assertEquals(null, descriptor.bindingsClass)
        assertEquals(null, descriptor.openApiSpec)
        assertEquals(30_000L, descriptor.timeout)
    }

    @Test
    fun `TestBindings provides custom values`() {
        val bindings = TestBindings()
        val values = bindings.getBindings()

        assertEquals("http://localhost:8080", values["baseUrl"])
        assertEquals("test-token", values["authToken"])
    }
}

/**
 * Test bindings implementation for testing.
 */
class TestBindings : LemonCheckBindings {
    override fun getBindings(): Map<String, Any> =
        mapOf(
            "baseUrl" to "http://localhost:8080",
            "authToken" to "test-token",
        )

    override fun getOpenApiSpec(): String = "custom-spec.yaml"

    override fun configure(config: Configuration) {
        config.baseUrl = "http://localhost:8080"
    }
}

/**
 * Sample configured test class.
 * Used only for testing configuration parsing, not for actual test execution.
 */
@LemonCheckConfiguration(
    bindings = TestBindings::class,
    openApiSpec = "test-api.yaml",
    timeout = 60_000L,
)
class ConfiguredTestStub

/**
 * Sample unconfigured test class.
 * Used only for testing default values, not for actual test execution.
 */
class UnconfiguredTestStub
