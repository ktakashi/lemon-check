package org.berrycrush.junit

import kotlin.annotation.AnnotationRetention
import kotlin.annotation.AnnotationTarget

/**
 * Annotation to specify OpenAPI spec(s) for a BerryCrush test class.
 *
 * @property paths Paths to OpenAPI specification files
 * @property baseUrl Base URL override for API requests
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class BerryCrushSpec(
    vararg val paths: String = [],
    val baseUrl: String = "",
)

/**
 * Annotation to mark a test method or class as providing BerryCrush scenarios.
 *
 * When applied, the BerryCrushTestEngine will discover and execute scenario files
 * from the specified locations.
 *
 * @property locations Classpath locations to search for scenario files.
 *                     Supports glob patterns (e.g., berrycrush/scenarios/`*`.scenario, `**`/`*`.scenario).
 *                     Paths are relative to the classpath root.
 *                     Default is ["berrycrush/scenarios/\*.scenario"].
 * @property fragments Classpath locations to search for fragment files.
 *                     Supports glob patterns (e.g., berrycrush/fragments/`*`.fragment).
 *                     Paths are relative to the classpath root.
 *                     Default is ["berrycrush/fragments/\*.fragment"].
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class BerryCrushScenarios(
    vararg val locations: String = ["berrycrush/scenarios/*.scenario"],
    val fragments: Array<String> = ["berrycrush/fragments/*.fragment"],
)

/**
 * Annotation to filter scenarios by tags.
 *
 * @property include Only run scenarios with these tags
 * @property exclude Skip scenarios with these tags
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class BerryCrushTags(
    val include: Array<String> = [],
    val exclude: Array<String> = [],
)

/**
 * Annotation to configure timeout for scenario execution.
 *
 * @property value Timeout value
 * @property unit Time unit (default: seconds)
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class BerryCrushTimeout(
    val value: Long,
    val unit: java.util.concurrent.TimeUnit = java.util.concurrent.TimeUnit.SECONDS,
)

/**
 * Marks a method as a BerryCrush scenario test.
 *
 * Methods annotated with `@ScenarioTest` must return a [org.berrycrush.model.Scenario] object
 * that will be automatically executed by the BerryCrush test engine.
 *
 * ## Usage
 *
 * ```kotlin
 * @Suite
 * @BerryCrushSpec(paths = ["petstore.yaml"])
 * class PetstoreTest {
 *
 *     @ScenarioTest
 *     fun createPet(suite: BerryCrushSuite): Scenario =
 *         suite.scenario("Create a pet") {
 *             whenever("I create a pet") {
 *                 call("createPet") { body(mapOf("name" to "Fluffy")) }
 *             }
 *             afterwards("it is created") {
 *                 statusCode(201)
 *             }
 *         }
 * }
 * ```
 *
 * ## Spring Boot Integration
 *
 * ```kotlin
 * @SpringBootTest(webEnvironment = RANDOM_PORT)
 * @BerryCrushSpec(paths = ["petstore.yaml"])
 * @BerryCrushContextConfiguration
 * class PetstoreSpringTest {
 *
 *     @LocalServerPort
 *     var port: Int = 0
 *
 *     @BeforeEach
 *     fun setup(config: BerryCrushConfiguration) {
 *         config.baseUrl = "http://localhost:$port/api"
 *     }
 *
 *     @ScenarioTest
 *     fun createPet(suite: BerryCrushSuite): Scenario = ...
 * }
 * ```
 *
 * ## Method Requirements
 *
 * - Must return `Scenario` (from `BerryCrushSuite.scenario()`)
 * - Can accept `BerryCrushSuite` as parameter (injected by engine)
 * - Can be a member function of a class annotated with `@BerryCrushSpec`
 *
 * @see BerryCrushSpec
 * @see org.berrycrush.dsl.BerryCrushSuite
 * @see org.berrycrush.model.Scenario
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class ScenarioTest
