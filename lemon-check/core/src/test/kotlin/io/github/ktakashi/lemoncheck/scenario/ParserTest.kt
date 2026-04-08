package io.github.ktakashi.lemoncheck.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserTest {
    @Test
    fun `should parse simple scenario`() {
        val source =
            """
            scenario: List all pets
              given the API is available
              when I request the list of pets
                call ^listPets
              then I get a successful response
                assert status 200
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertEquals(1, result.ast!!.scenarios.size)
        assertEquals("List all pets", result.ast.scenarios[0].name)
    }

    @Test
    fun `should parse scenario with call action`() {
        val source =
            """
            scenario: Get a pet by ID
              when I request a specific pet
                call ^getPetById
                  petId: 123
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val scenario = result.ast!!.scenarios[0]
        assertTrue(scenario.steps.isNotEmpty())
    }

    @Test
    fun `should parse scenario with extraction`() {
        val source =
            """
            scenario: Create and retrieve pet
              when I create a pet
                call ^createPet
                extract $.id => petId
              then I can retrieve it
                call ^getPetById
                  petId: {{petId}}
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
    }

    @Test
    fun `should parse scenario with assertions`() {
        val source =
            """
            scenario: Verify pet response
              when I get a pet
                call ^getPetById
                  petId: 1
              then the response is correct
                assert status 200
                assert $.name equals "Fluffy"
                assert header Content-Type = "application/json"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
    }

    @Test
    fun `should parse fragment definition`() {
        val source =
            """
            fragment: authenticate
              given I have valid credentials
                call ^login
                  username: "admin"
                  password: "secret"
                extract $.token => authToken
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertEquals(1, result.ast!!.fragments.size)
        assertEquals("authenticate", result.ast.fragments[0].name)
    }

    @Test
    fun `should parse scenario with fragment include`() {
        val source =
            """
            scenario: Protected endpoint
              given I am authenticated
                include authenticate
              when I access protected resource
                call ^getProtectedData
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
    }

    @Test
    fun `should parse scenario outline with examples`() {
        val source =
            """
            outline: Test multiple pets
              when I get a pet
                call ^getPetById
                  petId: {{petId}}
              then I see the correct name
                assert $.name equals {{expectedName}}
              examples:
                | petId | expectedName |
                | 1     | "Fluffy"     |
                | 2     | "Buddy"      |
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val scenario = result.ast!!.scenarios[0]
        assertTrue(scenario.isOutline)
        assertNotNull(scenario.examples)
        assertEquals(2, scenario.examples.size)
    }

    @Test
    fun `should parse scenario with using for spec selection`() {
        val source =
            """
            scenario: Multi-spec test
              when I call the first API
                call using petstore ^listPets
              and I call the second API
                call using inventory ^getItems
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
    }

    @Test
    fun `should handle and keyword`() {
        val source =
            """
            scenario: Multiple assertions
              when I get pets
                call ^listPets
              then the response is an array
                assert $ notEmpty
              and it contains at least one pet
                assert $[0].id equals 1
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertTrue(
            result.ast!!
                .scenarios[0]
                .steps
                .any { it.keyword == StepKeyword.AND },
        )
    }

    @Test
    fun `should track source locations`() {
        val source =
            """
            scenario: Test
              given something
            """.trimIndent()

        val result = Parser.parse(source, "test.scenario")

        assertTrue(result.isSuccess)
        assertEquals(
            1,
            result.ast!!
                .scenarios[0]
                .location.line,
        )
        assertEquals(
            "test.scenario",
            result.ast.scenarios[0]
                .location.file,
        )
    }

    @Test
    fun `should report parse errors`() {
        val source =
            """
            invalid content without scenario keyword
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun `should parse schema validation assertion`() {
        val source =
            """
            scenario: Schema validation
              when I get a pet
                call ^getPetById
                  petId: 1
              then the response matches schema
                assert schema
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
    }

    @Test
    fun `should parse response time assertion`() {
        val source =
            """
            scenario: Performance test
              when I get pets
                call ^listPets
              then the response is fast
                assert responseTime 1000
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
    }

    @Test
    fun `should parse multiple scenarios in one file`() {
        val source =
            """
            scenario: First scenario
              when I do something
                call ^listPets

            scenario: Second scenario
              when I do something else
                call ^createPet
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertEquals(2, result.ast!!.scenarios.size)
    }

    @Test
    fun `should parse request body`() {
        val source =
            """
            scenario: Create pet with body
              when I create a pet
                call ^createPet
                  body: {"name": "Fluffy", "tag": "dog"}
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
    }

    @Test
    fun `should parse headers`() {
        val source =
            """
            scenario: Request with custom headers
              when I make a request
                call ^listPets
                  header_Authorization: "Bearer token123"
                  header_Accept: "application/json"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
    }
}
