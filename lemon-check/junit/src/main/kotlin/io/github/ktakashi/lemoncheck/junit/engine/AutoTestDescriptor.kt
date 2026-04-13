package io.github.ktakashi.lemoncheck.junit.engine

import io.github.ktakashi.lemoncheck.autotest.AutoTestCase
import io.github.ktakashi.lemoncheck.autotest.ParameterLocation
import io.github.ktakashi.lemoncheck.scenario.AutoTestType
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor

/**
 * Test descriptor for a single auto-generated test case.
 */
class AutoTestDescriptor(
    uniqueId: UniqueId,
    displayName: String,
    val testCase: AutoTestCase,
    val stepDescription: String,
) : AbstractTestDescriptor(uniqueId, displayName) {
    override fun getType(): TestDescriptor.Type = TestDescriptor.Type.TEST

    override fun getSource(): java.util.Optional<TestSource> = java.util.Optional.empty()

    companion object {
        /**
         * Create a display name for an auto-test case.
         * Format: [Invalid request] {location} {fieldName} with value {value}
         *         [Security {type}] {location} {fieldName} with value {value}
         */
        fun createDisplayName(testCase: AutoTestCase): String {
            val typeLabel = when (testCase.type) {
                AutoTestType.INVALID -> "[Invalid request]"
                AutoTestType.SECURITY -> "[Security ${extractSecurityType(testCase.description)}]"
            }
            val location = when (testCase.location) {
                ParameterLocation.BODY -> "request body"
                ParameterLocation.PATH -> "path variable"
                ParameterLocation.HEADER -> "header"
                ParameterLocation.QUERY -> "query parameter"
            }
            val valueStr = testCase.invalidValue?.toString()?.take(30) ?: "null"
            val valueSuffix = if (valueStr.length >= 30) "..." else ""
            return "$typeLabel $location ${testCase.fieldName} with value $valueStr$valueSuffix"
        }

        private fun extractSecurityType(description: String): String {
            // Extract type from description like "SQL Injection: Single quote"
            return description.substringBefore(":").trim()
        }
    }
}
