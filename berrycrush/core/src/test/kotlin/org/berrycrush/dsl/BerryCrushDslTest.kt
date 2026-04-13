package org.berrycrush.dsl

import org.berrycrush.model.Assertion
import org.berrycrush.model.Condition
import org.berrycrush.model.ConditionOperator
import org.berrycrush.model.StepType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BerryCrushDslTest {
    // Helper functions for checking assertion types
    private fun Assertion.isStatusAssertion(): Boolean =
        condition is Condition.Status || (condition is Condition.Negated && condition.condition is Condition.Status)

    private fun Assertion.isBodyContainsAssertion(): Boolean =
        condition is Condition.BodyContains ||
            (condition is Condition.Negated && condition.condition is Condition.BodyContains)

    private fun Assertion.isJsonPathAssertion(): Boolean {
        val c = if (condition is Condition.Negated) condition.condition else condition
        return c is Condition.JsonPath
    }

    private fun Assertion.isHeaderExistsAssertion(): Boolean {
        val c = if (condition is Condition.Negated) condition.condition else condition
        return c is Condition.Header && c.operator == ConditionOperator.EXISTS
    }

    @Test
    fun `should create suite with single spec`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val suite =
            berrycrush(specPath) {
                baseUrl = "https://api.example.com"
            }

        assertNotNull(suite)
        assertEquals("https://api.example.com", suite.configuration.baseUrl)
        assertTrue(suite.specRegistry.hasSpecs())
    }

    @Test
    fun `should create suite with multi-spec configuration`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val suite =
            berrycrush {
                spec("petstore", specPath) {
                    baseUrl = "https://petstore.example.com"
                }

                configure {
                    timeout(60)
                }
            }

        assertNotNull(suite)
        assertTrue(suite.specRegistry.specNames().contains("petstore"))
    }

    @Test
    fun `should define scenario with given-when-then`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val suite = berrycrush(specPath)

        val scenario =
            suite.scenario("List all pets") {
                given("the API is available") {
                    // No-op for this test
                }

                `when`("I request all pets") {
                    call("listPets")
                }

                then("I receive a list of pets") {
                    statusCode(200)
                    bodyContains("pets")
                }
            }

        assertEquals("List all pets", scenario.name)
        assertEquals(3, scenario.steps.size)
        assertEquals(StepType.GIVEN, scenario.steps[0].type)
        assertEquals(StepType.WHEN, scenario.steps[1].type)
        assertEquals(StepType.THEN, scenario.steps[2].type)
    }

    @Test
    fun `should build step with call and parameters`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val suite = berrycrush(specPath)

        val scenario =
            suite.scenario("Get pet by ID") {
                `when`("I fetch pet 123") {
                    call("getPetById") {
                        pathParam("petId", 123)
                        header("Accept", "application/json")
                    }
                }

                then("I get the pet") {
                    statusCode(200)
                }
            }

        val whenStep = scenario.steps[0]
        assertEquals("getPetById", whenStep.operationId)
        assertEquals(123, whenStep.pathParams["petId"])
        assertEquals("application/json", whenStep.headers["Accept"])
    }

    @Test
    fun `should build step with extraction`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val suite = berrycrush(specPath)

        val scenario =
            suite.scenario("Extract pet ID") {
                given("pets exist") {
                    call("listPets")
                    extractTo("firstPetId", "$.pets[0].id")
                }
            }

        val givenStep = scenario.steps[0]
        assertEquals(1, givenStep.extractions.size)
        assertEquals("firstPetId", givenStep.extractions[0].variableName)
        assertEquals("$.pets[0].id", givenStep.extractions[0].jsonPath)
    }

    @Test
    fun `should build step with multiple assertions`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val suite = berrycrush(specPath)

        val scenario =
            suite.scenario("Verify response") {
                then("verify all conditions") {
                    statusCode(200)
                    bodyContains("name")
                    bodyEquals("$.status", "available")
                    headerExists("Content-Type")
                }
            }

        val thenStep = scenario.steps[0]
        assertEquals(4, thenStep.assertions.size)

        assertTrue(thenStep.assertions.any { it.isStatusAssertion() })
        assertTrue(thenStep.assertions.any { it.isBodyContainsAssertion() })
        assertTrue(thenStep.assertions.any { it.isJsonPathAssertion() })
        assertTrue(thenStep.assertions.any { it.isHeaderExistsAssertion() })
    }

    @Test
    fun `should define scenario with tags`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val suite = berrycrush(specPath)

        val scenario =
            suite.scenario("Tagged scenario", tags = setOf("smoke", "api")) {
                then("do something") {}
            }

        assertEquals(setOf("smoke", "api"), scenario.tags)
    }

    @Test
    fun `should define fragment and include it`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val suite = berrycrush(specPath)

        val authFragment =
            suite.fragment("authenticate") {
                given("I login") {
                    call("login") {
                        body("""{"username":"test","password":"secret"}""")
                    }
                    extractTo("token", "$.token")
                }
            }

        val scenario =
            suite.scenario("Protected operation") {
                include(authFragment)

                `when`("I create a pet") {
                    call("createPet")
                }
            }

        // Fragment steps + scenario steps
        assertEquals(2, scenario.steps.size)
        assertEquals(StepType.GIVEN, scenario.steps[0].type)
        assertEquals("login", scenario.steps[0].operationId)
    }

    @Test
    fun `should use authentication shortcuts`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val suite = berrycrush(specPath)

        val scenario =
            suite.scenario("Auth test") {
                `when`("I call with auth") {
                    call("listPets") {
                        bearerToken("my-token")
                    }
                }
            }

        val step = scenario.steps[0]
        assertEquals("Bearer my-token", step.headers["Authorization"])
    }

    @Test
    fun `should configure default headers`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found")

        val suite =
            berrycrush(specPath) {
                defaultHeaders["X-Api-Key"] = "test-key"
            }

        assertEquals("test-key", suite.configuration.defaultHeaders["X-Api-Key"])
    }
}
