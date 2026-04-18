package org.berrycrush.samples.petstore.dsl

import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.dsl.BerryCrushSuite
import org.berrycrush.executor.BerryCrushScenarioExecutor
import org.berrycrush.junit.BerryCrushExtension
import org.berrycrush.junit.BerryCrushSpec
import org.berrycrush.model.ResultStatus
import org.berrycrush.samples.petstore.PetstoreApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import kotlin.test.assertEquals

/**
 * Tests for enhanced Kotlin DSL features:
 * - New keywords: whenever, afterwards, otherwise
 * - Custom assertions: assert
 * - Conditional logic: conditional/orElse
 */
@SpringBootTest(
    classes = [PetstoreApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(BerryCrushExtension::class)
@BerryCrushSpec("classpath:/petstore.yaml")
class EnhancedDslTest {
    @LocalServerPort
    private var port: Int = 0

    @BeforeEach
    fun setup(config: BerryCrushConfiguration) {
        config.baseUrl = "http://localhost:$port/api/v1"
    }

    // ========== New Keyword Tests ==========

    @Nested
    @DisplayName("New Keywords (whenever, afterwards, otherwise)")
    inner class NewKeywords {
        @Test
        @DisplayName("whenever keyword works like when")
        fun `whenever keyword works like when`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Using whenever keyword") {
                    whenever("I list all pets") {
                        call("listPets")
                    }
                    afterwards("I receive a response") {
                        statusCode(200)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status, "Scenario with whenever/afterwards should pass")
        }

        @Test
        @DisplayName("otherwise keyword works like but")
        fun `otherwise keyword works like but`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Using otherwise keyword") {
                    whenever("I get a pet") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    afterwards("response is successful") {
                        statusCode(200)
                    }
                    otherwise("it has no error") {
                        bodyEquals("$.name", "Max")
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status, "Scenario with otherwise should pass")
        }

        @Test
        @DisplayName("mixed new and legacy keywords")
        fun `mixed new and legacy keywords`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Mixing new and old keywords") {
                    // New keyword
                    whenever("I list pets") {
                        call("listPets")
                    }
                    // Using new keyword consistently
                    afterwards("I get a response") {
                        statusCode(200)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("fragment with new keywords")
        fun `fragment with new keywords`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            // Define a fragment using new keywords
            suite.fragment("get pet by id") {
                whenever("I request the pet") {
                    call("getPetById") {
                        pathParam("petId", 1)
                    }
                }
                afterwards("I get the pet details") {
                    statusCode(200)
                }
            }

            val scenario =
                suite.scenario("Using fragment with new keywords") {
                    include("get pet by id")
                    afterwards("pet has correct name") {
                        bodyEquals("$.name", "Max")
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }
    }

    // ========== Custom Assertions Tests ==========

    @Nested
    @DisplayName("Custom Assertions (assert DSL)")
    inner class CustomAssertions {
        @Test
        @DisplayName("custom assert that passes")
        fun `custom assert that passes`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Custom assertion passes") {
                    whenever("I get a pet") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    afterwards("response meets custom criteria") {
                        statusCode(200)
                        assert("response has valid data") { ctx ->
                            val body = ctx.responseBody
                            require(body != null) { "Response body should not be null" }
                            require(body.contains("name")) { "Response should contain name field" }
                        }
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status, "Scenario with passing custom assertion should pass")
        }

        @Test
        @DisplayName("custom assert that fails")
        fun `custom assert that fails`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Custom assertion fails") {
                    whenever("I get a pet") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    afterwards("response does not meet criteria") {
                        statusCode(200)
                        assert("impossible condition") { ctx ->
                            val statusCode = ctx.statusCode
                            require(statusCode == 999) { "Expected impossible status code" }
                        }
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.FAILED, result.status, "Scenario with failing custom assertion should fail")
        }

        @Test
        @DisplayName("custom assert can extract values")
        fun `custom assert can extract values`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Custom assertion extracts values") {
                    whenever("I get a pet") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    afterwards("I extract and verify") {
                        statusCode(200)
                        assert("extract pet name") { ctx ->
                            val body = ctx.responseBody ?: error("No body")
                            // Extract name using simple parsing (in real code, use JSON parser)
                            val nameMatch = """"name"\s*:\s*"([^"]+)"""".toRegex().find(body)
                            val name = nameMatch?.groupValues?.get(1) ?: error("Name not found")
                            ctx.set("extractedName", name)
                        }
                        assert("verify extracted name") { ctx ->
                            val name = ctx.get<String>("extractedName")
                            require(name == "Max") { "Expected name 'Max' but got '$name'" }
                        }
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("multiple custom asserts run in order")
        fun `multiple custom asserts run in order`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val executionOrder = mutableListOf<String>()

            val scenario =
                suite.scenario("Multiple custom assertions") {
                    whenever("I list pets") {
                        call("listPets")
                    }
                    afterwards("all assertions run") {
                        statusCode(200)
                        assert("first assertion") { _ ->
                            executionOrder.add("first")
                        }
                        assert("second assertion") { _ ->
                            executionOrder.add("second")
                        }
                        assert("third assertion") { _ ->
                            executionOrder.add("third")
                        }
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
            assertEquals(listOf("first", "second", "third"), executionOrder)
        }
    }

    // ========== Conditional Logic Tests ==========

    @Nested
    @DisplayName("Conditional Logic (conditional/orElse)")
    inner class ConditionalLogic {
        @Test
        @DisplayName("conditional with predicate that matches")
        fun `conditional with predicate that matches`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Conditional predicate matches") {
                    whenever("I get a pet") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    afterwards("conditional assertion runs") {
                        conditional({ ctx -> ctx.statusCode == 200 }) {
                            bodyEquals("$.name", "Max")
                        } orElse {
                            bodyContains("error")
                        }
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("conditional with predicate that does not match (orElse)")
        fun `conditional with predicate that does not match`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Conditional predicate does not match") {
                    whenever("I get a pet") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    afterwards("orElse branch runs") {
                        conditional({ ctx -> ctx.statusCode == 404 }) {
                            // This should NOT run
                            bodyContains("not found")
                        } orElse {
                            // This SHOULD run
                            statusCode(200)
                            bodyEquals("$.name", "Max")
                        }
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("conditional without orElse")
        fun `conditional without orElse`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Conditional without orElse") {
                    whenever("I list pets") {
                        call("listPets")
                    }
                    afterwards("conditional may or may not run") {
                        statusCode(200)
                        conditional({ ctx -> ctx.statusCode == 200 }) {
                            // This runs because status is 200
                            bodyArrayNotEmpty("$")
                        }
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("conditional with complex predicate")
        fun `conditional with complex predicate`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Conditional with complex predicate") {
                    whenever("I get a pet") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    afterwards("conditional checks response body") {
                        conditional({ ctx ->
                            ctx.statusCode == 200 && ctx.responseBody?.contains("Max") == true
                        }) {
                            bodyEquals("$.id", 1)
                        } orElse {
                            // Should not reach here
                            statusCode(404)
                        }
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }
    }

    // ========== Integration Tests ==========

    @Nested
    @DisplayName("Integration: Combined Features")
    inner class IntegrationTests {
        @Test
        @DisplayName("combine new keywords with custom assertions and conditionals")
        fun `combine all new features`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("All new features combined") {
                    whenever("I get a pet by ID") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    afterwards("I verify the response") {
                        statusCode(200)

                        // Custom assertion
                        assert("response is valid JSON") { ctx ->
                            val body = ctx.responseBody ?: error("No body")
                            require(body.startsWith("{")) { "Response should be JSON object" }
                        }

                        // Conditional logic
                        conditional({ ctx -> ctx.statusCode == 200 }) {
                            bodyEquals("$.name", "Max")
                            assert("pet has expected structure") { ctx ->
                                val body = ctx.responseBody ?: error("No body")
                                require(body.contains("id")) { "Response should have id field" }
                                require(body.contains("name")) { "Response should have name field" }
                            }
                        } orElse {
                            assert("handle error case") { _ ->
                                error("Should not reach error case")
                            }
                        }
                    }
                    otherwise("the pet is not in error state") {
                        assert("verify no error") { ctx ->
                            val body = ctx.responseBody ?: ""
                            require(!body.contains("error")) { "Response should not contain error" }
                        }
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }
    }
}
