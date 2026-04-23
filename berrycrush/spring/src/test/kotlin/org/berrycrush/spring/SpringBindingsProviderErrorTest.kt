package org.berrycrush.spring

import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.exception.ConfigurationException
import org.berrycrush.junit.BerryCrushBindings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for SpringBindingsProvider error handling.
 */
class SpringBindingsProviderErrorTest {
    private val provider = SpringBindingsProvider()

    @Test
    fun `initialize throws descriptive error when SpringBootTest is missing`() {
        val exception =
            assertThrows<ConfigurationException> {
                provider.initialize(MissingSpringBootTestClass::class.java)
            }

        // Verify error message contains helpful information
        assertTrue(
            exception.message!!.contains("@BerryCrushContextConfiguration"),
            "Error should mention @BerryCrushContextConfiguration",
        )
        assertTrue(
            exception.message!!.contains("@SpringBootTest"),
            "Error should mention @SpringBootTest",
        )
        assertTrue(
            exception.message!!.contains("MissingSpringBootTestClass"),
            "Error should mention the class name",
        )
    }

    @Test
    fun `createBindings falls back to direct instantiation when bean not found in Spring context`() {
        // Initialize with valid test class
        provider.initialize(ValidTestClassWithoutBindings::class.java)

        try {
            // When bean is not found in Spring context, fall back to direct instantiation
            val bindings =
                provider.createBindings(
                    ValidTestClassWithoutBindings::class.java,
                    NonExistentBindings::class.java,
                )

            // Verify bindings were created via direct instantiation
            assertNotNull(bindings, "Bindings should be created via fallback")
            assertTrue(bindings is NonExistentBindings, "Should be instance of NonExistentBindings")
        } finally {
            provider.cleanup(ValidTestClassWithoutBindings::class.java)
        }
    }

    @Test
    fun `createBindings throws error when context not initialized`() {
        // Try to create bindings without initializing first
        val exception =
            assertThrows<ConfigurationException> {
                provider.createBindings(
                    MissingSpringBootTestClass::class.java,
                    NonExistentBindings::class.java,
                )
            }

        assertTrue(
            exception.message!!.contains("not initialized"),
            "Error should indicate context is not initialized",
        )
    }

    // Test fixtures

    @SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
    class TestApplication

    @BerryCrushContextConfiguration
    class MissingSpringBootTestClass

    @SpringBootTest(
        classes = [TestApplication::class],
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    )
    @BerryCrushContextConfiguration
    class ValidTestClassWithoutBindings

    // This bindings class is NOT registered as a Spring component
    class NonExistentBindings : BerryCrushBindings {
        override fun getBindings(): Map<String, Any> = emptyMap()

        override fun configure(config: BerryCrushConfiguration) {}
    }
}
