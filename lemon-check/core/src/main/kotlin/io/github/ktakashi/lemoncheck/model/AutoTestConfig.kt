package io.github.ktakashi.lemoncheck.model

import io.github.ktakashi.lemoncheck.scenario.AutoTestType

/**
 * Configuration for auto-generated invalid/security tests.
 */
data class AutoTestConfig(
    /** Types of tests to generate */
    val types: Set<AutoTestType>,
)
