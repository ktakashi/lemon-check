package io.github.ktakashi.lemoncheck.spring

import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertTrue

/**
 * Tests for SpringBindingsProvider error handling.
 */
class SpringBindingsProviderErrorTest {
    private val provider = SpringBindingsProvider()

    @Test
    fun `initialize throws descriptive error when SpringBootTest is missing`() {
        val exception =
            assertThrows<IllegalStateException> {
                provider.initialize(MissingSpringBootTestClass::class.java)
            }

        // Verify error message contains helpful information
        assertTrue(
            exception.message!!.contains("@LemonCheckContextConfiguration"),
            "Error should mention @LemonCheckContextConfiguration",
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
    fun `createBindings throws error when bean not found in Spring context`() {
        // Initialize with valid test class
        provider.initialize(ValidTestClassWithoutBindings::class.java)

        try {
            val exception =
                assertThrows<IllegalStateException> {
                    provider.createBindings(
                        ValidTestClassWithoutBindings::class.java,
                        NonExistentBindings::class.java,
                    )
                }

            // Verify error message contains helpful information
            assertTrue(
                exception.message!!.contains("NonExistentBindings"),
                "Error should mention the bindings class name",
            )
            assertTrue(
                exception.message!!.contains("Spring bean") ||
                    exception.message!!.contains("not registered"),
                "Error should explain the bean is not registered",
            )
        } finally {
            provider.cleanup(ValidTestClassWithoutBindings::class.java)
        }
    }

    @Test
    fun `createBindings throws error when context not initialized`() {
        // Try to create bindings without initializing first
        val exception =
            assertThrows<IllegalStateException> {
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
    open class TestApplication

    @LemonCheckContextConfiguration
    class MissingSpringBootTestClass

    @SpringBootTest(
        classes = [TestApplication::class],
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    )
    @LemonCheckContextConfiguration
    class ValidTestClassWithoutBindings

    // This bindings class is NOT registered as a Spring component
    class NonExistentBindings : LemonCheckBindings {
        override fun getBindings(): Map<String, Any> = emptyMap()

        override fun configure(config: Configuration) {}
    }
}
