package org.berrycrush.step

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [AnnotationStepScanner].
 */
class AnnotationStepScannerTest {
    private val scanner = AnnotationStepScanner()

    @Test
    fun `scan class with annotated methods`() {
        val definitions = scanner.scan(SampleSteps::class.java)

        assertEquals(3, definitions.size)
    }

    @Test
    fun `extract pattern from annotation`() {
        val definitions = scanner.scan(SampleSteps::class.java)

        val patterns = definitions.map { it.pattern }
        assertTrue(patterns.contains("I have {int} items"))
        assertTrue(patterns.contains("the name is {string}"))
        assertTrue(patterns.contains("I add {int} and {int}"))
    }

    @Test
    fun `extract description from annotation`() {
        val definitions = scanner.scan(SampleSteps::class.java)

        val withDescription = definitions.find { it.pattern == "I have {int} items" }
        assertNotNull(withDescription)
        assertEquals("Sets the item count", withDescription.description)
    }

    @Test
    fun `scan with provided instance`() {
        val instance = SampleSteps()
        val definitions = scanner.scan(SampleSteps::class.java, instance)

        // All non-static methods should use the same instance
        for ((_, _, instance1) in definitions) {
            assertEquals(instance, instance1)
        }
    }

    @Test
    fun `scan multiple classes`() {
        val definitions = scanner.scanAll(SampleSteps::class.java, OtherSteps::class.java)

        assertEquals(4, definitions.size)
    }

    @Test
    fun `scan instances`() {
        val sample = SampleSteps()
        val other = OtherSteps()
        val definitions = scanner.scanInstances(sample, other)

        assertEquals(4, definitions.size)

        // Verify correct instances are used
        val sampleDefs = definitions.filter { it.instance == sample }
        val otherDefs = definitions.filter { it.instance == other }

        assertEquals(3, sampleDefs.size)
        assertEquals(1, otherDefs.size)
    }

    @Test
    fun `scan class without annotations returns empty`() {
        val definitions = scanner.scan(NoAnnotations::class.java)

        assertTrue(definitions.isEmpty())
    }

    // Test helper classes
    class SampleSteps {
        @Step("I have {int} items", description = "Sets the item count")
        fun setCount(count: Int) {
            // Implementation
        }

        @Step("the name is {string}")
        fun setName(name: String) {
            // Implementation
        }

        @Step("I add {int} and {int}")
        fun add(
            a: Int,
            b: Int,
        ): Int = a + b
    }

    class OtherSteps {
        @Step("the status is {word}")
        fun setStatus(status: String) {
            // Implementation
        }
    }

    class NoAnnotations {
        fun regularMethod() {
            // Not a step
        }
    }
}
