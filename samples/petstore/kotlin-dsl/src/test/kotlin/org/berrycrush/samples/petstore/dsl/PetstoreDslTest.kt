package org.berrycrush.samples.petstore.dsl

import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.dsl.BerryCrushSuite
import org.berrycrush.dsl.berrycrush
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
import kotlin.test.assertTrue

/**
 * Comprehensive Kotlin DSL tests for the BerryCrush library.
 *
 * This test suite exercises all DSL features against the petstore application.
 * Uses BerryCrushExtension with dynamic port configuration from Spring Boot.
 */
@SpringBootTest(
    classes = [PetstoreApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(BerryCrushExtension::class)
@BerryCrushSpec("classpath:/petstore.yaml")
class PetstoreDslTest {
    @LocalServerPort
    private var port: Int = 0

    // For tests that use the DSL builder directly
    private val specPath: String by lazy {
        javaClass.getResource("/petstore.yaml")?.path
            ?: error("petstore.yaml not found on classpath")
    }

    private val authSpecPath: String by lazy {
        javaClass.getResource("/auth.yaml")?.path
            ?: error("auth.yaml not found on classpath")
    }

    @BeforeEach
    fun setup(config: BerryCrushConfiguration) {
        // Configure dynamic port - must be set before executor is used
        config.baseUrl = "http://localhost:$port/api/v1"
    }

    // ========== Basic Scenario Tests ==========

    @Nested
    @DisplayName("Basic Scenario DSL")
    inner class BasicScenarioDsl {
        @Test
        @DisplayName("scenario with given-when-then")
        fun `scenario with given-when-then`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("List all pets") {
                    given("a request is prepared") {}
                    `when`("I request the list of pets") {
                        call("listPets")
                    }
                    then("I receive a successful response") {
                        statusCode(200)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status, "Scenario should pass")
        }

        @Test
        @DisplayName("scenario with and step")
        fun `scenario with and step`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Get pet and verify") {
                    `when`("I get a pet") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    then("response is successful") {
                        statusCode(200)
                    }
                    and("pet has a name") {
                        bodyEquals("$.name", "Max")
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("scenario with but step")
        fun `scenario with but step`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Get pet but not error") {
                    `when`("I get a pet") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    then("response is successful") {
                        statusCode(200)
                    }
                    but("no error field exists") {
                        bodyEquals("$.name", "Max")
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("scenario with tags")
        fun `scenario with tags`(suite: BerryCrushSuite) {
            val scenario =
                suite.scenario("Tagged scenario", tags = setOf("smoke", "api")) {
                    `when`("I list pets") {
                        call("listPets")
                    }
                    then("I get results") {
                        statusCode(200)
                    }
                }

            assertEquals(setOf("smoke", "api"), scenario.tags)
        }
    }

    // ========== Call DSL Tests ==========

    @Nested
    @DisplayName("Call Configuration DSL")
    inner class CallConfigurationDsl {
        @Test
        @DisplayName("call with path parameters")
        fun `call with path parameters`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Get pet by ID") {
                    `when`("I get pet by ID") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    then("pet is returned") {
                        statusCode(200)
                        bodyEquals("$.id", 1)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("call with query parameters")
        fun `call with query parameters`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("List pets with limit") {
                    `when`("I list pets with limit") {
                        call("listPets") {
                            queryParam("limit", 10)
                        }
                    }
                    then("pets are returned") {
                        statusCode(200)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("call with custom headers")
        fun `call with custom headers`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Request with custom header") {
                    `when`("I make a request with headers") {
                        call("listPets") {
                            header("X-Request-Id", "test-123")
                            header("Accept", "application/json")
                        }
                    }
                    then("request succeeds") {
                        statusCode(200)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("call with JSON string body")
        fun `call with JSON string body`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Create pet with string body") {
                    `when`("I create a pet") {
                        call("createPet") {
                            body("""{"name": "TestPet", "status": "available"}""")
                        }
                    }
                    then("pet is created") {
                        statusCode(201)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("call with map body")
        fun `call with map body`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Create pet with map body") {
                    `when`("I create a pet") {
                        call("createPet") {
                            body(
                                mapOf(
                                    "name" to "MapPet",
                                    "status" to "available",
                                ),
                            )
                        }
                    }
                    then("pet is created") {
                        statusCode(201)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("call with bearer token")
        fun `call with bearer token`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Request with bearer token") {
                    `when`("I make an authenticated request") {
                        call("listPets") {
                            bearerToken("test-token-123")
                        }
                    }
                    then("request succeeds") {
                        statusCode(200)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("call with basic auth")
        fun `call with basic auth`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Request with basic auth") {
                    `when`("I make a basic auth request") {
                        call("listPets") {
                            basicAuth("admin", "password")
                        }
                    }
                    then("request succeeds") {
                        statusCode(200)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("call with API key")
        fun `call with API key`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Request with API key") {
                    `when`("I make an API key request") {
                        call("listPets") {
                            apiKey("my-secret-api-key")
                        }
                    }
                    then("request succeeds") {
                        statusCode(200)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("call with custom API key header")
        fun `call with custom API key header`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Request with custom API key header") {
                    `when`("I make a request with custom API key header") {
                        call("listPets") {
                            apiKey("X-Custom-Key", "custom-key-value")
                        }
                    }
                    then("request succeeds") {
                        statusCode(200)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }
    }

    // ========== Assertion DSL Tests ==========

    @Nested
    @DisplayName("Assertion DSL")
    inner class AssertionDsl {
        @Test
        @DisplayName("statusCode exact match")
        fun `statusCode exact match`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Status code exact") {
                    `when`("I list pets") {
                        call("listPets")
                    }
                    then("status is 200") {
                        statusCode(200)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("statusCode range match")
        fun `statusCode range match`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Status code range") {
                    `when`("I list pets") {
                        call("listPets")
                    }
                    then("status is 2xx") {
                        statusCode(200..299)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("bodyEquals assertion")
        fun `bodyEquals assertion`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Body equals") {
                    `when`("I get pet") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    then("pet name matches") {
                        bodyEquals("$.name", "Max")
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("bodyArrayNotEmpty assertion")
        fun `bodyArrayNotEmpty assertion`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Array not empty") {
                    `when`("I list pets") {
                        call("listPets")
                    }
                    then("list is not empty") {
                        bodyArrayNotEmpty("$.pets")
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("headerExists assertion")
        fun `headerExists assertion`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Header exists") {
                    `when`("I list pets") {
                        call("listPets")
                    }
                    then("content-type header exists") {
                        headerExists("Content-Type")
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("headerEquals assertion")
        fun `headerEquals assertion`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario =
                suite.scenario("Header equals") {
                    `when`("I list pets") {
                        call("listPets")
                    }
                    then("content-type is JSON") {
                        headerEquals("Content-Type", "application/json")
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }
    }

    // ========== Extraction DSL Tests ==========

    @Nested
    @DisplayName("Extraction DSL")
    inner class ExtractionDsl {
        @Test
        @DisplayName("extract value from response")
        fun `extract value from response`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            // Create a pet first
            val createScenario =
                suite.scenario("Create and extract ID") {
                    `when`("I create a pet") {
                        call("createPet") {
                            body(mapOf("name" to "ExtractTest", "status" to "available"))
                        }
                    }
                    then("pet is created") {
                        statusCode(201)
                        extractTo("createdPetId", "$.id")
                    }
                }

            val result = executor.execute(createScenario)
            assertEquals(ResultStatus.PASSED, result.status)
            // Extraction is verified by the scenario passing - variables are stored in ExecutionContext
        }
    }

    // ========== Fragment DSL Tests ==========

    @Nested
    @DisplayName("Fragment DSL")
    inner class FragmentDsl {
        @Test
        @DisplayName("include fragment in scenario")
        fun `include fragment in scenario`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            // Define the fragment
            val authFragment =
                suite.fragment("setup_pet") {
                    given("a pet exists") {
                        call("createPet") {
                            body(mapOf("name" to "FragmentPet", "status" to "available"))
                        }
                        extractTo("fragmentPetId", "$.id")
                    }
                }

            // Use the fragment
            val scenario =
                suite.scenario("Use fragment") {
                    include(authFragment)
                    then("we have a pet ID") {
                        statusCode(201)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }

        @Test
        @DisplayName("include fragment by name")
        fun `include fragment by name`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            // Define the fragment first
            suite.fragment("create_pet_fragment") {
                `when`("creating a pet") {
                    call("createPet") {
                        body(mapOf("name" to "NamedFragmentPet", "status" to "available"))
                    }
                }
            }

            // Use the fragment by name
            val scenario =
                suite.scenario("Use named fragment") {
                    include("create_pet_fragment")
                    then("pet is created") {
                        statusCode(201)
                    }
                }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }
    }

    // ========== Scenario Outline DSL Tests ==========

    @Nested
    @DisplayName("Scenario Outline DSL")
    inner class ScenarioOutlineDsl {
        @Test
        @DisplayName("scenario outline with examples")
        fun `scenario outline with examples`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenarios =
                suite.scenarioOutline("Get pets by ID") {
                    `when`("I get pet with ID <petId>") {
                        call("getPetById") {
                            pathParam("petId", 1)
                        }
                    }
                    then("I see the pet") {
                        statusCode(200)
                    }
                    examples(
                        row("petId" to 1),
                        row("petId" to 2),
                    )
                }

            assertEquals(2, scenarios.size, "Should generate 2 scenarios from examples")
            scenarios.forEach { scenario ->
                val result = executor.execute(scenario)
                assertEquals(ResultStatus.PASSED, result.status)
            }
        }
    }

    // ========== Configuration DSL Tests ==========

    @Nested
    @DisplayName("Configuration DSL")
    inner class ConfigurationDsl {
        @Test
        @DisplayName("configure baseUrl")
        fun `configure baseUrl`() {
            val configuredSuite =
                berrycrush(specPath) {
                    baseUrl = "http://localhost:$port"
                }

            assertEquals("http://localhost:$port", configuredSuite.configuration.baseUrl)
        }

        @Test
        @DisplayName("configure timeout")
        fun `configure timeout`() {
            val configuredSuite =
                berrycrush(specPath) {
                    baseUrl = "http://localhost:$port"
                    timeout(30)
                }

            assertEquals(java.time.Duration.ofSeconds(30), configuredSuite.configuration.timeout)
        }

        @Test
        @DisplayName("configure with block syntax")
        fun `configure with block syntax`() {
            val configuredSuite =
                berrycrush {
                    spec(specPath)
                    configure {
                        baseUrl = "http://localhost:$port"
                        timeout(60)
                    }
                }

            assertEquals("http://localhost:$port", configuredSuite.configuration.baseUrl)
            assertEquals(java.time.Duration.ofSeconds(60), configuredSuite.configuration.timeout)
        }
    }

    // ========== Multi-Spec DSL Tests ==========

    @Nested
    @DisplayName("Multi-Spec DSL")
    inner class MultiSpecDsl {
        @Test
        @DisplayName("register multiple specs")
        fun `register multiple specs`() {
            val multiSpecSuite =
                berrycrush {
                    spec("petstore", specPath)
                    spec("auth", authSpecPath)
                    configure {
                        baseUrl = "http://localhost:$port"
                    }
                }

            val petSpec = multiSpecSuite.specRegistry.get("petstore")
            val authSpec = multiSpecSuite.specRegistry.get("auth")

            assertTrue(petSpec.name == "petstore", "Petstore spec should be registered")
            assertTrue(authSpec.name == "auth", "Auth spec should be registered")
        }

        @Test
        @DisplayName("use specific spec in call")
        fun `use specific spec in call`() {
            val multiSpecSuite =
                berrycrush {
                    spec("petstore", specPath)
                    configure {
                        baseUrl = "http://localhost:$port/api/v1"
                    }
                }
            val multiSpecExecutor = BerryCrushScenarioExecutor(multiSpecSuite.specRegistry, multiSpecSuite.configuration)

            val scenario =
                multiSpecSuite.scenario("Use specific spec") {
                    `when`("I call using petstore spec") {
                        using("petstore")
                        call("listPets")
                    }
                    then("request succeeds") {
                        statusCode(200)
                    }
                }

            val result = multiSpecExecutor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }
    }

    // ========== Edge Cases and Error Handling ==========

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {
        @Test
        @DisplayName("empty scenario steps")
        fun `empty scenario with only name`(suite: BerryCrushSuite) {
            val scenario =
                suite.scenario("Empty scenario") {
                    given("nothing to do") {}
                }

            assertEquals("Empty scenario", scenario.name)
            assertEquals(1, scenario.steps.size)
        }

        @Test
        @DisplayName("scenario with many steps")
        fun `scenario with many steps`(suite: BerryCrushSuite) {
            val scenario =
                suite.scenario("Multi-step scenario") {
                    given("step 1") {}
                    and("step 2") {}
                    and("step 3") {}
                    `when`("step 4") {
                        call("listPets")
                    }
                    then("step 5") {
                        statusCode(200)
                    }
                    and("step 6") {}
                    but("step 7") {}
                }

            assertEquals(7, scenario.steps.size)
        }
    }
}
