package io.github.ktakashi.lemoncheck.dsl

import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.config.SpecConfiguration
import io.github.ktakashi.lemoncheck.model.Fragment
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.openapi.SpecRegistry

/**
 * Main entry point for LemonCheck test suite definition.
 *
 * Manages OpenAPI spec(s) configuration and scenario definitions.
 */
@LemonCheckDsl
class LemonCheckSuite internal constructor() {
    val specRegistry = SpecRegistry()
    val configuration = Configuration()
    internal val scenarios = mutableListOf<Scenario>()
    internal val fragments = mutableMapOf<String, Fragment>()

    companion object {
        /**
         * Create a new LemonCheck test suite.
         */
        fun create(): LemonCheckSuite = LemonCheckSuite()
    }

    /**
     * Register a single OpenAPI spec (simple API).
     */
    fun spec(
        path: String,
        config: SpecConfiguration.() -> Unit = {},
    ) {
        specRegistry.registerDefault(path, config)
    }

    /**
     * Register a named OpenAPI spec (multi-spec API).
     */
    fun spec(
        name: String,
        path: String,
        config: SpecConfiguration.() -> Unit = {},
    ) {
        specRegistry.register(name, path, config)
    }

    /**
     * Configure the test suite.
     */
    fun configure(block: Configuration.() -> Unit) {
        configuration.apply(block)
    }

    /**
     * Define a scenario.
     */
    fun scenario(
        name: String,
        tags: Set<String> = emptySet(),
        block: ScenarioScope.() -> Unit,
    ): Scenario {
        val scope = ScenarioScope(name, tags, this)
        scope.block()
        val scenario = scope.build()
        scenarios.add(scenario)
        return scenario
    }

    /**
     * Define a scenario outline (parameterized scenario).
     */
    fun scenarioOutline(
        name: String,
        tags: Set<String> = emptySet(),
        block: ScenarioOutlineScope.() -> Unit,
    ): List<Scenario> {
        val scope = ScenarioOutlineScope(name, tags, this)
        scope.block()
        val expandedScenarios = scope.build()
        scenarios.addAll(expandedScenarios)
        return expandedScenarios
    }

    /**
     * Register a fragment for reuse.
     */
    fun fragment(
        name: String,
        block: FragmentScope.() -> Unit,
    ): Fragment {
        val scope = FragmentScope(name)
        scope.block()
        val fragment = scope.build()
        fragments[name] = fragment
        return fragment
    }

    /**
     * Get a registered fragment by name.
     */
    fun getFragment(name: String): Fragment? = fragments[name]

    /**
     * Get all defined scenarios.
     */
    fun allScenarios(): List<Scenario> = scenarios.toList()
}

/**
 * Create a LemonCheck test suite with a single OpenAPI spec.
 */
fun lemonCheck(
    openApiSpec: String,
    config: Configuration.() -> Unit = {},
): LemonCheckSuite =
    LemonCheckSuite().apply {
        spec(openApiSpec)
        configure(config)
    }

/**
 * Create a LemonCheck test suite with custom configuration (multi-spec support).
 */
fun lemonCheck(config: LemonCheckSuite.() -> Unit): LemonCheckSuite = LemonCheckSuite().apply(config)
