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
 * @see LemonCheckContextConfiguration
 * @see PetstoreBindings
 */
@Suite
@IncludeEngines("lemoncheck")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@LemonCheckContextConfiguration
@LemonCheckScenarios(locations = {"scenarios/*.scenario"})
@LemonCheckConfiguration(bindings = PetstoreBindings.class, openApiSpec = "petstore.yaml")
@LemonCheckSpec(paths = {"petstore.yaml"})
public class PetstoreScenarioTest {
}
