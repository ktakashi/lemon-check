package org.berrycrush.samples.petstore;

import org.berrycrush.junit.BerryCrushConfiguration;
import org.berrycrush.junit.BerryCrushScenarios;
import org.berrycrush.junit.BerryCrushSpec;
import org.berrycrush.junit.BerryCrushTags;
import org.berrycrush.spring.BerryCrushContextConfiguration;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.Suite;
import org.springframework.boot.test.context.SpringBootTest;

import org.berrycrush.samples.petstore.assertions.PetstoreAssertions;

/**
 * Integration test for petstore API using BerryCrush scenarios with Spring Boot.
 * 
 * <p>This test class demonstrates how to integrate BerryCrush scenarios
 * with Spring Boot test context. The scenarios are executed against
 * a running Spring Boot application with H2 database.
 * 
 * <h2>Spring Integration Setup</h2>
 * <ol>
 *   <li>Add {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} - starts embedded server</li>
 *   <li>Add {@code @BerryCrushContextConfiguration} - enables Spring bindings injection</li>
 *   <li>Ensure bindings class (e.g., {@link PetstoreBindings}) is a Spring {@code @Component}</li>
 *   <li>Use {@code @LocalServerPort} in bindings to get the dynamic port</li>
 * </ol>
 * 
 * <h2>How It Works</h2>
 * <p>The {@code @BerryCrushContextConfiguration} annotation triggers the Spring 
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
 * @see BerryCrushContextConfiguration
 * @see PetstoreBindings
 * @see SampleLoggingPlugin
 */
@Suite
@IncludeEngines("berrycrush")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@BerryCrushContextConfiguration
@BerryCrushScenarios(locations = {"scenarios/*.scenario"}, fragments = {"fragments/*.fragment"})
@BerryCrushConfiguration(
    bindings = PetstoreBindings.class, 
    openApiSpec = "petstore.yaml",
    plugins = {"report:text", "report:console:high-contrast", "report:json:berrycrush/report.json", "sample:logging"},
    stepClasses = {PetCustomSteps.class},
    assertionClasses = {PetstoreAssertions.class}
)
@BerryCrushSpec(paths = {"petstore.yaml"})
@BerryCrushTags(exclude = {"ignore"})
public class PetstoreScenarioTest {
}
