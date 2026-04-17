package org.berrycrush.samples.petstore.assertions

import com.jayway.jsonpath.JsonPath
import org.berrycrush.assertion.Assertion
import org.berrycrush.assertion.AssertionContext
import org.berrycrush.assertion.AssertionResult

/**
 * Custom assertion definitions for Petstore scenarios.
 *
 * These assertions demonstrate BerryCrush's custom assertion capability
 * for domain-specific validation logic that returns explicit pass/fail results.
 */
class PetstoreAssertions {
    /**
     * Assert that a pet has a specific status.
     *
     * Usage in scenario:
     * ```
     * assert the pet should have status "available"
     * ```
     */
    @Assertion("the pet should have status {string}")
    fun assertPetStatus(
        expectedStatus: String,
        context: AssertionContext,
    ): AssertionResult {
        val response =
            context.lastResponse
                ?: return AssertionResult.failed("No HTTP response available")

        return try {
            val actualStatus: String = JsonPath.read(response.body(), "$.status")
            if (actualStatus == expectedStatus) {
                AssertionResult.passed()
            } else {
                AssertionResult.failed(
                    message = "Pet status mismatch",
                    expectedValue = expectedStatus,
                    actualValue = actualStatus,
                )
            }
        } catch (e: Exception) {
            AssertionResult.failed("Failed to read pet status: ${e.message}")
        }
    }

    /**
     * Assert that a pet has a specific name.
     *
     * Usage in scenario:
     * ```
     * assert the pet name should be "Fluffy"
     * ```
     */
    @Assertion("the pet name should be {string}")
    fun assertPetName(
        expectedName: String,
        context: AssertionContext,
    ): AssertionResult {
        val response =
            context.lastResponse
                ?: return AssertionResult.failed("No HTTP response available")

        return try {
            val actualName: String = JsonPath.read(response.body(), "$.name")
            if (actualName == expectedName) {
                AssertionResult.passed()
            } else {
                AssertionResult.failed(
                    message = "Pet name mismatch",
                    expectedValue = expectedName,
                    actualValue = actualName,
                )
            }
        } catch (e: Exception) {
            AssertionResult.failed("Failed to read pet name: ${e.message}")
        }
    }

    /**
     * Assert that the response contains a specific number of items.
     *
     * Usage in scenario:
     * ```
     * assert there are 5 items in the response
     * ```
     */
    @Assertion("there are {int} items in the response")
    fun assertItemCount(
        expectedCount: Int,
        context: AssertionContext,
    ): AssertionResult {
        val response =
            context.lastResponse
                ?: return AssertionResult.failed("No HTTP response available")

        return try {
            val items = JsonPath.read<List<*>>(response.body(), "$")
            val actualCount = items.size
            if (actualCount == expectedCount) {
                AssertionResult.passed()
            } else {
                AssertionResult.failed(
                    message = "Item count mismatch",
                    expectedValue = expectedCount,
                    actualValue = actualCount,
                )
            }
        } catch (e: Exception) {
            AssertionResult.failed("Failed to count items: ${e.message}")
        }
    }

    /**
     * Assert that the response body contains a substring.
     *
     * Usage in scenario:
     * ```
     * assert the response body contains "Fluffy"
     * ```
     */
    @Assertion("the response body contains {string}")
    fun assertBodyContains(
        expected: String,
        context: AssertionContext,
    ): AssertionResult {
        val response =
            context.lastResponse
                ?: return AssertionResult.failed("No HTTP response available")

        val body = response.body()
        return if (body.contains(expected)) {
            AssertionResult.passed()
        } else {
            AssertionResult.failed(
                message = "Response body does not contain expected string",
                expectedValue = expected,
                actualValue = body.take(200) + if (body.length > 200) "..." else "",
            )
        }
    }

    /**
     * Assert that a pet's name matches a variable.
     *
     * Usage in scenario:
     * ```
     * assert the pet name matches variable "createdPetName"
     * ```
     */
    @Assertion("the pet name matches variable {string}")
    fun assertPetNameMatchesVariable(
        variableName: String,
        context: AssertionContext,
    ): AssertionResult {
        val expectedName =
            context.variable(variableName) as? String
                ?: return AssertionResult.failed("Variable '$variableName' not found or not a string")

        val response =
            context.lastResponse
                ?: return AssertionResult.failed("No HTTP response available")

        return try {
            val actualName: String = JsonPath.read(response.body(), "$.name")
            if (actualName == expectedName) {
                AssertionResult.passed()
            } else {
                AssertionResult.failed(
                    message = "Pet name does not match variable",
                    expectedValue = expectedName,
                    actualValue = actualName,
                )
            }
        } catch (e: Exception) {
            AssertionResult.failed("Failed to read pet name: ${e.message}")
        }
    }

    /**
     * Assert that the response has a successful status code.
     *
     * Usage in scenario:
     * ```
     * assert the response is successful
     * ```
     */
    @Assertion("the response is successful")
    fun assertResponseSuccessful(context: AssertionContext): AssertionResult {
        val response =
            context.lastResponse
                ?: return AssertionResult.failed("No HTTP response available")

        val statusCode = response.statusCode()
        return if (statusCode in 200..299) {
            AssertionResult.passed()
        } else {
            AssertionResult.failed(
                message = "Response is not successful",
                expectedValue = "2xx",
                actualValue = statusCode,
            )
        }
    }

    /**
     * Assert that the pet ID is positive.
     *
     * Usage in scenario:
     * ```
     * assert the pet has a valid ID
     * ```
     */
    @Assertion("the pet has a valid ID")
    fun assertPetIdValid(context: AssertionContext): AssertionResult {
        val response =
            context.lastResponse
                ?: return AssertionResult.failed("No HTTP response available")

        return try {
            val id: Long = JsonPath.read(response.body(), "$.id")
            if (id > 0) {
                AssertionResult.passed()
            } else {
                AssertionResult.failed(
                    message = "Pet ID is not valid",
                    expectedValue = "> 0",
                    actualValue = id,
                )
            }
        } catch (e: Exception) {
            AssertionResult.failed("Failed to read pet ID: ${e.message}")
        }
    }
}
