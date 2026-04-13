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
    fun `should handle but keyword`() {
        val source =
            """
            scenario: Negative assertion
              when I request missing resource
                call ^getResource
              then response indicates error
                assert status 404
              but still has content type header
                assert header Content-Type
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val steps = result.ast!!.scenarios[0].steps
        assertTrue(
            steps.any { it.keyword == StepKeyword.BUT },
            "Should have BUT step",
        )
        assertEquals(
            "still has content type header",
            steps.first { it.keyword == StepKeyword.BUT }.description,
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

    @Test
    fun `should parse parameters section`() {
        val source =
            """
            parameters:
              baseUrl: "http://localhost:8080"
              timeout: 60
              shareVariablesAcrossScenarios: true

            scenario: Test with parameters
              when I get pets
                call ^listPets
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertNotNull(result.ast!!.parameters)
        assertEquals("http://localhost:8080", result.ast.parameters.values["baseUrl"])
        assertEquals(60L, result.ast.parameters.values["timeout"])
        assertEquals(true, result.ast.parameters.values["shareVariablesAcrossScenarios"])
    }

    @Test
    fun `should parse parameters with header override`() {
        val source =
            """
            parameters:
              header.Authorization: "Bearer test-token"
              logRequests: true

            scenario: Authenticated request
              when I make a request
                call ^listPets
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertNotNull(result.ast!!.parameters)
        assertEquals("Bearer test-token", result.ast.parameters.values["header.Authorization"])
        assertEquals(true, result.ast.parameters.values["logRequests"])
    }

    @Test
    fun `should parse parameters before scenarios and fragments`() {
        val source =
            """
            parameters:
              environment: "test"

            fragment: authenticate
              given credentials
                call ^login

            scenario: Test endpoint
              when I call the API
                call ^listPets
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertNotNull(result.ast!!.parameters)
        assertEquals("test", result.ast.parameters.values["environment"])
        assertEquals(1, result.ast.fragments.size)
        assertEquals(1, result.ast.scenarios.size)
    }

    @Test
    fun `should parse feature with background and tags`() {
        val source =
            """
            @api @feature
            feature: Pet Operations
              background:
                given: setup
                  call ^setupPet

              scenario: list pets
                when: list
                  call ^listPets
                  assert status 200

              @ignore
              scenario: ignored test
                when: invalid
                  call ^invalid
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertEquals(1, result.ast!!.features.size)

        val feature = result.ast.features[0]
        assertEquals("Pet Operations", feature.name)
        assertEquals(setOf("api", "feature"), feature.tags)
        assertNotNull(feature.background)
        assertEquals(1, feature.background.steps.size)
        assertEquals(2, feature.scenarios.size)

        // First scenario inherits feature tags
        val scenario1 = feature.scenarios[0]
        assertEquals("list pets", scenario1.name)
        assertEquals(emptySet(), scenario1.tags) // Tags are not merged in parsing, only in loading

        // Second scenario has @ignore tag
        val scenario2 = feature.scenarios[1]
        assertEquals("ignored test", scenario2.name)
        assertEquals(setOf("ignore"), scenario2.tags)
    }

    @Test
    fun `should parse standalone tagged scenario`() {
        val source =
            """
            @smoke @critical
            scenario: critical test
              when: test
                call ^test
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertEquals(1, result.ast!!.scenarios.size)
        assertEquals(setOf("smoke", "critical"), result.ast.scenarios[0].tags)
    }

    // =========================================================================
    // Assertion Parsing Tests
    // Tests for all assertion types supported by parseAssertAction
    // =========================================================================

    @Test
    fun `should parse status code assertion`() {
        val source =
            """
            scenario: Status code assertion test
              then: check status
                assert status 200
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(1, assertions.size)
        assertEquals(AssertionKind.STATUS_CODE, assertions[0].assertionType)
    }

    @Test
    fun `should parse statusCode variant assertion`() {
        val source =
            """
            scenario: StatusCode assertion test
              then: check status
                assert statusCode 201
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.STATUS_CODE, assertions[0].assertionType)
    }

    @Test
    fun `should parse body contains assertion`() {
        val source =
            """
            scenario: Body contains assertion test
              then: check body content
                assert contains "success"
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.BODY_CONTAINS, assertions[0].assertionType)
    }

    @Test
    fun `should parse bodyContains variant assertion`() {
        val source =
            """
            scenario: BodyContains assertion test
              then: check body
                assert bodyContains "error"
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.BODY_CONTAINS, assertions[0].assertionType)
    }

    @Test
    fun `should parse JSONPath equals assertion`() {
        val source =
            """
            scenario: JSONPath equals test
              then: check json value
                assert $.name equals "Fluffy"
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(1, assertions.size)
        assertEquals(AssertionKind.BODY_EQUALS, assertions[0].assertionType)
        assertEquals("$.name", assertions[0].path)
    }

    @Test
    fun `should parse JSONPath with = operator`() {
        val source =
            """
            scenario: JSONPath = operator test
              then: check json value
                assert $.id = 123
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.BODY_EQUALS, assertions[0].assertionType)
        assertEquals("$.id", assertions[0].path)
    }

    @Test
    fun `should parse JSONPath matches assertion`() {
        val source =
            """
            scenario: JSONPath matches regex test
              then: check pattern
                assert $.email matches ".*@.*"
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.BODY_MATCHES, assertions[0].assertionType)
        assertEquals("$.email", assertions[0].path)
    }

    @Test
    fun `should parse array size assertion`() {
        val source =
            """
            scenario: Array size test
              then: check array length
                assert $.items size 5
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.BODY_ARRAY_SIZE, assertions[0].assertionType)
        assertEquals("$.items", assertions[0].path)
    }

    @Test
    fun `should parse arraySize variant assertion`() {
        val source =
            """
            scenario: ArraySize variant test
              then: check array
                assert $.pets arraySize 3
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.BODY_ARRAY_SIZE, assertions[0].assertionType)
    }

    @Test
    fun `should parse array notEmpty assertion`() {
        val source =
            """
            scenario: Array not empty test
              then: check array has items
                assert $.results notEmpty
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.BODY_ARRAY_NOT_EMPTY, assertions[0].assertionType)
        assertEquals("$.results", assertions[0].path)
    }

    @Test
    fun `should parse header exists assertion`() {
        val source =
            """
            scenario: Header exists test
              then: check header present
                assert header X-Request-Id
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.HEADER_EXISTS, assertions[0].assertionType)
        assertEquals("X-Request-Id", assertions[0].headerName)
    }

    @Test
    fun `should parse header equals assertion with equals sign`() {
        val source =
            """
            scenario: Header equals test
              then: check header value
                assert header Content-Type = "application/json"
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.HEADER_EQUALS, assertions[0].assertionType)
        assertEquals("Content-Type", assertions[0].headerName)
    }

    @Test
    fun `should parse header equals assertion with colon`() {
        val source =
            """
            scenario: Header equals with colon test
              then: check header
                assert header Accept: "text/plain"
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.HEADER_EQUALS, assertions[0].assertionType)
        assertEquals("Accept", assertions[0].headerName)
    }

    @Test
    fun `should parse schema assertion`() {
        val source =
            """
            scenario: Schema validation test
              then: validate schema
                assert schema
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.MATCHES_SCHEMA, assertions[0].assertionType)
    }

    @Test
    fun `should parse matchesSchema variant assertion`() {
        val source =
            """
            scenario: MatchesSchema test
              then: validate
                assert matchesSchema
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.MATCHES_SCHEMA, assertions[0].assertionType)
    }

    @Test
    fun `should parse responseTime assertion`() {
        val source =
            """
            scenario: Response time test
              then: check performance
                assert responseTime 1000
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(AssertionKind.RESPONSE_TIME, assertions[0].assertionType)
    }

    @Test
    fun `should parse multiple assertions in single step`() {
        val source =
            """
            scenario: Multiple assertions test
              then: validate response
                assert status 200
                assert $.id equals 1
                assert $.name notEmpty
                assert header Content-Type = "application/json"
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(4, assertions.size)
        assertEquals(AssertionKind.STATUS_CODE, assertions[0].assertionType)
        assertEquals(AssertionKind.BODY_EQUALS, assertions[1].assertionType)
        assertEquals(AssertionKind.BODY_ARRAY_NOT_EMPTY, assertions[2].assertionType)
        assertEquals(AssertionKind.HEADER_EQUALS, assertions[3].assertionType)
    }

    // =========================================================================
    // Body File Parsing Tests
    // Tests for external body file references
    // =========================================================================

    @Test
    fun `should parse bodyFile with classpath prefix`() {
        val source =
            """
            scenario: External body test
              when I create a pet
                call ^createPet
                  bodyFile: "classpath:templates/create-pet.json"
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val step = result.ast!!.scenarios[0].steps[0]
        val callAction = step.actions.filterIsInstance<CallNode>().first()
        assertEquals("classpath:templates/create-pet.json", callAction.bodyFile)
    }

    @Test
    fun `should parse bodyFile with file prefix`() {
        val source =
            """
            scenario: File prefix test
              when I create a pet
                call ^createPet
                  bodyFile: "file:./templates/body.json"
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val step = result.ast!!.scenarios[0].steps[0]
        val callAction = step.actions.filterIsInstance<CallNode>().first()
        assertEquals("file:./templates/body.json", callAction.bodyFile)
    }

    @Test
    fun `should parse bodyFile with inline body having priority in step`() {
        // When both body and bodyFile are specified, body should be in the AST but bodyFile is also captured
        val source =
            """
            scenario: Both body types
              when I create a pet
                call ^createPet
                  body: {"name": "inline"}
            """.trimIndent()

        val result = Parser.parse(source)
        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")

        val step = result.ast!!.scenarios[0].steps[0]
        val callAction = step.actions.filterIsInstance<CallNode>().first()
        assertNotNull(callAction.body)
        // Body takes precedence, bodyFile should be null
        assertEquals(null, callAction.bodyFile)
    }

    // =========================================================================
    // Assertion Error Case Tests
    // Tests for strict assertion parsing that should fail fast
    // =========================================================================

    @Test
    fun `should fail on unknown assertion type`() {
        val source =
            """
            scenario: Unknown assertion type
              then: check something
                assert foo "bar"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(!result.isSuccess || result.errors.isNotEmpty(), "Parse should fail for unknown assertion type")
        assertTrue(result.errors.any { it.message.contains("Unknown assertion type") })
    }

    @Test
    fun `should fail on unknown JSON path action`() {
        val source =
            """
            scenario: Unknown JSON path action
              then: check something
                assert $.name invalid "value"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(!result.isSuccess || result.errors.isNotEmpty(), "Parse should fail for unknown JSON path action")
        assertTrue(
            result.errors.any { it.message.contains("Unknown assertion action") },
            "Error should mention 'Unknown assertion action', got: ${result.errors}",
        )
    }

    @Test
    fun `should parse negated contains assertion correctly`() {
        val source =
            """
            scenario: Negated contains
              then: check not contains
                assert not contains "error"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(1, assertions.size)
        assertEquals(AssertionKind.BODY_CONTAINS, assertions[0].assertionType)
        assertTrue(assertions[0].negate, "Assertion should have negate=true")
    }

    @Test
    fun `should parse negated equals assertion with not after path`() {
        val source =
            """
            scenario: Negated equals
              then: check not equals
                assert $.status not equals "error"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(1, assertions.size)
        assertEquals(AssertionKind.BODY_EQUALS, assertions[0].assertionType)
        assertTrue(assertions[0].negate, "Assertion should have negate=true")
    }

    @Test
    fun `should parse structured body with properties`() {
        val source =
            """
            scenario: Create pet with structured body
              when I create a pet
                call ^createPet
                  body:
                    name: Fluffy
                    status: available
                    category: dog
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val scenario = result.ast!!.scenarios[0]
        val callAction =
            scenario.steps[0]
                .actions
                .filterIsInstance<CallNode>()
                .firstOrNull()
        assertNotNull(callAction, "Should have call action")
        assertNotNull(callAction.bodyProperties, "Should have body properties")
        assertEquals(3, callAction.bodyProperties.size)
        assertTrue(callAction.bodyProperties.containsKey("name"))
        assertTrue(callAction.bodyProperties.containsKey("status"))
        assertTrue(callAction.bodyProperties.containsKey("category"))
    }

    @Test
    fun `should parse nested body properties`() {
        val source =
            """
            scenario: Create pet with nested body
              when I create a pet
                call ^createPet
                  body:
                    name: Fluffy
                    metadata:
                      source: test
                      version: 1.0
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val scenario = result.ast!!.scenarios[0]
        val callAction =
            scenario.steps[0]
                .actions
                .filterIsInstance<CallNode>()
                .firstOrNull()
        assertNotNull(callAction, "Should have call action")
        assertNotNull(callAction.bodyProperties, "Should have body properties")
        assertEquals(2, callAction.bodyProperties.size)

        val metadata = callAction.bodyProperties["metadata"]
        assertTrue(
            metadata is BodyPropertyValue.Nested,
            "Metadata should be nested",
        )
        val nested = metadata.properties
        assertTrue(nested.containsKey("source"))
        assertTrue(nested.containsKey("version"))
    }

    @Test
    fun `should parse triple-quoted multi-line body`() {
        val source =
            """
            scenario: Create pet with multi-line body
              when I create a pet
                call ^createPet
                  body:
                    ${"\"\"\""}
                    {
                      "name": "Fluffy",
                      "status": "available"
                    }
                    ${"\"\"\""}
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val scenario = result.ast!!.scenarios[0]
        val callAction =
            scenario.steps[0]
                .actions
                .filterIsInstance<CallNode>()
                .firstOrNull()
        assertNotNull(callAction, "Should have call action")
        assertNotNull(callAction.body, "Should have body")

        val body =
            when (val b = callAction.body) {
                is StringValueNode -> b.value
                is JsonValueNode -> b.json
                else -> null
            }
        assertNotNull(body, "Body value should not be null")
        assertTrue(body.contains("\"name\": \"Fluffy\""), "Body should contain name")
        assertTrue(
            body.contains("\"status\": \"available\""),
            "Body should contain status",
        )
    }

    @Test
    fun `should parse scenario after triple-quoted body`() {
        val source =
            """
            scenario: First scenario
              when I create a pet
                call ^createPet
                  body:
                    ${"\"\"\""}
                    {"name": "Test"}
                    ${"\"\"\""}
              then the pet is created
                assert status 201

            scenario: Second scenario
              when I list pets
                call ^listPets
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertEquals(2, result.ast!!.scenarios.size)
        assertEquals("First scenario", result.ast.scenarios[0].name)
        assertEquals("Second scenario", result.ast.scenarios[1].name)
    }

    /**
     * Helper function to extract all AssertNode from a scenario.
     */
    private fun extractAssertions(scenario: ScenarioNode): List<AssertNode> =
        scenario.steps.flatMap { step ->
            step.actions.filterIsInstance<AssertNode>()
        }

    // =========================================================================
    // Conditional Assertion Tests
    // Tests for if/else if/else/fail parsing
    // =========================================================================

    @Test
    fun `should parse simple if conditional`() {
        val source =
            """
            scenario: Simple conditional
              when I create a pet
                call ^createPet
                if status 201
                  assert $.status equals "available"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val conditionals = extractConditionals(result.ast!!.scenarios[0])
        assertEquals(1, conditionals.size)

        val conditional = conditionals[0]
        assertTrue(conditional.ifBranch.condition is ConditionNode.StatusCondition)
        assertEquals(1, conditional.ifBranch.actions.size)
        assertTrue(conditional.ifBranch.actions[0] is AssertNode)
    }

    @Test
    fun `should parse if-else conditional`() {
        val source =
            """
            scenario: If-else conditional
              when I create a pet
                call ^createPet
                if status 201
                  assert $.status equals "available"
                else
                  fail "Expected status 201"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val conditionals = extractConditionals(result.ast!!.scenarios[0])
        assertEquals(1, conditionals.size)

        val conditional = conditionals[0]
        assertNotNull(conditional.elseActions)
        assertTrue(conditional.elseActions.isNotEmpty())
        assertTrue(conditional.elseActions[0] is FailNode)
    }

    @Test
    fun `should parse if-else if-else conditional`() {
        val source =
            """
            scenario: Full conditional
              when I create a pet
                call ^createPet
                if status 201
                  assert $.status equals "created"
                else if status 200
                  assert $.status equals "exists"
                else
                  fail "Unexpected status"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val conditionals = extractConditionals(result.ast!!.scenarios[0])
        assertEquals(1, conditionals.size)

        val conditional = conditionals[0]
        assertEquals(1, conditional.elseIfBranches.size)
        assertTrue(conditional.elseIfBranches[0].condition is ConditionNode.StatusCondition)
        assertNotNull(conditional.elseActions)
    }

    @Test
    fun `should parse conditional with multiple assertions`() {
        val source =
            """
            scenario: Multiple assertions in conditional
              when I create a pet
                call ^createPet
                if status 201
                  assert $.status equals "available"
                  assert $.id notEmpty
                  extract $.id => petId
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val conditionals = extractConditionals(result.ast!!.scenarios[0])
        assertEquals(1, conditionals.size)

        val conditional = conditionals[0]
        assertEquals(
            2,
            conditional.ifBranch.actions
                .filterIsInstance<AssertNode>()
                .size,
        )
        assertEquals(
            1,
            conditional.ifBranch.actions
                .filterIsInstance<ExtractNode>()
                .size,
        )
    }

    @Test
    fun `should parse conditional with json path condition`() {
        val source =
            """
            scenario: JSON path condition
              when I get a pet
                call ^getPetById
                if $.status equals "active"
                  assert $.available equals true
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val conditionals = extractConditionals(result.ast!!.scenarios[0])
        assertEquals(1, conditionals.size)

        val condition = conditionals[0].ifBranch.condition
        assertTrue(condition is ConditionNode.JsonPathCondition)
        assertEquals("$.status", condition.path)
        assertEquals(ConditionOperator.EQUALS, condition.operator)
    }

    @Test
    fun `should parse conditional with header condition`() {
        val source =
            """
            scenario: Header condition
              when I get a resource
                call ^getResource
                if header Content-Type equals "application/json"
                  assert $.type equals "json"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val conditionals = extractConditionals(result.ast!!.scenarios[0])
        assertEquals(1, conditionals.size)

        val condition = conditionals[0].ifBranch.condition
        assertTrue(condition is ConditionNode.HeaderCondition)
        assertEquals("Content-Type", condition.headerName)
    }

    @Test
    fun `should parse standalone fail action`() {
        val source =
            """
            scenario: Fail test
              then: this should fail
                fail "This test is designed to fail"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val failNodes =
            result.ast!!
                .scenarios[0]
                .steps
                .flatMap { it.actions.filterIsInstance<FailNode>() }
        assertEquals(1, failNodes.size)
        assertEquals("This test is designed to fail", failNodes[0].message)
    }

    @Test
    fun `should parse fail with unquoted message`() {
        val source =
            """
            scenario: Fail without quotes
              then: this should fail
                fail status must be 200 or 201
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val failNodes =
            result.ast!!
                .scenarios[0]
                .steps
                .flatMap { it.actions.filterIsInstance<FailNode>() }
        assertEquals(1, failNodes.size)
        assertEquals("status must be 200 or 201", failNodes[0].message)
    }

    /**
     * Helper function to extract all ConditionalNode from a scenario.
     */
    private fun extractConditionals(scenario: ScenarioNode): List<ConditionalNode> =
        scenario.steps.flatMap { step ->
            step.actions.filterIsInstance<ConditionalNode>()
        }

    @Test
    fun `should parse status range patterns (1xx to 5xx)`() {
        val patterns = listOf("1xx" to 1, "2xx" to 2, "3xx" to 3, "4xx" to 4, "5xx" to 5)

        for ((pattern, base) in patterns) {
            val source =
                """
                scenario: Test status range $pattern
                  when I make a request
                    call ^testOperation
                  then I get a $pattern response
                    assert status $pattern
                """.trimIndent()

            val result = Parser.parse(source)

            assertTrue(result.isSuccess, "Parse should succeed for pattern $pattern: ${result.errors}")
            val assertions = extractAssertions(result.ast!!.scenarios[0])
            assertEquals(1, assertions.size)
            val assertion = assertions[0]
            assertEquals(AssertionKind.STATUS_CODE, assertion.assertionType)
            assertTrue(assertion.expected is StatusRangeNode, "Expected StatusRangeNode for pattern $pattern")
            assertEquals(base, assertion.expected.base)
        }
    }

    @Test
    fun `should parse uppercase status range patterns`() {
        val source =
            """
            scenario: Test uppercase pattern
              when I make a request
                call ^testOperation
              then I get a 4XX response
                assert status 4XX
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertTrue(assertions[0].expected is StatusRangeNode)
        assertEquals(4, (assertions[0].expected as StatusRangeNode).base)
    }

    @Test
    fun `should still parse exact status codes`() {
        val source =
            """
            scenario: Test exact status
              when I make a request
                call ^testOperation
              then I get a 201 response
                assert status 201
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertTrue(assertions[0].expected is NumberValueNode, "Expected NumberValueNode for exact status")
        assertEquals(201L, (assertions[0].expected as NumberValueNode).value)
    }

    @Test
    fun `should convert status range to IntRange`() {
        val node2xx = StatusRangeNode(2, SourceLocation(1, 1, null))
        assertEquals(200..299, node2xx.toRange())

        val node4xx = StatusRangeNode(4, SourceLocation(1, 1, null))
        assertEquals(400..499, node4xx.toRange())
    }

    // Variable Condition Tests

    @Test
    fun `should parse variable condition`() {
        val source =
            """
            scenario: Variable condition test
              when I make a request
                call ^testOperation
              then check variable
                if test.type equals "invalid"
                  fail "Expected invalid test"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val conditionals = extractConditionals(result.ast!!.scenarios[0])
        assertEquals(1, conditionals.size)

        val conditional = conditionals[0]
        assertTrue(conditional.ifBranch.condition is ConditionNode.VariableCondition)
        val varCondition = conditional.ifBranch.condition
        assertEquals("test.type", varCondition.variableName)
        assertEquals(ConditionOperator.EQUALS, varCondition.operator)
    }

    @Test
    fun `should parse compound condition with and`() {
        val source =
            """
            scenario: Compound AND test
              when I make a request
                call ^testOperation
              then check compound
                if status 4xx and test.type equals "invalid"
                  fail "Got 4xx with invalid test type"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val conditionals = extractConditionals(result.ast!!.scenarios[0])
        assertEquals(1, conditionals.size)

        val conditional = conditionals[0]
        assertTrue(conditional.ifBranch.condition is ConditionNode.CompoundCondition)
        val compound = conditional.ifBranch.condition
        assertEquals(LogicalOperator.AND, compound.operator)
        assertTrue(compound.left is ConditionNode.StatusCondition)
        assertTrue(compound.right is ConditionNode.VariableCondition)
    }

    @Test
    fun `should parse compound condition with or`() {
        val source =
            """
            scenario: Compound OR test
              when I make a request
                call ^testOperation
              then check compound or
                if status 200 or status 201
                  assert status 200
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val conditionals = extractConditionals(result.ast!!.scenarios[0])
        assertEquals(1, conditionals.size)

        val conditional = conditionals[0]
        assertTrue(conditional.ifBranch.condition is ConditionNode.CompoundCondition)
        val compound = conditional.ifBranch.condition
        assertEquals(LogicalOperator.OR, compound.operator)
        assertTrue(compound.left is ConditionNode.StatusCondition)
        assertTrue(compound.right is ConditionNode.StatusCondition)
    }

    @Test
    fun `should parse negated variable condition`() {
        val source =
            """
            scenario: Negated variable test
              when I make a request
                call ^testOperation
              then check negated
                if not test.valid equals "true"
                  fail "Test is not valid"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val conditionals = extractConditionals(result.ast!!.scenarios[0])
        assertEquals(1, conditionals.size)

        val conditional = conditionals[0]
        assertTrue(conditional.ifBranch.condition is ConditionNode.NegatedCondition)
        val negated = conditional.ifBranch.condition
        assertTrue(negated.condition is ConditionNode.VariableCondition)
    }

    @Test
    fun `should parse complex compound with multiple operators`() {
        val source =
            """
            scenario: Complex compound test
              when I make a request
                call ^testOperation
              then check complex
                if status 4xx and test.type equals "invalid"
                  fail "Test should fail for 4xx with invalid type"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val conditionals = extractConditionals(result.ast!!.scenarios[0])
        assertEquals(1, conditionals.size)

        val conditional = conditionals[0]
        assertTrue(conditional.ifBranch.condition is ConditionNode.CompoundCondition)
    }

    // Auto-test excludes tests

    @Test
    fun `should parse auto-test with excludes`() {
        val source =
            """
            scenario: Auto-test with excludes
              when I make a request
                call ^createPet
                  auto: [invalid security]
                  excludes: [SQLInjection maxLength]
                  body:
                    name: "Test"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val callNode = step.actions.first() as CallNode
        assertNotNull(callNode.autoTestConfig)
        assertEquals(setOf(AutoTestType.INVALID, AutoTestType.SECURITY), callNode.autoTestConfig.types)
        assertEquals(setOf("SQLInjection", "maxLength"), callNode.autoTestConfig.excludes)
    }

    @Test
    fun `should parse auto-test excludes with comma separation`() {
        val source =
            """
            scenario: Auto-test with comma excludes
              when I test
                call ^createPet
                  auto: [security]
                  excludes: [XSS, PathTraversal, CommandInjection]
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val callNode = step.actions.first() as CallNode
        assertNotNull(callNode.autoTestConfig)
        assertEquals(setOf("XSS", "PathTraversal", "CommandInjection"), callNode.autoTestConfig.excludes)
    }

    @Test
    fun `should parse auto-test without excludes`() {
        val source =
            """
            scenario: Auto-test without excludes
              when I test
                call ^createPet
                  auto: [invalid]
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val callNode = step.actions.first() as CallNode
        assertNotNull(callNode.autoTestConfig)
        assertEquals(setOf(AutoTestType.INVALID), callNode.autoTestConfig.types)
        assertEquals(emptySet(), callNode.autoTestConfig.excludes)
    }
}
