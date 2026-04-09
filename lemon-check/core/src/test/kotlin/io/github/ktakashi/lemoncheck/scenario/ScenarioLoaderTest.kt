package io.github.ktakashi.lemoncheck.scenario

import io.github.ktakashi.lemoncheck.model.StepType
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScenarioLoaderTest {
    private val loader = ScenarioLoader()

    private fun getResourcePath(name: String): java.nio.file.Path {
        val url = javaClass.getResource("/scenarios/$name") ?: error("Resource not found: /scenarios/$name")
        return Paths.get(url.toURI())
    }

    @Test
    fun `should load scenarios from valid file`() {
        val path = getResourcePath("valid/petstore-crud.scenario")
        val scenarios = loader.loadScenariosFromFile(path)

        assertEquals(3, scenarios.size)
        assertEquals("List all pets", scenarios[0].name)
        assertEquals("Create a new pet", scenarios[1].name)
        assertEquals("Get pet by ID", scenarios[2].name)
    }

    @Test
    fun `should load fragments from valid file`() {
        val path = getResourcePath("valid/auth.fragment")
        val fragments = loader.loadFragmentsFromFile(path)

        assertEquals(2, fragments.size)
        assertTrue(fragments.containsKey("authenticate"))
        assertTrue(fragments.containsKey("setAuthHeader"))
    }

    @Test
    fun `should load parameterized scenario with examples`() {
        val path = getResourcePath("valid/parameterized.scenario")
        val scenarios = loader.loadScenariosFromFile(path)

        assertEquals(1, scenarios.size)
        val scenario = scenarios[0]
        assertEquals("Test multiple pet retrieval", scenario.name)
        assertNotNull(scenario.examples)
        assertEquals(3, scenario.examples.size)
    }

    @Test
    fun `should fail on invalid scenario file`() {
        val path = getResourcePath("invalid/random-content.scenario")

        assertFailsWith<IllegalArgumentException> {
            loader.loadScenariosFromFile(path)
        }
    }

    @Test
    fun `should load scenarios from directory`() {
        val path = getResourcePath("valid")
        val scenarios = loader.loadScenariosFromDirectory(path)

        assertTrue(scenarios.size >= 4) // At least from two .scenario files
    }

    @Test
    fun `should load fragments from directory`() {
        val path = getResourcePath("valid")
        val fragments = loader.loadFragmentsFromDirectory(path)

        assertTrue(fragments.isNotEmpty())
    }

    @Test
    fun `should parse scenario with steps containing operations`() {
        val source =
            """
            |scenario: Test with operations
            |  when I list pets
            |    call ^listPets
            |  then I get results
            |    assert status 200
            """.trimMargin()

        val scenarios = loader.loadScenariosFromString(source)

        assertEquals(1, scenarios.size)
        val scenario = scenarios[0]
        assertTrue(scenario.steps.isNotEmpty())
        assertTrue(scenario.steps.any { it.operationId == "listPets" })
    }

    @Test
    fun `should transform step types correctly`() {
        val source =
            """
            |scenario: Step types test
            |  given the prerequisite
            |  when I do something
            |  then I see results
            |  and another assertion
            """.trimMargin()

        val scenarios = loader.loadScenariosFromString(source)
        val steps = scenarios[0].steps

        assertTrue(steps.any { it.type == StepType.GIVEN })
        assertTrue(steps.any { it.type == StepType.WHEN })
        assertTrue(steps.any { it.type == StepType.THEN })
        assertTrue(steps.any { it.type == StepType.AND })
    }

    @Test
    fun `should transform extractions correctly`() {
        val source =
            """
            |scenario: Extraction test
            |  when I create something
            |    call ^createPet
            |    extract $.id => petId
            """.trimMargin()

        val scenarios = loader.loadScenariosFromString(source)
        val step = scenarios[0].steps.first()

        assertTrue(step.extractions.isNotEmpty())
        assertEquals("petId", step.extractions[0].variableName)
        assertEquals("$.id", step.extractions[0].jsonPath)
    }

    @Test
    fun `should transform assertions correctly`() {
        val source =
            """
            |scenario: Assertions test
            |  then the response is correct
            |    assert status 200
            |    assert $.name equals "Fluffy"
            """.trimMargin()

        val scenarios = loader.loadScenariosFromString(source)
        val step = scenarios[0].steps.first()

        assertTrue(step.assertions.isNotEmpty())
    }

    @Test
    fun `should handle fragment includes`() {
        val source =
            """
            |scenario: Fragment include test
            |  given I am authenticated
            |    include authenticate
            |  when I call protected endpoint
            |    call ^getProtected
            """.trimMargin()

        val scenarios = loader.loadScenariosFromString(source)
        val steps = scenarios[0].steps

        assertTrue(steps.any { it.fragmentName == "authenticate" })
    }

    @Test
    fun `should handle call with parameters`() {
        val source =
            """
            |scenario: Call with params
            |  when I get a specific pet
            |    call ^getPetById
            |      petId: 123
            |      query_include: "details"
            """.trimMargin()

        val scenarios = loader.loadScenariosFromString(source)
        val step = scenarios[0].steps.first { it.operationId != null }

        assertEquals(123L, step.pathParams["petId"])
        assertEquals("details", step.queryParams["include"])
    }

    @Test
    fun `should handle call with headers and body`() {
        val source =
            """
            |scenario: Call with headers
            |  when I create a pet
            |    call ^createPet
            |      header_Authorization: "Bearer token"
            |      body: {"name": "Fluffy"}
            """.trimMargin()

        val scenarios = loader.loadScenariosFromString(source)
        val step = scenarios[0].steps.first { it.operationId != null }

        assertEquals("Bearer token", step.headers["Authorization"])
        assertNotNull(step.body)
    }

    @Test
    fun `should load file content with parameters`() {
        val source =
            """
            |parameters:
            |  baseUrl: "http://localhost:8080"
            |  timeout: 60
            |  shareVariablesAcrossScenarios: true
            |
            |scenario: Test with parameters
            |  when I get pets
            |    call ^listPets
            """.trimMargin()

        val content = loader.loadFileContentFromString(source)

        assertEquals(1, content.scenarios.size)
        assertEquals(3, content.parameters.size)
        assertEquals("http://localhost:8080", content.parameters["baseUrl"])
        assertEquals(60L, content.parameters["timeout"])
        assertEquals(true, content.parameters["shareVariablesAcrossScenarios"])
    }

    @Test
    fun `should load file content with empty parameters`() {
        val source =
            """
            |scenario: Test without parameters
            |  when I get pets
            |    call ^listPets
            """.trimMargin()

        val content = loader.loadFileContentFromString(source)

        assertEquals(1, content.scenarios.size)
        assertTrue(content.parameters.isEmpty())
    }

    @Test
    fun `should load file content with header parameters`() {
        val source =
            """
            |parameters:
            |  header.Authorization: "Bearer test-token"
            |  header.X-Custom: "custom-value"
            |  logRequests: true
            |
            |scenario: Authenticated request
            |  when I make a request
            |    call ^listPets
            """.trimMargin()

        val content = loader.loadFileContentFromString(source)

        assertEquals(1, content.scenarios.size)
        assertEquals("Bearer test-token", content.parameters["header.Authorization"])
        assertEquals("custom-value", content.parameters["header.X-Custom"])
        assertEquals(true, content.parameters["logRequests"])
    }
}
