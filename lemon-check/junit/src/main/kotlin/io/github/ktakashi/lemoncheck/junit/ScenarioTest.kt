package io.github.ktakashi.lemoncheck.junit

import io.github.ktakashi.lemoncheck.dsl.LemonCheckSuite
import io.github.ktakashi.lemoncheck.dsl.ScenarioScope
import io.github.ktakashi.lemoncheck.executor.ScenarioExecutor
import io.github.ktakashi.lemoncheck.model.ResultStatus
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.ScenarioResult
import io.github.ktakashi.lemoncheck.report.ConsoleReporter
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Base class for LemonCheck scenario tests.
 *
 * Extend this class to define and run BDD-style API tests with JUnit 5.
 *
 * Example:
 * ```kotlin
 * @LemonCheckSpec("petstore.yaml")
 * class PetstoreTest : ScenarioTest() {
 *     override fun defineScenarios() {
 *         scenario("List all pets") {
 *             `when`("I request the list of pets") {
 *                 call("listPets")
 *             }
 *             then("I receive a successful response") {
 *                 statusCode(200)
 *                 bodyArrayNotEmpty("$")
 *             }
 *         }
 *     }
 * }
 * ```
 */
@ExtendWith(LemonCheckExtension::class)
abstract class ScenarioTest {
    private val suite: LemonCheckSuite = LemonCheckSuite.create()
    private val reporter = ConsoleReporter()

    /**
     * Override this method to define your scenarios.
     */
    abstract fun defineScenarios()

    /**
     * Override this method to configure the test suite.
     */
    open fun configureSuite() {
        // Default implementation does nothing
    }

    /**
     * Register the OpenAPI spec for this test.
     */
    protected fun spec(
        path: String,
        block: io.github.ktakashi.lemoncheck.config.SpecConfiguration.() -> Unit = {},
    ) {
        suite.spec(path, block)
    }

    /**
     * Register a named OpenAPI spec for multi-spec scenarios.
     */
    protected fun spec(
        name: String,
        path: String,
        block: io.github.ktakashi.lemoncheck.config.SpecConfiguration.() -> Unit = {},
    ) {
        suite.spec(name, path, block)
    }

    /**
     * Configure the test suite.
     */
    protected fun configure(block: io.github.ktakashi.lemoncheck.config.Configuration.() -> Unit) {
        suite.configure(block)
    }

    /**
     * Define a scenario.
     */
    protected fun scenario(
        name: String,
        tags: Set<String> = emptySet(),
        block: ScenarioScope.() -> Unit,
    ): Scenario = suite.scenario(name, tags, block)

    /**
     * Define a scenario outline (parameterized scenario).
     */
    protected fun scenarioOutline(
        name: String,
        tags: Set<String> = emptySet(),
        block: io.github.ktakashi.lemoncheck.dsl.ScenarioOutlineScope.() -> Unit,
    ): List<Scenario> = suite.scenarioOutline(name, tags, block)

    /**
     * Define a reusable fragment.
     */
    protected fun fragment(
        name: String,
        block: io.github.ktakashi.lemoncheck.dsl.FragmentScope.() -> Unit,
    ) {
        suite.fragment(name, block)
    }

    /**
     * JUnit 5 @TestFactory method that generates dynamic tests from scenarios.
     */
    @TestFactory
    fun scenarios(): Collection<DynamicTest> {
        configureSuite()
        defineScenarios()

        val executor = ScenarioExecutor(suite.specRegistry, suite.configuration)

        return suite.allScenarios().map { scenario ->
            DynamicTest.dynamicTest(scenario.name) {
                val result = executor.execute(scenario)
                reporter.onScenarioComplete(result)

                if (result.status == ResultStatus.FAILED) {
                    val failedSteps =
                        result.stepResults
                            .filter { it.status == ResultStatus.FAILED }
                            .joinToString("\n") { step ->
                                "  - ${step.step.description}: ${step.error?.message ?: "Unknown error"}"
                            }
                    throw AssertionError("Scenario '${scenario.name}' failed:\n$failedSteps")
                }

                if (result.status == ResultStatus.SKIPPED) {
                    org.junit.jupiter.api.Assumptions
                        .assumeTrue(false, "Scenario was skipped")
                }
            }
        }
    }

    /**
     * Execute a single scenario and return the result.
     */
    protected fun executeScenario(scenario: Scenario): ScenarioResult {
        val executor = ScenarioExecutor(suite.specRegistry, suite.configuration)
        return executor.execute(scenario)
    }

    /**
     * Get the underlying suite for advanced usage.
     */
    protected fun getSuite(): LemonCheckSuite = suite
}
