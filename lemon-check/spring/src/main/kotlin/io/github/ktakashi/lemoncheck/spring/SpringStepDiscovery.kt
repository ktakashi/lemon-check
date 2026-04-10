package io.github.ktakashi.lemoncheck.spring

import io.github.ktakashi.lemoncheck.step.AnnotationStepScanner
import io.github.ktakashi.lemoncheck.step.DefaultStepRegistry
import io.github.ktakashi.lemoncheck.step.Step
import io.github.ktakashi.lemoncheck.step.StepDefinition
import io.github.ktakashi.lemoncheck.step.StepRegistry
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Spring auto-discovery support for @Step annotated methods.
 *
 * Automatically discovers and registers step definitions from Spring-managed beans
 * annotated with @Component (or its derivatives) that have methods with @Step.
 *
 * To enable auto-discovery, include this configuration class in your Spring context:
 *
 * ```kotlin
 * @SpringBootTest
 * @Import(SpringStepDiscovery::class)
 * class MyApiTest {
 *     @Autowired
 *     lateinit var stepRegistry: StepRegistry
 * }
 * ```
 *
 * Or annotate your step definitions with @Component:
 *
 * ```kotlin
 * @Component
 * class MySteps {
 *     @Step("I have {int} pets")
 *     fun setPetCount(count: Int) {
 *         // Step implementation
 *     }
 * }
 * ```
 */
@Configuration
class SpringStepDiscovery {
    private val annotationScanner = AnnotationStepScanner()

    /**
     * Creates a StepRegistry bean populated with all step definitions
     * discovered from Spring-managed beans.
     *
     * @param context The Spring ApplicationContext
     * @return A StepRegistry containing all discovered step definitions
     */
    @Bean
    fun stepRegistry(context: ApplicationContext): StepRegistry {
        val registry = DefaultStepRegistry()
        val definitions = discoverSteps(context)
        registry.registerAll(definitions)
        return registry
    }

    /**
     * Discovers all step definitions from Spring-managed beans.
     *
     * Scans all beans in the application context for methods annotated with @Step
     * and creates StepDefinition instances using the Spring-managed bean instances.
     *
     * @param context The Spring ApplicationContext
     * @return List of discovered StepDefinitions
     */
    fun discoverSteps(context: ApplicationContext): List<StepDefinition> =
        context.beanDefinitionNames
            .mapNotNull { beanName ->
                runCatching {
                    val bean = context.getBean(beanName)
                    val beanClass = bean.javaClass
                    val hasStepMethods = beanClass.declaredMethods.any { it.isAnnotationPresent(Step::class.java) }
                    if (hasStepMethods) annotationScanner.scan(beanClass, bean) else null
                }.getOrNull()
            }.flatten()
}
