package org.berrycrush.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParserTest {
    // =========================================================================
    // Helper functions for working with new ConditionNode-based AssertNode
    // =========================================================================

    /**
     * Check if an assertion's condition is a StatusCondition.
     */
    private fun AssertNode.isStatusAssertion(): Boolean = unwrapCondition() is ConditionNode.StatusCondition

    /**
     * Check if an assertion's condition is a JsonPathCondition.
     */
    private fun AssertNode.isJsonPathAssertion(): Boolean = unwrapCondition() is ConditionNode.JsonPathCondition

    /**
     * Check if an assertion's condition is a HeaderCondition.
     */
    private fun AssertNode.isHeaderAssertion(): Boolean = unwrapCondition() is ConditionNode.HeaderCondition

    /**
     * Check if an assertion's condition is a BodyContainsCondition.
     */
    private fun AssertNode.isBodyContainsAssertion(): Boolean = unwrapCondition() is ConditionNode.BodyContainsCondition

    /**
     * Check if an assertion's condition is a SchemaCondition.
     */
    private fun AssertNode.isSchemaAssertion(): Boolean = unwrapCondition() is ConditionNode.SchemaCondition

    /**
     * Check if an assertion's condition is a ResponseTimeCondition.
     */
    private fun AssertNode.isResponseTimeAssertion(): Boolean = unwrapCondition() is ConditionNode.ResponseTimeCondition

    /**
     * Check if an assertion's condition is a CustomAssertionCondition.
     */
    private fun AssertNode.isCustomAssertion(): Boolean = unwrapCondition() is ConditionNode.CustomAssertionCondition

    /**
     * Get the pattern from a CustomAssertionCondition assertion.
     */
    private fun AssertNode.getCustomPattern(): String? =
        (unwrapCondition() as? ConditionNode.CustomAssertionCondition)?.pattern

    /**
     * Check if an assertion is negated (wrapped in NegatedCondition).
     */
    private fun AssertNode.isNegated(): Boolean = condition is ConditionNode.NegatedCondition

    /**
     * Unwrap NegatedCondition to get the inner condition.
     */
    private fun AssertNode.unwrapCondition(): ConditionNode =
        when (val c = condition) {
            is ConditionNode.NegatedCondition -> c.condition
            else -> c
        }

    /**
     * Get the JSON path from a JsonPathCondition assertion.
     */
    private fun AssertNode.getJsonPath(): String? = (unwrapCondition() as? ConditionNode.JsonPathCondition)?.path

    /**
     * Get the header name from a HeaderCondition assertion.
     */
    private fun AssertNode.getHeaderName(): String? = (unwrapCondition() as? ConditionNode.HeaderCondition)?.headerName

    /**
     * Get the expected value from the condition.
     */
    private fun AssertNode.getExpected(): ValueNode? =
        when (val c = unwrapCondition()) {
            is ConditionNode.StatusCondition -> c.expected
            is ConditionNode.JsonPathCondition -> c.expected
            is ConditionNode.HeaderCondition -> c.expected
            is ConditionNode.BodyContainsCondition -> c.text
            is ConditionNode.ResponseTimeCondition -> c.maxMs
            else -> null
        }

    /**
     * Get the operator from a JsonPathCondition assertion.
     */
    private fun AssertNode.getOperator(): ConditionOperator? = (unwrapCondition() as? ConditionNode.JsonPathCondition)?.operator

    /**
     * Check if JSON path assertion has HAS_SIZE operator.
     */
    private fun AssertNode.isArraySizeAssertion(): Boolean {
        val c = unwrapCondition()
        return c is ConditionNode.JsonPathCondition && c.operator == ConditionOperator.HAS_SIZE
    }

    /**
     * Check if JSON path assertion has NOT_EMPTY operator.
     */
    private fun AssertNode.isArrayNotEmptyAssertion(): Boolean {
        val c = unwrapCondition()
        return c is ConditionNode.JsonPathCondition && c.operator == ConditionOperator.NOT_EMPTY
    }

    /**
     * Check if JSON path assertion has MATCHES operator.
     */
    private fun AssertNode.isMatchesAssertion(): Boolean {
        val c = unwrapCondition()
        return c is ConditionNode.JsonPathCondition && c.operator == ConditionOperator.MATCHES
    }

    /**
     * Check if header assertion has EXISTS operator.
     */
    private fun AssertNode.isHeaderExistsAssertion(): Boolean {
        val c = unwrapCondition()
        return c is ConditionNode.HeaderCondition && c.operator == ConditionOperator.EXISTS
    }

    /**
     * Check if header assertion has EQUALS operator.
     */
    private fun AssertNode.isHeaderEqualsAssertion(): Boolean {
        val c = unwrapCondition()
        return c is ConditionNode.HeaderCondition && c.operator == ConditionOperator.EQUALS
    }

    // =========================================================================
    // Basic Parsing Tests
    // =========================================================================

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
    fun `should parse feature with parameters`() {
        val source =
            """
            feature: Pet CRUD Operations
              parameters:
                shareVariablesAcrossScenarios: true
                timeout: 60

              background:
                given: setup
                  call ^setupPet

              scenario: create pet
                when: create
                  call ^createPet
                  assert status 201

              scenario: use shared variable
                when: get
                  call ^getPet
                  assert status 200
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertEquals(1, result.ast!!.features.size)

        val feature = result.ast.features[0]
        assertEquals("Pet CRUD Operations", feature.name)
        assertNotNull(feature.parameters, "Feature should have parameters")
        assertEquals(true, feature.parameters!!.values["shareVariablesAcrossScenarios"])
        assertEquals(60L, feature.parameters!!.values["timeout"])
        assertNotNull(feature.background)
        assertEquals(2, feature.scenarios.size)
    }

    @Test
    fun `should parse feature with parameters only (no background)`() {
        val source =
            """
            feature: Simple Feature
              parameters:
                shareVariablesAcrossScenarios: true

              scenario: test scenario
                when: test
                  call ^test
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertEquals(1, result.ast!!.features.size)

        val feature = result.ast.features[0]
        assertEquals("Simple Feature", feature.name)
        assertNotNull(feature.parameters)
        assertEquals(true, feature.parameters!!.values["shareVariablesAcrossScenarios"])
        assertEquals(null, feature.background)
        assertEquals(1, feature.scenarios.size)
    }

    @Test
    fun `should parse feature without parameters`() {
        val source =
            """
            feature: No Params Feature
              scenario: test scenario
                when: test
                  call ^test
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        assertEquals(1, result.ast!!.features.size)

        val feature = result.ast.features[0]
        assertEquals(null, feature.parameters)
        assertEquals(1, feature.scenarios.size)
    }

    @Test
    fun `should report error when feature parameters appear after scenarios`() {
        val source =
            """
            feature: Bad Feature
              scenario: test scenario
                when: test
                  call ^test

              parameters:
                shareVariablesAcrossScenarios: true
            """.trimIndent()

        val result = Parser.parse(source)

        // Should have a parse error about parameters after scenarios
        assertTrue(result.errors.any { it.message.contains("parameters") && it.message.contains("before") })
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
        assertTrue(assertions[0].isStatusAssertion(), "Should be a status assertion")
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
        assertTrue(assertions[0].isStatusAssertion(), "Should be a status assertion")
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
        assertTrue(assertions[0].isBodyContainsAssertion(), "Should be a body contains assertion")
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
        assertTrue(assertions[0].isBodyContainsAssertion(), "Should be a body contains assertion")
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
        assertTrue(assertions[0].isJsonPathAssertion(), "Should be a JSON path assertion")
        assertEquals("$.name", assertions[0].getJsonPath())
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
        assertTrue(assertions[0].isJsonPathAssertion(), "Should be a JSON path assertion")
        assertEquals("$.id", assertions[0].getJsonPath())
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
        assertTrue(assertions[0].isMatchesAssertion(), "Should be a matches assertion")
        assertEquals("$.email", assertions[0].getJsonPath())
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
        assertTrue(assertions[0].isArraySizeAssertion(), "Should be an array size assertion")
        assertEquals("$.items", assertions[0].getJsonPath())
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
        assertTrue(assertions[0].isArraySizeAssertion(), "Should be an array size assertion")
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
        assertTrue(assertions[0].isArrayNotEmptyAssertion(), "Should be an array notEmpty assertion")
        assertEquals("$.results", assertions[0].getJsonPath())
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
        assertTrue(assertions[0].isHeaderExistsAssertion(), "Should be a header exists assertion")
        assertEquals("X-Request-Id", assertions[0].getHeaderName())
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
        assertTrue(assertions[0].isHeaderEqualsAssertion(), "Should be a header equals assertion")
        assertEquals("Content-Type", assertions[0].getHeaderName())
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
        assertTrue(assertions[0].isHeaderEqualsAssertion(), "Should be a header equals assertion")
        assertEquals("Accept", assertions[0].getHeaderName())
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
        assertTrue(assertions[0].isSchemaAssertion(), "Should be a schema assertion")
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
        assertTrue(assertions[0].isSchemaAssertion(), "Should be a schema assertion")
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
        assertTrue(assertions[0].isResponseTimeAssertion(), "Should be a response time assertion")
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
        assertTrue(assertions[0].isStatusAssertion(), "First should be status assertion")
        assertTrue(assertions[1].isJsonPathAssertion(), "Second should be JSON path assertion")
        assertTrue(assertions[2].isArrayNotEmptyAssertion(), "Third should be array notEmpty assertion")
        assertTrue(assertions[3].isHeaderEqualsAssertion(), "Fourth should be header equals assertion")
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
    fun `should parse unknown assertion type as custom assertion`() {
        val source =
            """
            scenario: Custom assertion pattern
              then: check something
                assert foo "bar"
            """.trimIndent()

        val result = Parser.parse(source)

        // Unknown assertion types are now treated as custom assertion patterns
        assertTrue(result.isSuccess, "Parse should succeed for custom assertion pattern: ${result.errors}")
        assertEquals(1, result.ast!!.scenarios.size)
        
        // Verify the assertion is captured as custom assertion
        val assertions = extractAssertions(result.ast.scenarios[0])
        assertEquals(1, assertions.size)
        assertTrue(assertions[0].isCustomAssertion(), "Should be a custom assertion")
        // Pattern preserves quotes for string tokens so custom assertion matchers can extract parameters
        assertEquals("foo \"bar\"", assertions[0].getCustomPattern())
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
        assertTrue(assertions[0].isBodyContainsAssertion(), "Should be a body contains assertion")
        assertTrue(assertions[0].isNegated(), "Assertion should be negated")
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
        assertTrue(assertions[0].isJsonPathAssertion(), "Should be a JSON path assertion")
        assertTrue(assertions[0].isNegated(), "Assertion should be negated")
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
            assertTrue(assertion.isStatusAssertion(), "Should be a status assertion for pattern $pattern")
            val expected = assertion.getExpected()
            assertTrue(expected is StatusRangeNode, "Expected StatusRangeNode for pattern $pattern")
            assertEquals(base, expected.base)
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
        val expected = assertions[0].getExpected()
        assertTrue(expected is StatusRangeNode)
        assertEquals(4, expected.base)
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
        val expected = assertions[0].getExpected()
        assertTrue(expected is NumberValueNode, "Expected NumberValueNode for exact status")
        assertEquals(201L, expected.value)
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

    // =========================================================================
    // Shared Condition Tests - if and assert use same condition types
    // =========================================================================

    @Test
    fun `should parse status condition shared between if and assert`() {
        val source =
            """
            scenario: Shared status condition
              when I make a request
                call ^listPets
                assert status 200
                if status 200
                  assert $.items notEmpty
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]

        // Check assert has status condition
        val assertNode = step.actions[1] as AssertNode
        assertTrue(assertNode.isStatusAssertion())

        // Check if has same status condition
        val conditional = step.actions[2] as ConditionalNode
        assertTrue(conditional.ifBranch.condition is ConditionNode.StatusCondition)
        val statusCond = conditional.ifBranch.condition
        assertEquals(200.toLong(), (statusCond.expected as NumberValueNode).value)
    }

    @Test
    fun `should parse jsonpath condition shared between if and assert`() {
        val source =
            """
            scenario: Shared jsonpath condition
              when I check status
                call ^getPet
                assert $.status equals "active"
                if $.status equals "active"
                  assert $.id notEmpty
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]

        // Check assert jsonpath condition
        val assertNode = step.actions[1] as AssertNode
        assertTrue(assertNode.isJsonPathAssertion())
        assertEquals("$.status", assertNode.getJsonPath())
        assertEquals(ConditionOperator.EQUALS, assertNode.getOperator())

        // Check if has same jsonpath condition
        val conditional = step.actions[2] as ConditionalNode
        assertTrue(conditional.ifBranch.condition is ConditionNode.JsonPathCondition)
        val jsonCond = conditional.ifBranch.condition
        assertEquals("$.status", jsonCond.path)
        assertEquals(ConditionOperator.EQUALS, jsonCond.operator)
    }

    @Test
    fun `should parse header condition shared between if and assert`() {
        val source =
            """
            scenario: Shared header condition
              when I check content type
                call ^getPet
                assert header Content-Type equals "application/json"
                if header Content-Type equals "application/json"
                  assert $.body notEmpty
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]

        // Check assert header condition
        val assertNode = step.actions[1] as AssertNode
        assertTrue(assertNode.isHeaderAssertion())
        assertEquals("Content-Type", assertNode.getHeaderName())

        // Check if has same header condition
        val conditional = step.actions[2] as ConditionalNode
        assertTrue(conditional.ifBranch.condition is ConditionNode.HeaderCondition)
        val headerCond = conditional.ifBranch.condition
        assertEquals("Content-Type", headerCond.headerName)
        assertEquals(ConditionOperator.EQUALS, headerCond.operator)
    }

    // =========================================================================
    // Response Time Assertion Tests
    // =========================================================================

    @Test
    fun `should parse responseTime lessThan assertion condition`() {
        val source =
            """
            scenario: Response time check
              when I get pets
                call ^listPets
                assert responseTime 1000
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val assertNode = step.actions[1] as AssertNode
        assertTrue(assertNode.isResponseTimeAssertion())
        val cond = assertNode.unwrapCondition() as ConditionNode.ResponseTimeCondition
        assertEquals(1000.toLong(), (cond.maxMs as NumberValueNode).value)
    }

    @Test
    fun `should parse responseTime greaterThan assertion condition`() {
        val source =
            """
            scenario: Response time minimum check
              when I get pets slowly
                call ^listPets
                assert responseTime 100
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val assertNode = step.actions[1] as AssertNode
        assertTrue(assertNode.isResponseTimeAssertion())
    }

    // =========================================================================
    // Negation Edge Case Tests
    // =========================================================================

    @Test
    fun `should parse negated body contains assertion condition`() {
        val source =
            """
            scenario: Negated body contains
              when I get data
                call ^getData
                assert not contains "error"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val assertNode = step.actions[1] as AssertNode
        assertTrue(assertNode.isNegated())
        assertTrue(assertNode.isBodyContainsAssertion())
    }

    @Test
    fun `should parse negation in if condition branch`() {
        val source =
            """
            scenario: Negated condition in if
              when I check
                call ^check
                if not status 200
                  fail "Expected 200"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val conditional = step.actions[1] as ConditionalNode
        assertTrue(conditional.ifBranch.condition is ConditionNode.NegatedCondition)
        val negated = conditional.ifBranch.condition
        assertTrue(negated.condition is ConditionNode.StatusCondition)
    }

    @Test
    fun `should parse negation after jsonpath in assert`() {
        val source =
            """
            scenario: Negation after jsonpath
              when I check status
                call ^getStatus
                assert $.status not equals "deleted"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val assertNode = step.actions[1] as AssertNode
        assertTrue(assertNode.isNegated())
        assertTrue(assertNode.isJsonPathAssertion())
    }

    // =========================================================================
    // Header Operator Tests (extended)
    // =========================================================================

    @Test
    fun `should parse header exists assertion with explicit exists keyword`() {
        val source =
            """
            scenario: Header exists check with keyword
              when I get resource
                call ^getResource
                assert header X-Request-Id exists
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val assertNode = step.actions[1] as AssertNode
        assertTrue(assertNode.isHeaderExistsAssertion())
        assertEquals("X-Request-Id", assertNode.getHeaderName())
    }

    @Test
    fun `should parse header equals with colon shorthand`() {
        val source =
            """
            scenario: Header equals check
              when I check cache
                call ^getData
                assert header Cache-Control: "no-cache"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val assertNode = step.actions[1] as AssertNode
        assertTrue(assertNode.isHeaderAssertion(), "Expected header assertion")
        val cond = assertNode.unwrapCondition()
        assertTrue(cond is ConditionNode.HeaderCondition, "Expected HeaderCondition, got ${cond::class.simpleName}")
        assertEquals(ConditionOperator.EQUALS, cond.operator)
    }

    // =========================================================================
    // Combined Assertion Tests
    // =========================================================================

    @Test
    fun `should parse multiple different assertion types in one step`() {
        val source =
            """
            scenario: Multiple assertions
              when I create a pet
                call ^createPet
                assert status 201
                assert $.id exists
                assert header Location exists
                assert contains "created"
                assert schema
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        assertEquals(6, step.actions.size) // call + 5 assertions

        val assertions = step.actions.drop(1).filterIsInstance<AssertNode>()
        assertEquals(5, assertions.size)

        assertTrue(assertions[0].isStatusAssertion())
        assertTrue(assertions[1].isJsonPathAssertion())
        assertTrue(assertions[2].isHeaderExistsAssertion())
        assertTrue(assertions[3].isBodyContainsAssertion())
        assertTrue(assertions[4].isSchemaAssertion())
    }

    @Test
    fun `should parse hasSize operator in jsonpath assertion`() {
        val source =
            """
            scenario: Array size check
              when I list items
                call ^listItems
                assert $.items hasSize 5
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val assertNode = step.actions[1] as AssertNode
        assertTrue(assertNode.isArraySizeAssertion())
        assertEquals(5.toLong(), (assertNode.getExpected() as NumberValueNode).value)
    }

    @Test
    fun `should parse status range 2xx in assertion`() {
        val source =
            """
            scenario: Status range check
              when I make a request
                call ^api
                assert status 2xx
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val assertNode = step.actions[1] as AssertNode
        assertTrue(assertNode.isStatusAssertion())
        val cond = assertNode.unwrapCondition() as ConditionNode.StatusCondition
        assertTrue(cond.expected is StatusRangeNode)
        assertEquals(2, cond.expected.base)
    }

    // =========================================================================
    // Unified Condition Tests - all condition types available in both if and assert
    // =========================================================================

    @Test
    fun `should parse schema condition in if statement`() {
        val source =
            """
            scenario: Schema in if
              when I check response
                call ^getData
                if schema
                  assert $.valid equals true
                else
                  fail "Response does not match schema"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val conditional = step.actions[1] as ConditionalNode
        assertTrue(conditional.ifBranch.condition is ConditionNode.SchemaCondition)
    }

    @Test
    fun `should parse contains condition in if statement`() {
        val source =
            """
            scenario: Contains in if
              when I check response
                call ^getData
                if contains "success"
                  assert $.status equals "ok"
                else
                  fail "Expected success message"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val conditional = step.actions[1] as ConditionalNode
        assertTrue(conditional.ifBranch.condition is ConditionNode.BodyContainsCondition)
    }

    @Test
    fun `should parse responseTime condition in if statement`() {
        val source =
            """
            scenario: ResponseTime in if
              when I check performance
                call ^getData
                if responseTime 1000
                  assert $.cached equals true
                else
                  assert $.cached equals false
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val step = result.ast!!.scenarios[0].steps[0]
        val conditional = step.actions[1] as ConditionalNode
        assertTrue(conditional.ifBranch.condition is ConditionNode.ResponseTimeCondition)
    }

    // =========================================================================
    // Custom Assertion Tests
    // Tests for custom assertion patterns (domain-specific assertions)
    // =========================================================================

    @Test
    fun `should parse custom assertion with multi-word pattern`() {
        val source =
            """
            scenario: Custom multi-word assertion
              then: check custom rule
                assert the user is authenticated
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(1, assertions.size)
        assertTrue(assertions[0].isCustomAssertion(), "Should be a custom assertion")
        assertEquals("the user is authenticated", assertions[0].getCustomPattern())
    }

    @Test
    fun `should parse custom assertion with variable placeholder`() {
        val source =
            """
            scenario: Custom assertion with variable
              then: check ownership
                assert {{userId}} owns the resource
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertTrue(assertions[0].isCustomAssertion(), "Should be a custom assertion")
        // Variable tokens capture just the name without {{}}
        assertEquals("userId owns the resource", assertions[0].getCustomPattern())
    }

    @Test
    fun `should parse custom assertion with numeric values`() {
        val source =
            """
            scenario: Custom numeric assertion
              then: check count
                assert item count equals 5
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertTrue(assertions[0].isCustomAssertion(), "Should be a custom assertion")
        assertEquals("item count equals 5", assertions[0].getCustomPattern())
    }

    @Test
    fun `should parse multiple custom assertions in same step`() {
        val source =
            """
            scenario: Multiple custom assertions
              then: verify all rules
                assert the user is active
                assert permissions include admin
                assert quota is not exceeded
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(3, assertions.size)

        assertTrue(assertions[0].isCustomAssertion())
        assertEquals("the user is active", assertions[0].getCustomPattern())

        assertTrue(assertions[1].isCustomAssertion())
        assertEquals("permissions include admin", assertions[1].getCustomPattern())

        assertTrue(assertions[2].isCustomAssertion())
        assertEquals("quota is not exceeded", assertions[2].getCustomPattern())
    }

    @Test
    fun `should parse custom assertion mixed with standard assertions`() {
        val source =
            """
            scenario: Mixed assertions
              then: check everything
                assert status 200
                assert the user is authenticated
                assert $.name equals "Test"
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(3, assertions.size)

        assertTrue(assertions[0].isStatusAssertion(), "First should be status assertion")
        assertTrue(assertions[1].isCustomAssertion(), "Second should be custom assertion")
        assertEquals("the user is authenticated", assertions[1].getCustomPattern())
        assertTrue(assertions[2].isJsonPathAssertion(), "Third should be JSON path assertion")
    }

    @Test
    fun `should parse custom assertion in custom step without call`() {
        val source =
            """
            scenario: Custom step with custom assertion
              then I verify the business rule
                assert the order is valid
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(1, assertions.size)
        assertTrue(assertions[0].isCustomAssertion())
        assertEquals("the order is valid", assertions[0].getCustomPattern())
    }

    @Test
    fun `should parse custom assertion starting with known keyword but different context`() {
        val source =
            """
            scenario: Assertion resembling builtin but custom
              then: check custom
                assert response is cached locally
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertEquals(1, assertions.size)
        assertTrue(assertions[0].isCustomAssertion())
        assertEquals("response is cached locally", assertions[0].getCustomPattern())
    }

    @Test
    fun `should normalize whitespace in custom assertion patterns`() {
        val source =
            """
            scenario: Normalize spacing
              then: check
                assert total   equals   100
            """.trimIndent()

        val result = Parser.parse(source)

        assertTrue(result.isSuccess, "Parse should succeed: ${result.errors}")
        val assertions = extractAssertions(result.ast!!.scenarios[0])
        assertTrue(assertions[0].isCustomAssertion())
        // Tokens are joined with single spaces - whitespace is normalized
        assertEquals("total equals 100", assertions[0].getCustomPattern())
    }
}
