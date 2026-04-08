package io.github.ktakashi.lemoncheck.junit

import io.github.ktakashi.lemoncheck.config.Configuration
import io.github.ktakashi.lemoncheck.dsl.LemonCheckSuite
import io.github.ktakashi.lemoncheck.executor.ScenarioExecutor
import io.github.ktakashi.lemoncheck.model.Scenario
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.jupiter.api.extension.TestTemplateInvocationContext
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider
import java.util.stream.Stream

/**
 * JUnit 5 extension for LemonCheck scenarios.
 *
 * This extension integrates LemonCheck scenarios with JUnit 5's test framework,
 * enabling scenario execution as JUnit tests with full IDE and CI support.
 *
 * Usage:
 * ```kotlin
 * @ExtendWith(LemonCheckExtension::class)
 * @LemonCheckSpec("api-spec.yaml")
 * class ApiTest : ScenarioTest() {
 *     override fun defineScenarios() {
 *         scenario("List pets") {
 *             `when`("I list all pets") {
 *                 call("listPets")
 *             }
 *             then("I get a list of pets") {
 *                 statusCode(200)
 *             }
 *         }
 *     }
 * }
 * ```
 */
class LemonCheckExtension :
    BeforeAllCallback,
    BeforeEachCallback,
    ParameterResolver,
    TestTemplateInvocationContextProvider {
    companion object {
        private val NAMESPACE = ExtensionContext.Namespace.create(LemonCheckExtension::class.java)
        private const val SUITE_KEY = "lemonCheckSuite"
        private const val EXECUTOR_KEY = "scenarioExecutor"
    }

    override fun beforeAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        val specAnnotation = testClass.getAnnotation(LemonCheckSpec::class.java)

        val suite = LemonCheckSuite.create()

        // Load spec from annotation
        specAnnotation?.paths?.forEach { path ->
            suite.spec(path)
        }

        // Apply configuration
        specAnnotation?.baseUrl?.takeIf { it.isNotBlank() }?.let {
            suite.configuration.baseUrl = it
        }

        context.getStore(NAMESPACE).put(SUITE_KEY, suite)

        // Create executor
        val executor = ScenarioExecutor(suite.specRegistry, suite.configuration)
        context.getStore(NAMESPACE).put(EXECUTOR_KEY, executor)
    }

    override fun beforeEach(context: ExtensionContext) {
        // Reset execution context for each test
        val suite = getSuite(context)
        // Clear any scenario-specific state if needed
    }

    override fun supportsParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Boolean {
        val paramType = parameterContext.parameter.type
        return paramType == LemonCheckSuite::class.java ||
            paramType == ScenarioExecutor::class.java ||
            paramType == Configuration::class.java
    }

    override fun resolveParameter(
        parameterContext: ParameterContext,
        extensionContext: ExtensionContext,
    ): Any =
        when (val paramType = parameterContext.parameter.type) {
            LemonCheckSuite::class.java -> getSuite(extensionContext)
            ScenarioExecutor::class.java -> getExecutor(extensionContext)
            Configuration::class.java -> getSuite(extensionContext).configuration
            else -> throw IllegalArgumentException("Unsupported parameter type: $paramType")
        }

    override fun supportsTestTemplate(context: ExtensionContext): Boolean =
        context.requiredTestMethod.isAnnotationPresent(LemonCheckScenarios::class.java) ||
            context.requiredTestClass.isAnnotationPresent(LemonCheckScenarios::class.java)

    override fun provideTestTemplateInvocationContexts(context: ExtensionContext): Stream<TestTemplateInvocationContext> =
        getSuite(context).allScenarios().stream().map { scenario ->
            ScenarioInvocationContext(scenario, getExecutor(context))
        }

    private fun getSuite(context: ExtensionContext): LemonCheckSuite =
        context.getStore(NAMESPACE).get(SUITE_KEY, LemonCheckSuite::class.java)
            ?: throw IllegalStateException("LemonCheckSuite not initialized. Is @ExtendWith(LemonCheckExtension::class) present?")

    private fun getExecutor(context: ExtensionContext): ScenarioExecutor =
        context.getStore(NAMESPACE).get(EXECUTOR_KEY, ScenarioExecutor::class.java)
            ?: throw IllegalStateException("ScenarioExecutor not initialized.")

    /**
     * Context for a single scenario invocation.
     */
    private class ScenarioInvocationContext(
        private val scenario: Scenario,
        private val executor: ScenarioExecutor,
    ) : TestTemplateInvocationContext {
        override fun getDisplayName(invocationIndex: Int): String = scenario.name

        override fun getAdditionalExtensions(): List<org.junit.jupiter.api.extension.Extension> =
            listOf(
                ScenarioParameterResolver(scenario, executor),
            )
    }

    /**
     * Parameter resolver for individual scenarios.
     */
    private class ScenarioParameterResolver(
        private val scenario: Scenario,
        private val executor: ScenarioExecutor,
    ) : ParameterResolver {
        override fun supportsParameter(
            parameterContext: ParameterContext,
            extensionContext: ExtensionContext,
        ): Boolean = parameterContext.parameter.type == Scenario::class.java

        override fun resolveParameter(
            parameterContext: ParameterContext,
            extensionContext: ExtensionContext,
        ): Any = scenario
    }
}
