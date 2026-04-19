package org.berrycrush.junit

import org.berrycrush.dsl.BerryCrushSuite
import org.berrycrush.executor.BerryCrushScenarioExecutor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(BerryCrushExtension::class)
@BerryCrushSpec
class BerryCrushExtensionTest {
    @Test
    fun `extension should inject BerryCrushSuite`(suite: BerryCrushSuite) {
        assertNotNull(suite)
    }

    @Test
    fun `extension should inject ScenarioExecutor`(executor: BerryCrushScenarioExecutor) {
        assertNotNull(executor)
    }

    @Test
    fun `annotation should be present`() {
        val annotation = BerryCrushExtensionTest::class.java.getAnnotation(BerryCrushSpec::class.java)
        assertNotNull(annotation)
    }

    @Test
    fun `annotations should contain expected properties`() {
        val specAnnotation = TestClassWithSpec::class.java.getAnnotation(BerryCrushSpec::class.java)
        assertNotNull(specAnnotation)
        assertTrue(specAnnotation.paths.isNotEmpty())
    }
}

@BerryCrushSpec("test-spec.yaml", baseUrl = "http://localhost:8080")
class TestClassWithSpec
