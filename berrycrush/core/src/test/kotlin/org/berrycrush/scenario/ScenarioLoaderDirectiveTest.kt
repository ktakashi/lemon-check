package org.berrycrush.scenario

import org.berrycrush.model.Assertion
import org.berrycrush.model.Directive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScenarioLoaderDirectiveTest {
    @Test
    fun `should map call extract assert conditional and fail actions to directives`() {
        val source =
            """
            scenario: Directive mapping
              when I call API
                call ^createPet
                  petId: 1
                extract $.id => petId
                assert status 201
                if status 201
                  assert $.status equals "available"
                else
                  fail "unexpected status"
                fail "force fail"
            """.trimIndent()

        val scenario = ScenarioLoader.loadFileContentFromString(source).scenarios.single()
        val step = scenario.steps.single()

        assertTrue(step.directives.isNotEmpty())
        assertIs<Directive.CallDirective>(step.directives[0])
        assertTrue(step.directives.any { it is Directive.ExtractionDirective })
        assertTrue(step.directives.any { it is Directive.AssertionDirective })
        assertTrue(step.directives.any { it is Directive.ConditionalDirective })
        assertTrue(step.directives.any { it is Directive.FailDirective })
    }

    @Test
    fun `should map include action to include directive`() {
        val source =
            """
            scenario: Include mapping
              given include a fragment
                include create_user
                  name: "Alice"
                  age: 30
            """.trimIndent()

        val scenario = ScenarioLoader.loadFileContentFromString(source).scenarios.single()
        val step = scenario.steps.single()

        val include = step.directives.single() as Directive.IncludeDirective
        assertEquals("create_user", include.fragmentName)
        assertEquals("Alice", include.parameters["name"])
        assertEquals(30L, include.parameters["age"])
    }

    @Test
    fun `should map webhook action to webhook directive`() {
        val source =
            """
            scenario: Webhook mapping
              given webhook is ready
                webhook: payments
                  hook: onPaymentReceived
            """.trimIndent()

        val scenario = ScenarioLoader.loadFileContentFromString(source).scenarios.single()
        val step = scenario.steps.single()

        val webhook = step.directives.single() as Directive.WebhookDirective
        assertEquals("payments", webhook.config.name)
        assertEquals(listOf("onPaymentReceived"), webhook.config.hooks)
    }

    @Test
    fun `legacy assertions getter should include conditional assertions`() {
        val source =
            """
            scenario: Legacy assertions
              when I call API
                call ^createPet
                if status 201
                  assert $.status equals "available"
                else
                  fail "unexpected status"
            """.trimIndent()

        val scenario = ScenarioLoader.loadFileContentFromString(source).scenarios.single()
        val step = scenario.steps.single()

        @Suppress("DEPRECATION")
        run {
            assertEquals(1, step.assertions.size)
            assertIs<Assertion.ConditionalAssertion>(step.assertions.single())
        }
        assertNotNull(step.directives.filterIsInstance<Directive.ConditionalDirective>().singleOrNull())
    }
}
