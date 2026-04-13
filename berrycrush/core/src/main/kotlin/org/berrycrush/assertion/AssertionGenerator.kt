package org.berrycrush.assertion

import org.berrycrush.model.Assertion
import org.berrycrush.model.Condition
import org.berrycrush.model.ConditionOperator
import org.berrycrush.openapi.ResolvedOperation
import org.berrycrush.openapi.findResponse

/**
 * Generates assertions from OpenAPI specification.
 *
 * Automatically creates assertions based on the expected response
 * defined in the OpenAPI operation.
 */
class AssertionGenerator {
    /**
     * Generate assertions for an expected response.
     *
     * @param operation The resolved OpenAPI operation
     * @param expectedStatusCode Expected status code (default: 200)
     * @param includeStatusCode Include status code assertion
     * @param includeContentType Include Content-Type header assertion
     * @param includeSchema Include schema validation assertion
     * @return List of generated assertions
     */
    fun generateAssertions(
        operation: ResolvedOperation,
        expectedStatusCode: Int = 200,
        includeStatusCode: Boolean = true,
        includeContentType: Boolean = true,
        includeSchema: Boolean = true,
    ): List<Assertion> {
        val assertions = mutableListOf<Assertion>()

        // Get the response definition for expected status
        val response = operation.findResponse(expectedStatusCode)

        // Status code assertion
        if (includeStatusCode) {
            assertions.add(
                Assertion(
                    condition = Condition.Status(expectedStatusCode),
                    description = "status $expectedStatusCode",
                ),
            )
        }

        // Content-Type assertion
        if (includeContentType && response != null) {
            val contentType = response.content?.keys?.firstOrNull()
            if (contentType != null) {
                assertions.add(
                    Assertion(
                        condition =
                            Condition.Header(
                                name = "Content-Type",
                                operator = ConditionOperator.EQUALS,
                                expected = contentType,
                            ),
                        description = "header Content-Type equals \"$contentType\"",
                    ),
                )
            }
        }

        // Schema validation assertion
        if (includeSchema && response?.content != null) {
            val schema =
                response.content.values
                    .firstOrNull()
                    ?.schema
            if (schema != null) {
                assertions.add(
                    Assertion(
                        condition = Condition.Schema,
                        description = "matches schema",
                    ),
                )
            }
        }

        return assertions
    }

    /**
     * Determine the expected successful status code for an operation.
     *
     * @param operation The resolved operation
     * @return Expected success status code (200, 201, 204, etc.)
     */
    fun determineSuccessStatusCode(operation: ResolvedOperation): Int {
        val responses = operation.operation.responses ?: return 200

        // Check for explicit success codes
        for (code in listOf("200", "201", "202", "204")) {
            if (responses.containsKey(code)) {
                return code.toInt()
            }
        }

        // Check for 2xx pattern
        if (responses.containsKey("2XX")) {
            return 200
        }

        // Default to 200
        return 200
    }
}
