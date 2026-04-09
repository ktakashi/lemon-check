Standalone Runner
=================

LemonCheck can be used without JUnit through the ``ScenarioRunner`` class.
This is useful for:

- Custom test frameworks
- CI/CD pipeline scripts
- Integration with other testing tools
- Programmatic scenario execution

Overview
--------

The ``ScenarioRunner`` provides a simple API to execute scenarios and collect results:

.. code-block:: kotlin

    import io.github.ktakashi.lemoncheck.config.Configuration
    import io.github.ktakashi.lemoncheck.dsl.lemonCheck
    import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
    import io.github.ktakashi.lemoncheck.runner.ScenarioRunner

    // Create configuration
    val config = Configuration().apply {
        baseUrl = "https://api.example.com"
    }

    // Create spec registry and load OpenAPI spec
    val specRegistry = SpecRegistry()
    specRegistry.registerDefault("petstore.yaml")

    // Create runner
    val runner = ScenarioRunner(specRegistry, config)

    // Define scenarios
    val suite = lemonCheck("petstore.yaml")
    val scenario = suite.scenario("List pets") {
        `when`("I request pets") {
            call("listPets")
        }
        then("I get results") {
            statusCode(200)
        }
    }

    // Run and get results
    val result = runner.run(listOf(scenario))
    println("Passed: ${result.passed}, Failed: ${result.failed}")

Basic Usage
-----------

Running Multiple Scenarios
^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: kotlin

    val scenarios = listOf(
        suite.scenario("Test 1") { /* ... */ },
        suite.scenario("Test 2") { /* ... */ },
        suite.scenario("Test 3") { /* ... */ }
    )

    val result = runner.run(scenarios)

    // Check overall status
    when (result.status) {
        ResultStatus.PASSED -> println("All tests passed!")
        ResultStatus.FAILED -> println("${result.failed} tests failed")
        ResultStatus.ERROR -> println("${result.errors} tests had errors")
        ResultStatus.SKIPPED -> println("All tests were skipped")
    }

Running a Single Scenario
^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: kotlin

    val result = runner.run(scenario)

    for ((scenario, scenarioResult) in result.scenarioResults) {
        println("${scenario.name}: ${scenarioResult.status}")
    }

Progress Callbacks
^^^^^^^^^^^^^^^^^^

Receive callbacks as each scenario completes:

.. code-block:: kotlin

    val result = runner.run(scenarios) { scenario, scenarioResult ->
        when (scenarioResult.status) {
            ResultStatus.PASSED -> println("✓ ${scenario.name}")
            ResultStatus.FAILED -> println("✗ ${scenario.name}")
            ResultStatus.ERROR -> println("! ${scenario.name}: ${scenarioResult.error?.message}")
            ResultStatus.SKIPPED -> println("- ${scenario.name} (skipped)")
        }
    }

Manual Lifecycle Control
------------------------

For fine-grained control over execution, use the lifecycle methods:

.. code-block:: kotlin

    // Start the test execution lifecycle
    runner.beginExecution()

    try {
        // Execute scenarios one by one
        for (scenario in scenarios) {
            val result = runner.executeScenario(scenario)
            
            // Custom logic between scenarios
            if (result.status == ResultStatus.FAILED) {
                logFailure(scenario, result)
            }
        }
    } finally {
        // End the lifecycle (triggers report generation)
        runner.endExecution()
    }

Cross-Scenario Variable Sharing
-------------------------------

Enable variable sharing between scenarios:

.. code-block:: kotlin

    val config = Configuration().apply {
        baseUrl = "https://api.example.com"
        shareVariablesAcrossScenarios = true
    }

    val runner = ScenarioRunner(specRegistry, config)

    // Scenario 1: Create resource and extract ID
    val createScenario = suite.scenario("Create pet") {
        `when`("I create a pet") {
            call("createPet") {
                body(mapOf("name" to "Fluffy"))
            }
            extractTo("petId", "$.id")
        }
    }

    // Scenario 2: Use extracted ID
    val getScenario = suite.scenario("Get pet") {
        `when`("I get the pet") {
            call("getPetById") {
                pathParam("petId", $$"${petId}")
            }
        }
    }

    // Variables persist across scenarios
    val result = runner.run(listOf(createScenario, getScenario))

Plugin Integration
------------------

Register plugins for lifecycle hooks:

.. code-block:: kotlin

    import io.github.ktakashi.lemoncheck.plugin.PluginRegistry

    val pluginRegistry = PluginRegistry()

    // Register built-in plugins
    pluginRegistry.registerByName("report:text")
    pluginRegistry.registerByName("report:html")

    // Register custom plugins
    pluginRegistry.register(MyCustomPlugin())

    // Create runner with plugins
    val runner = ScenarioRunner(
        specRegistry = specRegistry,
        configuration = config,
        pluginRegistry = pluginRegistry
    )

Fragment Support
----------------

Load and use fragments:

.. code-block:: kotlin

    import io.github.ktakashi.lemoncheck.model.FragmentRegistry
    import io.github.ktakashi.lemoncheck.parser.FragmentParser

    // Create fragment registry
    val fragmentRegistry = FragmentRegistry()

    // Load fragments from files
    val parser = FragmentParser()
    val authFragment = parser.parse(File("fragments/auth.fragment").readText())
    fragmentRegistry.register("authenticate", authFragment)

    // Create runner with fragments
    val runner = ScenarioRunner(
        specRegistry = specRegistry,
        configuration = config,
        fragmentRegistry = fragmentRegistry
    )

Loading Scenarios from Files
----------------------------

Parse and run ``.scenario`` files:

.. code-block:: kotlin

    import io.github.ktakashi.lemoncheck.parser.ScenarioParser

    val parser = ScenarioParser()

    // Parse a scenario file
    val scenarios = parser.parse(File("scenarios/petstore.scenario").readText())

    // Run all scenarios from the file
    val result = runner.run(scenarios)

Complete Example
----------------

Here's a complete example of a standalone test runner:

.. code-block:: kotlin

    import io.github.ktakashi.lemoncheck.config.Configuration
    import io.github.ktakashi.lemoncheck.model.ResultStatus
    import io.github.ktakashi.lemoncheck.openapi.SpecRegistry
    import io.github.ktakashi.lemoncheck.parser.ScenarioParser
    import io.github.ktakashi.lemoncheck.plugin.PluginRegistry
    import io.github.ktakashi.lemoncheck.runner.ScenarioRunner
    import java.io.File
    import kotlin.system.exitProcess

    fun main(args: Array<String>) {
        // Configuration
        val config = Configuration().apply {
            baseUrl = System.getenv("API_BASE_URL") ?: "http://localhost:8080"
            timeout(30)
            shareVariablesAcrossScenarios = true
        }

        // Load OpenAPI spec
        val specRegistry = SpecRegistry()
        specRegistry.registerDefault("api-spec.yaml")

        // Set up plugins
        val pluginRegistry = PluginRegistry()
        pluginRegistry.registerByName("report:text")

        // Create runner
        val runner = ScenarioRunner(
            specRegistry = specRegistry,
            configuration = config,
            pluginRegistry = pluginRegistry
        )

        // Parse scenario files
        val parser = ScenarioParser()
        val scenarioDir = File("scenarios")
        val scenarios = scenarioDir.listFiles { f -> f.extension == "scenario" }
            ?.flatMap { parser.parse(it.readText()) }
            ?: emptyList()

        println("Running ${scenarios.size} scenarios...")

        // Execute with progress
        val result = runner.run(scenarios) { scenario, scenarioResult ->
            val icon = when (scenarioResult.status) {
                ResultStatus.PASSED -> "✓"
                ResultStatus.FAILED -> "✗"
                ResultStatus.ERROR -> "!"
                ResultStatus.SKIPPED -> "-"
            }
            println("$icon ${scenario.name}")
        }

        // Print summary
        println()
        println("=" .repeat(50))
        println("Results: ${result.passed} passed, ${result.failed} failed, " +
                "${result.errors} errors, ${result.skipped} skipped")
        println("Duration: ${result.duration.toMillis()}ms")
        println("=" .repeat(50))

        // Exit with appropriate code
        exitProcess(if (result.status == ResultStatus.PASSED) 0 else 1)
    }

CI/CD Integration
-----------------

Use the standalone runner in CI/CD pipelines:

**GitHub Actions:**

.. code-block:: yaml

    - name: Run API Tests
      run: |
        ./gradlew :api-tests:run --args="--baseUrl=${{ vars.API_URL }}"
      env:
        API_KEY: ${{ secrets.API_KEY }}

**Gradle Task:**

.. code-block:: kotlin

    // build.gradle.kts
    tasks.register<JavaExec>("runApiTests") {
        mainClass.set("com.example.ApiTestRunnerKt")
        classpath = sourceSets["test"].runtimeClasspath
        args = listOf(
            "--baseUrl=${project.findProperty("apiUrl") ?: "http://localhost:8080"}"
        )
    }

Run with: ``./gradlew runApiTests -PapiUrl=https://staging.example.com``

API Reference
-------------

ScenarioRunner
^^^^^^^^^^^^^^

+----------------------------------------------------+------------------------------------------------+
| Constructor                                        | Description                                    |
+====================================================+================================================+
| ``ScenarioRunner(specRegistry, configuration)``    | Basic runner                                   |
+----------------------------------------------------+------------------------------------------------+
| ``ScenarioRunner(specRegistry, configuration,      | Runner with plugins                            |
| pluginRegistry)``                                  |                                                |
+----------------------------------------------------+------------------------------------------------+
| ``ScenarioRunner(specRegistry, configuration,      | Full runner with plugins and fragments         |
| pluginRegistry, fragmentRegistry)``                |                                                |
+----------------------------------------------------+------------------------------------------------+

**Methods:**

+------------------------------------+------------------------------------------------------+
| Method                             | Description                                          |
+====================================+======================================================+
| ``run(scenarios)``                 | Execute scenarios and return aggregated result       |
+------------------------------------+------------------------------------------------------+
| ``run(scenarios, callback)``       | Execute with progress callback                       |
+------------------------------------+------------------------------------------------------+
| ``run(scenario)``                  | Execute single scenario                              |
+------------------------------------+------------------------------------------------------+
| ``beginExecution()``               | Start lifecycle (calls plugin hooks)                 |
+------------------------------------+------------------------------------------------------+
| ``executeScenario(scenario)``      | Execute one scenario (no lifecycle hooks)            |
+------------------------------------+------------------------------------------------------+
| ``endExecution()``                 | End lifecycle (triggers reports)                     |
+------------------------------------+------------------------------------------------------+

RunResult
^^^^^^^^^

+----------------------+--------------------------------------------+
| Property             | Description                                |
+======================+============================================+
| ``status``           | Overall result status                      |
+----------------------+--------------------------------------------+
| ``duration``         | Total execution duration                   |
+----------------------+--------------------------------------------+
| ``scenarioResults``  | List of (Scenario, ScenarioResult) pairs   |
+----------------------+--------------------------------------------+
| ``passed``           | Number of passed scenarios                 |
+----------------------+--------------------------------------------+
| ``failed``           | Number of failed scenarios                 |
+----------------------+--------------------------------------------+
| ``skipped``          | Number of skipped scenarios                |
+----------------------+--------------------------------------------+
| ``errors``           | Number of error scenarios                  |
+----------------------+--------------------------------------------+

ScenarioResult
^^^^^^^^^^^^^^

+----------------------+--------------------------------------------+
| Property             | Description                                |
+======================+============================================+
| ``scenario``         | The executed scenario                      |
+----------------------+--------------------------------------------+
| ``status``           | Result status                              |
+----------------------+--------------------------------------------+
| ``stepResults``      | Results for each step                      |
+----------------------+--------------------------------------------+
| ``startTime``        | Execution start time                       |
+----------------------+--------------------------------------------+
| ``duration``         | Execution duration                         |
+----------------------+--------------------------------------------+

See Also
--------

- :doc:`kotlin-dsl` - Kotlin DSL for programmatic scenarios
- :doc:`plugins` - Plugin system for lifecycle hooks
- :doc:`fragments` - Reusable step sequences
