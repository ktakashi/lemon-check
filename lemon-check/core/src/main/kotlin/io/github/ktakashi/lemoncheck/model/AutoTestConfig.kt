package io.github.ktakashi.lemoncheck.model

import io.github.ktakashi.lemoncheck.scenario.AutoTestType

/**
 * Configuration for auto-generated invalid/security tests.
 *
 * @property types Types of tests to generate (invalid, security)
 * @property excludes Categories/names of tests to exclude (e.g., "SQLInjection", "maxLength")
 *
 * Available exclude patterns:
 * - Security: "SQLInjection", "XSS", "PathTraversal", "CommandInjection",
 *             "LDAPInjection", "XXE", "XMLInjection", "HeaderInjection"
 * - Invalid: "minLength", "maxLength", "pattern", "required", "enum", "type"
 */
data class AutoTestConfig(
    /** Types of tests to generate */
    val types: Set<AutoTestType>,
    /** Test categories/names to exclude */
    val excludes: Set<String> = emptySet(),
)
