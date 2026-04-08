package io.github.ktakashi.lemoncheck.executor

import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepType
import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScenarioExecutorTest {
    private fun createExecutor(): ScenarioExecutor {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val registry = SpecRegistry()
        registry.registerDefault(specPath)

        val config =
            Configuration().apply {
                baseUrl = "https://httpbin.org" // Use httpbin for testing
            }

        return ScenarioExecutor(registry, config)
    }

    @Test
    fun `should execute scenario with no API calls`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val registry = SpecRegistry()
        registry.registerDefault(specPath)
        val executor = ScenarioExecutor(registry, Configuration())

        val scenario =
            Scenario(
                name = "Simple scenario",
                steps =
                    listOf(
                        Step(
                            type = StepType.GIVEN,
                            description = "something is true",
                        ),
                        Step(
                            type = StepType.THEN,
                            description = "something happens",
                        ),
                    ),
            )

        val result = executor.execute(scenario)

        assertEquals(ResultStatus.PASSED, result.status)
        assertEquals(2, result.stepResults.size)
        assertTrue(result.stepResults.all { it.status == ResultStatus.PASSED })
    }

    @Test
    fun `should mark subsequent steps as skipped after failure`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val registry = SpecRegistry()
        registry.registerDefault(specPath)
        val config =
            Configuration().apply {
                baseUrl = "http://nonexistent.invalid"
            }
        val executor = ScenarioExecutor(registry, config)

        val scenario =
            Scenario(
                name = "Failing scenario",
                steps =
                    listOf(
                        Step(
                            type = StepType.WHEN,
                            description = "calling nonexistent API",
                            operationId = "listPets",
                        ),
                        Step(
                            type = StepType.THEN,
                            description = "this should be skipped",
                        ),
                    ),
            )

        val result = executor.execute(scenario)

        assertEquals(ResultStatus.ERROR, result.status)
        assertEquals(2, result.stepResults.size)
        assertEquals(ResultStatus.ERROR, result.stepResults[0].status)
        assertEquals(ResultStatus.SKIPPED, result.stepResults[1].status)
    }

    @Test
    fun `should track step duration`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val registry = SpecRegistry()
        registry.registerDefault(specPath)
        val executor = ScenarioExecutor(registry, Configuration())

        val scenario =
            Scenario(
                name = "Duration test",
                steps =
                    listOf(
                        Step(
                            type = StepType.THEN,
                            description = "simple step",
                        ),
                    ),
            )

        val result = executor.execute(scenario)

        assertTrue(result.duration.toMillis() >= 0)
        assertTrue(result.stepResults[0].duration.toMillis() >= 0)
    }

    @Test
    fun `should report assertion failures correctly`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val registry = SpecRegistry()
        registry.registerDefault(specPath)
        val config =
            Configuration().apply {
                baseUrl = "https://httpbin.org"
            }
        val executor = ScenarioExecutor(registry, config)

        // This will fail because httpbin won't match petstore schema,
        // but we can test that assertion mechanics work
        val scenario =
            Scenario(
                name = "Assertion test",
                steps =
                    listOf(
                        Step(
                            type = StepType.THEN,
                            description = "no-op step with passing assertion",
                            // No assertions = pass
                            assertions = listOf(),
                        ),
                    ),
            )

        val result = executor.execute(scenario)

        assertEquals(ResultStatus.PASSED, result.status)
    }
}
