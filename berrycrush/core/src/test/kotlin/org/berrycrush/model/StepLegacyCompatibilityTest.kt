package org.berrycrush.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StepLegacyCompatibilityTest {
    @Suppress("DEPRECATION")
    @Test
    fun `legacy constructor should expose legacy getters and directives`() {
        val assertion = Assertion.BuiltinAssertion(Condition.Status(200), "status 200")
        val step =
            Step(
                type = StepType.WHEN,
                description = "call pets",
                operationId = "listPets",
                specName = "petstore",
                pathParams = mapOf("petId" to 1),
                queryParams = mapOf("status" to "available"),
                headers = mapOf("Accept" to "application/json"),
                body = "{}",
                extractions = listOf(Extraction("petId", "$.id")),
                assertions = listOf(assertion),
                failMessage = "fail now",
                fragmentName = "create_user",
                includeParameters = mapOf("name" to "Alice"),
                webhookConfig = WebhookConfig("payments", 0, listOf("onPaymentReceived")),
            )

        assertEquals("listPets", step.operationId)
        assertEquals("petstore", step.specName)
        assertEquals(1, step.pathParams["petId"])
        assertEquals("available", step.queryParams["status"])
        assertEquals("application/json", step.headers["Accept"])
        assertEquals("{}", step.body)
        assertEquals(1, step.extractions.size)
        assertEquals(1, step.assertions.size)
        assertEquals("fail now", step.failMessage)
        assertEquals("create_user", step.fragmentName)
        assertEquals("Alice", step.includeParameters["name"])
        assertEquals("payments", step.webhookConfig?.name)

        assertTrue(step.directives.any { it is Directive.CallDirective })
        assertTrue(step.directives.any { it is Directive.ExtractionDirective })
        assertTrue(step.directives.any { it is Directive.AssertionDirective })
        assertTrue(step.directives.any { it is Directive.FailDirective })
        assertTrue(step.directives.any { it is Directive.IncludeDirective })
        assertTrue(step.directives.any { it is Directive.WebhookDirective })
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy constructor should not create call directive for plain step`() {
        val step = Step(type = StepType.GIVEN, description = "setup")

        assertTrue(step.directives.isEmpty())
        assertNull(step.operationId)
        assertNull(step.rawRequest)
        assertTrue(step.autoAssert)
        assertFalse(step.directives.any { it is Directive.CallDirective })
    }

    @Suppress("DEPRECATION")
    @Test
    fun `legacy constructor should map conditional assertion to conditional directive`() {
        val conditional =
            Assertion.ConditionalAssertion(
                ifBranch =
                    ConditionBranch(
                        condition = Condition.Status(201),
                        actions = ConditionalActions(assertions = listOf(Condition.Status(200).toAssertion("status 200"))),
                    ),
            )

        val step =
            Step(
                type = StepType.THEN,
                description = "conditional",
                assertions = listOf(conditional),
            )

        assertEquals(1, step.assertions.size)
        assertIs<Assertion.ConditionalAssertion>(step.assertions.single())
        assertTrue(step.directives.any { it is Directive.ConditionalDirective })
    }
}
