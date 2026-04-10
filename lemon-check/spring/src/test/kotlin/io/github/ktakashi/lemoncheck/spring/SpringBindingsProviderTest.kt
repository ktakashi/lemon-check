package io.github.ktakashi.lemoncheck.spring

import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.exception.ConfigurationException
import io.github.ktakashi.lemoncheck.junit.LemonCheckBindings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for SpringBindingsProvider.
 */
class SpringBindingsProviderTest {
    private val provider = SpringBindingsProvider()

    @Test
    fun `supports returns true for class with LemonCheckContextConfiguration`() {
        assertTrue(provider.supports(ValidTestClass::class.java))
    }

    @Test
    fun `supports returns false for class without LemonCheckContextConfiguration`() {
        assertFalse(provider.supports(ClassWithoutAnnotation::class.java))
    }

    @Test
    fun `priority returns 100`() {
        assertEquals(100, provider.priority())
    }

    @Test
    fun `initialize throws when SpringBootTest is missing`() {
        val exception =
            assertThrows<ConfigurationException> {
                provider.initialize(MissingSpringBootTestClass::class.java)
            }
        assertTrue(exception.message!!.contains("missing @SpringBootTest"))
    }

    @Test
    fun `full lifecycle test with Spring context`() {
        // Initialize Spring context
        provider.initialize(ValidTestClass::class.java)

        // Create bindings from Spring context
        val bindings =
            provider.createBindings(
                ValidTestClass::class.java,
                TestBindingsComponent::class.java,
            )

        // Verify bindings work
        assertNotNull(bindings)
        val bindingsMap = bindings.getBindings()
        assertNotNull(bindingsMap["baseUrl"])
        assertTrue((bindingsMap["baseUrl"] as String).startsWith("http://localhost:"))

        // Cleanup
        provider.cleanup(ValidTestClass::class.java)
    }

    @Test
    fun `createBindings throws when context not initialized`() {
        val exception =
            assertThrows<ConfigurationException> {
                provider.createBindings(
                    ClassWithoutAnnotation::class.java,
                    TestBindingsComponent::class.java,
                )
            }
        assertTrue(exception.message!!.contains("not initialized"))
    }

    // Test fixtures

    @SpringBootApplication(exclude = [DataSourceAutoConfiguration::class])
    class TestApplication

    @SpringBootTest(
        classes = [TestApplication::class, TestBindingsComponent::class],
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    )
    @LemonCheckContextConfiguration
    class ValidTestClass

    @LemonCheckContextConfiguration
    class MissingSpringBootTestClass

    class ClassWithoutAnnotation

    @Component
    @Lazy
    class TestBindingsComponent : LemonCheckBindings {
        @LocalServerPort
        private var port: Int = 0

        override fun getBindings(): Map<String, Any> = mapOf("baseUrl" to "http://localhost:$port/api")

        override fun configure(config: Configuration) {
            config.baseUrl = "http://localhost:$port/api"
        }
    }
}
