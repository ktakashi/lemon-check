package org.berrycrush.executor

import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.exception.ConfigurationException
import org.berrycrush.model.Condition
import org.berrycrush.model.Fragment
import org.berrycrush.model.FragmentRegistry
import org.berrycrush.model.ResultStatus
import org.berrycrush.model.Scenario
import org.berrycrush.model.Step
import org.berrycrush.model.StepType
import org.berrycrush.openapi.SpecRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BerryCrushScenarioExecutorBranchTest {
    @Test
    fun `should expand included fragment steps during execution`() {
        val registry = FragmentRegistry()
        registry.register(
            Fragment(
                name = "auth",
                steps =
                    listOf(
                        Step(type = StepType.GIVEN, description = "token exists"),
                        Step(type = StepType.THEN, description = "token is valid"),
                    ),
            ),
        )
        val executor =
            BerryCrushScenarioExecutor(
                specRegistry = SpecRegistry(),
                configuration = BerryCrushConfigurationProvider.from(BerryCrushConfiguration()),
                fragmentRegistry = registry,
            )

        val scenario =
            Scenario(
                name = "fragment expansion",
                steps =
                    listOf(
                        Step(type = StepType.GIVEN, description = "include auth", fragmentName = "auth"),
                        Step(type = StepType.THEN, description = "final check"),
                    ),
            )

        val result = executor.execute(scenario)

        assertEquals(ResultStatus.ERROR, result.status)
        assertEquals(3, result.stepResults.size)
        assertEquals(
            listOf("token exists", "token is valid", "final check"),
            result.stepResults.map { it.step.description },
        )
        assertTrue(result.stepResults.all { it.status != ResultStatus.PASSED })
    }

    @Test
    fun `should fail fast when included fragment is missing`() {
        val executor =
            BerryCrushScenarioExecutor(
                specRegistry = SpecRegistry(),
                configuration = BerryCrushConfigurationProvider.from(BerryCrushConfiguration()),
                fragmentRegistry = FragmentRegistry(),
            )
        val scenario =
            Scenario(
                name = "missing fragment",
                steps =
                    listOf(
                        Step(type = StepType.GIVEN, description = "include missing", fragmentName = "does-not-exist"),
                    ),
            )

        val error =
            assertFailsWith<ConfigurationException> {
                executor.execute(scenario)
            }

        assertEquals(true, error.message?.contains("Fragment 'does-not-exist' not found"))
    }

    @Test
    fun `should return error when assertion runs without previous response`() {
        val executor =
            BerryCrushScenarioExecutor(
                specRegistry = SpecRegistry(),
                configuration = BerryCrushConfigurationProvider.from(BerryCrushConfiguration()),
            )
        val scenario =
            Scenario(
                name = "assertion without response",
                steps =
                    listOf(
                        Step(
                            type = StepType.THEN,
                            description = "assert status without request",
                            assertions = listOf(Condition.Status(expected = 200).toAssertion()),
                        ),
                    ),
            )

        val result = executor.execute(scenario)

        assertEquals(ResultStatus.ERROR, result.status)
        assertEquals(ResultStatus.ERROR, result.stepResults.single().status)
        assertEquals(
            true,
            result.stepResults
                .single()
                .error
                ?.message
                ?.contains("No previous response"),
        )
    }

    @Test
    fun `should return clear error when dynamic call operation id cannot be resolved`() {
        val executor =
            BerryCrushScenarioExecutor(
                specRegistry = SpecRegistry(),
                configuration = BerryCrushConfigurationProvider.from(BerryCrushConfiguration()),
            )
        val scenario =
            Scenario(
                name = "unresolved dynamic call",
                steps =
                    listOf(
                        Step(
                            type = StepType.WHEN,
                            description = "call unresolved operation",
                            operationId = "{{operationId}}",
                        ),
                    ),
            )

        val result = executor.execute(scenario)

        assertEquals(ResultStatus.ERROR, result.status)
        assertEquals(ResultStatus.ERROR, result.stepResults.single().status)
        assertEquals(
            true,
            result.stepResults
                .single()
                .error
                ?.message
                ?.contains("Unable to resolve operation ID"),
        )
    }
}
