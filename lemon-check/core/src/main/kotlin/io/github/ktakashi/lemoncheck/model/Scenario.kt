package io.github.ktakashi.lemoncheck.model

import io.github.ktakashi.lemoncheck.scenario.SourceLocation

/**
 * Represents a BDD scenario containing a sequence of steps.
 *
 * @property name Human-readable name of the scenario
 * @property tags Set of tags for filtering/grouping scenarios
 * @property steps Ordered list of steps to execute
 * @property background Optional background steps run before the scenario
 * @property examples Optional example rows for scenario outline parameterization
 * @property sourceLocation Optional source location for error reporting
 */
data class Scenario(
    val name: String,
    val tags: Set<String> = emptySet(),
    val steps: List<Step> = emptyList(),
    val background: List<Step> = emptyList(),
    val examples: List<ExampleRow>? = null,
    val sourceLocation: SourceLocation? = null,
) {
    init {
        require(name.isNotBlank()) { "Scenario name cannot be blank" }
    }
}
