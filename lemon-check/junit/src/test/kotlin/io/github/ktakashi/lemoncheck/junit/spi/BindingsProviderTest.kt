package io.github.ktakashi.lemoncheck.junit.spi

import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for BindingsProvider SPI behavior.
 */
class BindingsProviderTest {
    @Test
    fun `default priority should return 0`() {
        val provider = TestBindingsProvider()
        assertEquals(0, provider.priority())
    }

    @Test
    fun `provider should indicate support for test class`() {
        val provider = TestBindingsProvider()
        assertTrue(provider.supports(SupportedTestClass::class.java))
        assertFalse(provider.supports(UnsupportedTestClass::class.java))
    }

    @Test
    fun `provider should create bindings for supported class`() {
        val provider = TestBindingsProvider()
        val bindings =
            provider.createBindings(
                SupportedTestClass::class.java,
                TestBindings::class.java,
            )
        assertEquals("test-value", bindings.getBindings()["testKey"])
    }

    @Test
    fun `provider lifecycle methods should be called`() {
        val provider = TestBindingsProvider()

        provider.initialize(SupportedTestClass::class.java)
        assertTrue(provider.initialized)

        provider.cleanup(SupportedTestClass::class.java)
        assertTrue(provider.cleanedUp)
    }

    // Test implementation of BindingsProvider
    private class TestBindingsProvider : BindingsProvider {
        var initialized = false
        var cleanedUp = false

        override fun supports(testClass: Class<*>): Boolean = testClass == SupportedTestClass::class.java

        override fun initialize(testClass: Class<*>) {
            initialized = true
        }

        override fun createBindings(
            testClass: Class<*>,
            bindingsClass: Class<out LemonCheckBindings>,
        ): LemonCheckBindings = TestBindings()

        override fun cleanup(testClass: Class<*>) {
            cleanedUp = true
        }
    }

    // Test bindings implementation
    private class TestBindings : LemonCheckBindings {
        override fun getBindings(): Map<String, Any> = mapOf("testKey" to "test-value")

        override fun configure(config: Configuration) {}
    }

    // Test classes for provider support checks
    private class SupportedTestClass

    private class UnsupportedTestClass
}
