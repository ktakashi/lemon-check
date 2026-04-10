package io.github.ktakashi.samples.petstore;

import io.github.ktakashi.lemoncheck.junit.LemonCheckConfiguration;
import io.github.ktakashi.lemoncheck.junit.LemonCheckScenarios;
import io.github.ktakashi.lemoncheck.junit.LemonCheckSpec;
import io.github.ktakashi.lemoncheck.spring.LemonCheckContextConfiguration;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.Suite;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test for petstore API using lemon-check scenarios with Spring Boot.
 * 
 * <p>This test class demonstrates how to integrate lemon-check scenarios
 * with Spring Boot test context. The scenarios are executed against
 * a running Spring Boot application with H2 database.
 * 
 * <h2>Spring Integration Setup</h2>
 * <ol>
 *   <li>Add {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} - starts embedded server</li>
 *   <li>Add {@code @LemonCheckContextConfiguration} - enables Spring bindings injection</li>
 *   <li>Ensure bindings class (e.g., {@link PetstoreBindings}) is a Spring {@code @Component}</li>
 *   <li>Use {@code @LocalServerPort} in bindings to get the dynamic port</li>
 * </ol>
 * 
 * <h2>How It Works</h2>
 * <p>The {@code @LemonCheckContextConfiguration} annotation triggers the Spring 
 * context integration module which:
 * <ul>
 *   <li>Initializes Spring TestContext before scenario execution</li>
 *   <li>Retrieves the bindings instance from Spring's ApplicationContext</li>
 *   <li>Enables dependency injection ({@code @Autowired}, {@code @LocalServerPort})</li>
 *   <li>Cleans up context after all scenarios complete</li>
 * </ul>
 * 
 * <h2>Plugin Integration</h2>
 * <p>This test demonstrates plugin registration using name-based discovery.
 * The {@link SampleLoggingPlugin} is registered via ServiceLoader and resolved
 * by its plugin ID ("sample:logging"). It logs scenario and step lifecycle events.
 *
 * @see LemonCheckContextConfiguration
 * @see PetstoreBindings
 * @see SampleLoggingPlugin
 */
@Suite
@IncludeEngines("lemoncheck")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@LemonCheckContextConfiguration
@LemonCheckScenarios(locations = {"scenarios/*.scenario"}, fragments = {"fragments/*.fragment"})
@LemonCheckConfiguration(
    bindings = PetstoreBindings.class, 
    openApiSpec = "petstore.yaml",
    plugins = {"report:text", "sample:logging"},
    stepClasses = {PetCustomSteps.class}
)
@LemonCheckSpec(paths = {"petstore.yaml"})
public class PetstoreScenarioTest {
}
