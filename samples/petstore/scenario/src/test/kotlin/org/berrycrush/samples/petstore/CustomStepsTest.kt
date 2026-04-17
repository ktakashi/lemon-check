package org.berrycrush.samples.petstore

import org.berrycrush.dsl.BerryCrushSuite
import org.berrycrush.executor.BerryCrushScenarioExecutor
import org.berrycrush.junit.BerryCrushConfiguration
import org.berrycrush.junit.BerryCrushExtension
import org.berrycrush.junit.BerryCrushSpec
import org.berrycrush.model.ResultStatus
import org.berrycrush.samples.petstore.assertions.PetstoreAssertions
import org.berrycrush.samples.petstore.steps.PetstoreSteps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.berrycrush.config.BerryCrushConfiguration as Config

/**
 * Test demonstrating custom step definitions in BerryCrush.
 *
 * Uses the BerryCrushExtension with stepClasses to enable custom step invocation.
 */
@SpringBootTest(
    classes = [PetstoreApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(BerryCrushExtension::class)
@BerryCrushSpec("classpath:/petstore.yaml")
@BerryCrushConfiguration(
    stepClasses = [PetstoreSteps::class],
    assertionClasses = [PetstoreAssertions::class],
)
class CustomStepsTest {
    @LocalServerPort
    private var port: Int = 0

    @BeforeEach
    fun setup(config: Config) {
        config.baseUrl = "http://localhost:$port/api/v1"
    }

    @Test
    @DisplayName("Custom steps with string parameters")
    fun testCustomStepsWithStringParameters(
        suite: BerryCrushSuite,
        executor: BerryCrushScenarioExecutor,
    ) {
        val scenario =
            suite.scenario("Custom steps with string parameters") {
                given("I have a pet named \"Fluffy\" with status \"available\"")
                then("the pet data should contain \"Fluffy\"")
                then("the pet data should contain \"available\"")
            }

        val result = executor.execute(scenario)
        assertEquals(ResultStatus.PASSED, result.status, "Scenario should pass: ${result.stepResults}")
    }

    @Test
    @DisplayName("Custom steps with mixed parameters")
    fun testCustomStepsWithMixedParameters(
        suite: BerryCrushSuite,
        executor: BerryCrushScenarioExecutor,
    ) {
        val scenario =
            suite.scenario("Custom steps with mixed parameters") {
                `when`("I reset the pet data")
                given("I have a pet named \"Max\" with status \"pending\"")
                then("the pet data should contain \"Max\"")
                then("I should have 1 pets with status pending")
                then("the pet price should be 199.99")
            }

        val result = executor.execute(scenario)
        assertEquals(ResultStatus.PASSED, result.status, "Scenario should pass: ${result.stepResults}")
    }

    @Test
    @DisplayName("Custom steps combined with API calls")
    fun testCustomStepsCombinedWithApiCalls(
        suite: BerryCrushSuite,
        executor: BerryCrushScenarioExecutor,
    ) {
        val scenario =
            suite.scenario("Custom steps combined with API calls") {
                // First set up custom data
                given("I have a pet named \"TestPet\" with status \"available\"")
                // Then verify via API
                `when`("I list pets") {
                    call("listPets")
                }
                then("the response is successful") {
                    statusCode(200)
                }
            }

        val result = executor.execute(scenario)
        assertEquals(ResultStatus.PASSED, result.status, "Scenario should pass: ${result.stepResults}")
    }
}
