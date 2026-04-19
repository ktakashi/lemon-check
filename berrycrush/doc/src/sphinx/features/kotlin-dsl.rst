Kotlin DSL
==========

BerryCrush provides a type-safe Kotlin DSL for defining API test scenarios programmatically.
This approach offers IDE autocompletion, compile-time type safety, and the full power of Kotlin
for complex test logic.

Overview
--------

The Kotlin DSL provides an alternative to text-based ``.scenario`` files, allowing you to:

- Define scenarios with compile-time type safety
- Use Kotlin expressions for dynamic values
- Leverage IDE features like autocompletion and refactoring
- Mix declarative scenario structure with imperative logic

Quick Start
-----------

Here's a minimal example:

.. code-block:: kotlin

    import org.berrycrush.berrycrush.dsl.berryCrush

    val suite = berryCrush("petstore.yaml") {
        baseUrl = "https://api.example.com"
    }

    @ScenarioTest Annotation
    -------------------------
            call("listPets")
    The ``@ScenarioTest`` annotation provides a cleaner way to define scenarios

        afterwards("I receive a list") {
            statusCode(200)
            bodyArrayNotEmpty("$.pets")
        }
    }

Creating a Test Suite
---------------------

Single Spec
        import org.berrycrush.junit.ScenarioTest

For APIs with a single OpenAPI specification:

.. code-block:: kotlin

            @ScenarioTest
        baseUrl = "https://api.example.com"
        timeout(30) // seconds
        header("Accept", "application/json")
    }

Multi-Spec
^^^^^^^^^^

For APIs with multiple OpenAPI specifications:

            @ScenarioTest

    val suite = berryCrush {
        spec("petstore", "petstore.yaml") {
            baseUrl = "https://petstore.example.com"
        }

        spec("auth", "auth.yaml") {
            baseUrl = "https://auth.example.com"
        }

        configure {
            timeout(60)
        }
    }

Defining Scenarios
------------------
    2. Methods annotated with ``@ScenarioTest`` that return ``Scenario`` are automatically discovered
    3. Each ``@ScenarioTest`` method becomes a separate test in the JUnit test tree
^^^^^^^^^^^^^^^

Scenarios follow the Given-When-Then pattern:

.. code-block:: kotlin
    **Using @ScenarioTest (recommended for most cases):**
    suite.scenario("Create and retrieve a pet") {
        given("the API is available") {
            // Optional setup
        }

            @ScenarioTest
            call("createPet") {
                body(mapOf(
                    "name" to "Fluffy",
                    "status" to "available"
                ))
            }
        }

        afterwards("the pet is created") {
            statusCode(201)
            bodyEquals("$.name", "Fluffy")
        }
    }

.. note::
    The ``whenever`` keyword is the preferred alternative to ``when``, which requires backticks.

Scenario with Tags
^^^^^^^^^^^^^^^^^^
    Use ``@ScenarioTest`` when you want:
Add tags for filtering and organization:

.. code-block:: kotlin

    suite.scenario("Smoke test", tags = setOf("smoke", "critical")) {
        whenever("I request the health endpoint") {
            call("healthCheck")
        }

        afterwards("the service is healthy") {
            statusCode(200)
        }
    }

API Calls
---------

Basic Call
^^^^^^^^^^

Call an operation by its OpenAPI ``operationId``:
        Methods annotated with ``@ScenarioTest`` must:
.. code-block:: kotlin

    whenever("I get a pet") {
        call("getPetById") {
            pathParam("petId", 123)
        }
    }

With Query Parameters
^^^^^^^^^^^^^^^^^^^^^

.. code-block:: kotlin

    call("findPetsByStatus") {
        queryParam("status", "available")
        queryParam("limit", 10)
    }

With Headers
^^^^^^^^^^^^

.. code-block:: kotlin

    call("listPets") {
        header("Accept", "application/json")
        header("X-Api-Key", "my-api-key")
    }

With Request Body
^^^^^^^^^^^^^^^^^

Using a map (recommended):

.. code-block:: kotlin

    call("createPet") {
        body(mapOf(
            "name" to "Fluffy",
            "category" to "cat",
            "status" to "available"
        ))
    }

Using a JSON string:

.. code-block:: kotlin

    call("createPet") {
        body("""{"name": "Fluffy", "status": "available"}""")
    }

Authentication Shortcuts
^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: kotlin

    // Bearer token
    call("protectedEndpoint") {
        bearerToken("my-jwt-token")
    }

    // Basic auth
    call("protectedEndpoint") {
        basicAuth("username", "password")
    }

    // API key
    call("protectedEndpoint") {
        apiKey("X-Api-Key", "secret-key")
    }

Multi-Spec Calls
^^^^^^^^^^^^^^^^

Call operations from a specific spec:

.. code-block:: kotlin

    whenever("I authenticate") {
        using("auth")  // Switch to auth spec
        call("login") {
            body(mapOf("username" to "test", "password" to "secret"))
        }
    }

Variable Extraction
-------------------

Extract values from responses for use in subsequent steps:

.. code-block:: kotlin

    whenever("I create a pet") {
        call("createPet") {
            body(mapOf("name" to "Fluffy"))
        }
        extractTo("petId", "$.id")
        extractTo("petName", "$.name")
    }

    and("I retrieve the pet") {
        call("getPetById") {
            pathParam("petId", $$"${petId}")  // Use extracted variable
        }
    }

Cross-Scenario Variable Sharing
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Variables can be shared across scenarios when enabled in configuration:

.. code-block:: kotlin

    val suite = berryCrush("petstore.yaml") {
        shareVariablesAcrossScenarios = true
    }

    suite.scenario("Create a pet") {
        whenever("I create a pet") {
            call("createPet") { body(mapOf("name" to "Fluffy")) }
            extractTo("petId", "$.id")
        }
    }

    suite.scenario("Use the created pet") {
        whenever("I get the pet from the previous scenario") {
            call("getPetById") {
                pathParam("petId", $$"${petId}")
            }
        }
    }

Assertions
----------

Status Code
^^^^^^^^^^^

.. code-block:: kotlin

    afterwards("the response is successful") {
        statusCode(200)           // Exact match
        statusCode(200..299)      // Range match
    }

Body Assertions
^^^^^^^^^^^^^^^

.. code-block:: kotlin

    afterwards("the response body is correct") {
        bodyContains("Fluffy")                    // Contains substring
        bodyEquals("$.name", "Fluffy")            // JSONPath equals
        bodyMatches("$.email", ".*@.*\\.com")     // JSONPath matches regex
        bodyArrayNotEmpty("$.pets")               // Array is not empty
        bodyArraySize("$.pets", 10)               // Array has specific size
    }

Header Assertions
^^^^^^^^^^^^^^^^^

.. code-block:: kotlin

    afterwards("the headers are correct") {
        headerExists("Content-Type")
        headerEquals("Content-Type", "application/json")
    }

Schema Validation
^^^^^^^^^^^^^^^^^

.. code-block:: kotlin

    afterwards("the response matches the OpenAPI schema") {
        matchesSchema()
    }

Custom Assertions
^^^^^^^^^^^^^^^^^

Define programmatic assertions with full access to the test execution context:

.. code-block:: kotlin

    suite.scenario("Custom assertion example") {
        whenever("I get user profile") {
            call("getUserProfile") {
                pathParam("userId", 123)
            }
        }

        afterwards("the response is valid") {
            statusCode(200)

            // Custom assertion with programmatic logic
            assert("user age is valid") { ctx ->
                val body = ctx.responseBody ?: error("No response body")
                val age = """\"age\"\s*:\s*(\d+)""".toRegex().find(body)
                    ?.groupValues?.get(1)?.toInt() ?: error("Age not found")

                require(age >= 0) { "Age must be non-negative: $age" }
                require(age <= 150) { "Age must be realistic: $age" }

                // Store extracted value for later use
                ctx.set("userAge", age)
            }

            // Use the extracted value in another assertion
            assert("verify age was extracted") { ctx ->
                val age = ctx.get<Int>("userAge")
                require(age != null) { "Age should have been extracted" }
            }
        }
    }

**TestExecutionContext** provides:

+----------------------+-----------------------------------------------------------+
| Property/Method      | Description                                               |
+======================+===========================================================+
| ``response``         | The current HTTP response                                 |
+----------------------+-----------------------------------------------------------+
| ``request``          | The current HTTP request                                  |
+----------------------+-----------------------------------------------------------+
| ``statusCode``       | The response status code                                  |
+----------------------+-----------------------------------------------------------+
| ``responseBody``     | The response body as string                               |
+----------------------+-----------------------------------------------------------+
| ``variables``        | Read-only view of all variables                           |
+----------------------+-----------------------------------------------------------+
| ``get<T>(key)``      | Get a variable with type casting                          |
+----------------------+-----------------------------------------------------------+
| ``set(key, value)``  | Store a value for subsequent assertions                   |
+----------------------+-----------------------------------------------------------+
| ``extract(name, v)`` | Store an extracted value for parameter binding            |
+----------------------+-----------------------------------------------------------+

Conditionals
------------

Execute different assertions based on runtime conditions:

.. code-block:: kotlin

    suite.scenario("Conditional assertion example") {
        whenever("I search for a pet") {
            call("findPetsByStatus") {
                queryParam("status", "available")
            }
        }

        afterwards("I handle the response appropriately") {
            // Conditional based on status code
            conditional({ ctx -> ctx.statusCode == 200 }) {
                // Success case
                bodyArrayNotEmpty("$")
            } orElse {
                // Error case
                bodyContains("error")
            }

            // Conditional based on response content
            conditional({ ctx ->
                ctx.responseBody?.contains("premium") == true
            }) {
                // Premium pets have extra fields
                assert("verify premium fields") { ctx ->
                    val body = ctx.responseBody ?: ""
                    require(body.contains("premiumFeatures"))
                }
            }
        }
    }

The ``conditional`` block:

- Takes a predicate function ``(TestExecutionContext) -> Boolean``
- Executes the first block if the predicate returns ``true``
- Executes the ``orElse`` block (if present) if the predicate returns ``false``
- Can be used multiple times in the same step
- Supports nested standard assertions (``statusCode``, ``bodyEquals``, etc.)

Scenario File Compatibility
---------------------------

For consistency with ``.scenario`` file keywords, the DSL provides aliases:

+-------------+------------------+------------------------------------------------+
| Alias       | Primary Method   | Description                                    |
+=============+==================+================================================+
| ``when``    | ``whenever``     | Matches ``when`` keyword in scenario files     |
+-------------+------------------+------------------------------------------------+
| ``then``    | ``afterwards``   | Matches ``then`` keyword in scenario files     |
+-------------+------------------+------------------------------------------------+
| ``but``     | ``otherwise``    | Matches ``but`` keyword in scenario files      |
+-------------+------------------+------------------------------------------------+

These aliases allow you to use the same keywords in Kotlin DSL that you would use
in ``.scenario`` files:

.. code-block:: kotlin

    suite.scenario("Using scenario file keywords") {
        `when`("I request a pet") {       // Same as whenever
            call("getPetById") {
                pathParam("petId", 1)
            }
        }

        then("I receive the pet") {       // Same as afterwards
            statusCode(200)
            bodyEquals("$.name", "Fluffy")
        }

        but("no error is returned") {     // Same as otherwise
            bodyEquals("$.error", null)
        }
    }

.. note::
    The ``when`` keyword requires backticks (``` `when` ```) because it's a Kotlin
    reserved word. The primary methods (``whenever``, ``afterwards``, ``otherwise``)
    don't have this limitation.

Scenario Outlines
-----------------

Create parameterized scenarios with multiple data sets:

.. code-block:: kotlin

    suite.scenarioOutline("Filter pets by status: <status>") {
        whenever("I filter pets") {
            call("findPetsByStatus") {
                queryParam("status", "<status>")
            }
        }

        afterwards("I receive matching pets") {
            statusCode(200)
        }

        examples(
            row("status" to "available"),
            row("status" to "pending"),
            row("status" to "sold")
        )
    }

Fragments (Reusable Steps)
--------------------------

Define reusable step sequences:

.. code-block:: kotlin

    // Define a fragment
    val authenticateFragment = suite.fragment("authenticate") {
        given("I have credentials") {
            call("login") {
                body(mapOf("username" to "test", "password" to "secret"))
            }
            extractTo("authToken", "$.token")
        }
    }

    // Include in scenarios
    suite.scenario("Access protected resource") {
        include(authenticateFragment)

        whenever("I access the protected endpoint") {
            call("protectedResource") {
                bearerToken($$"${authToken}")
            }
        }

        afterwards("I get the data") {
            statusCode(200)
        }
    }

JUnit Integration
-----------------

Use ``BerryCrushExtension`` for JUnit integration:

.. code-block:: kotlin

    import org.berrycrush.junit.BerryCrushExtension
    import org.berrycrush.junit.BerryCrushSpec
    import org.berrycrush.dsl.BerryCrushSuite
    import org.berrycrush.executor.BerryCrushScenarioExecutor
    import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.extension.ExtendWith

    @ExtendWith(BerryCrushExtension::class)
    @BerryCrushSpec("petstore.yaml")
    class PetstoreKotlinScenarios {

        @Test
        fun `list all pets`(suite: BerryCrushSuite, executor: BerryCrushScenarioExecutor) {
            suite.configure {
                baseUrl = "https://api.example.com"
            }
            
            val scenario = suite.scenario("List all pets") {
                whenever("I request pets") {
                    call("listPets")
                }
                afterwards("I get a list") {
                    statusCode(200)
                }
            }
            
            executor.execute(scenario)
        }
    }

Complete Example
----------------

.. code-block:: kotlin

    @ExtendWith(BerryCrushExtension::class)
    @BerryCrushSpec("petstore.yaml")
    class PetstoreApiTests {

        @Test
        fun `list available pets`(suite: BerryCrushSuite, executor: BerryCrushScenarioExecutor) {
            suite.configure {
                baseUrl = "https://petstore.swagger.io/v2"
                timeout(30)
                header("Accept", "application/json")
            }

            val scenario = suite.scenario("List available pets") {
                whenever("I request available pets") {
                    call("findPetsByStatus") {
                        queryParam("status", "available")
                    }
                }

                afterwards("I receive a list") {
                    statusCode(200)
                    bodyArrayNotEmpty("$")
                }
            }

            executor.execute(scenario)
        }
    }

API Reference
-------------

BerryCrushSuite
^^^^^^^^^^^^^^^

+------------------------------+-------------------------------------------------------------+
| Method                       | Description                                                 |
+==============================+=============================================================+
| ``spec(path, config)``       | Register a single OpenAPI spec                              |
+------------------------------+-------------------------------------------------------------+
| ``spec(name, path, config)`` | Register a named spec (multi-spec)                          |
+------------------------------+-------------------------------------------------------------+
| ``configure(block)``         | Configure the test suite                                    |
+------------------------------+-------------------------------------------------------------+
| ``scenario(name, tags, block)`` | Define a scenario                                        |
+------------------------------+-------------------------------------------------------------+
| ``scenarioOutline(name, tags, block)`` | Define a parameterized scenario                   |
+------------------------------+-------------------------------------------------------------+
| ``fragment(name, block)``    | Define a reusable fragment                                  |
+------------------------------+-------------------------------------------------------------+

ScenarioScope
^^^^^^^^^^^^^

+------------------------+------------------------------------------------+
| Method                 | Description                                    |
+========================+================================================+
| ``given(desc, block)`` | Define a precondition step                     |
+------------------------+------------------------------------------------+
| ``whenever(desc, block)`` | Define an action step                       |
+------------------------+------------------------------------------------+
| ``afterwards(desc, block)`` | Define an assertion step                  |
+------------------------+------------------------------------------------+
| ``and(desc, block)``   | Continue previous step type                    |
+------------------------+------------------------------------------------+
| ``otherwise(desc, block)`` | Define an exception/negative case           |
+------------------------+------------------------------------------------+
| ``include(fragment)``  | Include a fragment's steps                     |
+------------------------+------------------------------------------------+

StepScope
^^^^^^^^^

+--------------------------------+------------------------------------------------+
| Method                         | Description                                    |
+================================+================================================+
| ``call(operationId, block)``   | Call an API operation                          |
+--------------------------------+------------------------------------------------+
| ``using(specName)``            | Switch to a named spec                         |
+--------------------------------+------------------------------------------------+
| ``extractTo(varName, jsonPath)`` | Extract value to variable                    |
+--------------------------------+------------------------------------------------+
| ``statusCode(expected)``       | Assert status code                             |
+--------------------------------+------------------------------------------------+
| ``bodyContains(substring)``    | Assert body contains text                      |
+--------------------------------+------------------------------------------------+
| ``bodyEquals(path, expected)`` | Assert JSONPath value                          |
+--------------------------------+------------------------------------------------+
| ``bodyMatches(path, regex)``   | Assert JSONPath matches regex                  |
+--------------------------------+------------------------------------------------+
| ``bodyArraySize(path, size)``  | Assert array size                              |
+--------------------------------+------------------------------------------------+
| ``bodyArrayNotEmpty(path)``    | Assert array is not empty                      |
+--------------------------------+------------------------------------------------+
| ``headerExists(name)``         | Assert header exists                           |
+--------------------------------+------------------------------------------------+
| ``headerEquals(name, value)``  | Assert header value                            |
+--------------------------------+------------------------------------------------+
| ``matchesSchema()``            | Assert response matches OpenAPI schema         |
+--------------------------------+------------------------------------------------+

CallScope
^^^^^^^^^

+--------------------------------+------------------------------------------------+
| Method                         | Description                                    |
+================================+================================================+
| ``pathParam(name, value)``     | Set a path parameter                           |
+--------------------------------+------------------------------------------------+
| ``queryParam(name, value)``    | Set a query parameter                          |
+--------------------------------+------------------------------------------------+
| ``header(name, value)``        | Set a header                                   |
+--------------------------------+------------------------------------------------+
| ``body(content)``              | Set request body (String or Map)               |
+--------------------------------+------------------------------------------------+
| ``bearerToken(token)``         | Add Bearer authentication                      |
+--------------------------------+------------------------------------------------+
| ``basicAuth(user, pass)``      | Add Basic authentication                       |
+--------------------------------+------------------------------------------------+
| ``apiKey(key)``                | Add API key header                             |
+--------------------------------+------------------------------------------------+
| ``autoAssert(enabled)``        | Enable/disable auto-assertions                 |
+--------------------------------+------------------------------------------------+

JUnit 5 Integration with BerryCrushExtension
--------------------------------------------

For JUnit 5 tests, ``BerryCrushExtension`` provides dependency injection of the suite,
configuration, and executor. This is the recommended approach for integrating with JUnit 5.

Basic Usage
^^^^^^^^^^^

.. code-block:: kotlin

    import org.berrycrush.junit.BerryCrushExtension
    import org.berrycrush.junit.BerryCrushSpec
    import org.junit.jupiter.api.Test
    import org.junit.jupiter.api.extension.ExtendWith

    @ExtendWith(BerryCrushExtension::class)
    @BerryCrushSpec("petstore.yaml", baseUrl = "http://localhost:8080/api")
    class PetApiTest {
        @Test
        fun `list all pets`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario = suite.scenario("List all pets") {
                whenever("I request all pets") {
                    call("listPets")
                }
                afterwards("I get a successful response") {
                    statusCode(200)
                }
            }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }
    }

Spring Boot with Dynamic Port
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

When using Spring Boot's random port, inject ``BerryCrushConfiguration`` in ``@BeforeEach``
to set the ``baseUrl`` dynamically. Inject ``BerryCrushSuite`` and ``BerryCrushScenarioExecutor``
directly in ``@Test`` methods:

.. code-block:: kotlin

    import org.berrycrush.config.BerryCrushConfiguration
    import org.berrycrush.dsl.BerryCrushSuite
    import org.berrycrush.executor.BerryCrushScenarioExecutor
    import org.berrycrush.junit.BerryCrushExtension
    import org.berrycrush.junit.BerryCrushSpec
    import org.junit.jupiter.api.*
    import org.junit.jupiter.api.extension.ExtendWith
    import org.springframework.boot.test.context.SpringBootTest
    import org.springframework.boot.test.web.server.LocalServerPort

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @ExtendWith(BerryCrushExtension::class)
    @BerryCrushSpec("classpath:/petstore.yaml")
    class PetApiTest {
        @LocalServerPort
        private var port: Int = 0

        @BeforeEach
        fun setup(config: BerryCrushConfiguration) {
            // Set dynamic port - Configuration is shared, changes affect executor
            config.baseUrl = "http://localhost:$port/api"
        }

        @Test
        fun `list all pets`(
            suite: BerryCrushSuite,
            executor: BerryCrushScenarioExecutor,
        ) {
            val scenario = suite.scenario("List all pets") {
                whenever("I request all pets") {
                    call("listPets")
                }
                afterwards("I get a successful response") {
                    statusCode(200)
                }
            }

            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }
    }

Nested Test Classes
^^^^^^^^^^^^^^^^^^^

``BerryCrushExtension`` automatically shares the suite with ``@Nested`` inner classes.
Configuration set in the outer class's ``@BeforeEach`` is inherited by nested classes:

.. code-block:: kotlin

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @ExtendWith(BerryCrushExtension::class)
    @BerryCrushSpec("classpath:/petstore.yaml")
    class PetApiTest {
        @LocalServerPort
        private var port: Int = 0

        @BeforeEach
        fun setup(config: BerryCrushConfiguration) {
            config.baseUrl = "http://localhost:$port/api"
        }

        @Nested
        inner class GetPets {
            @Test
            fun `should return list`(
                suite: BerryCrushSuite,
                executor: BerryCrushScenarioExecutor,
            ) {
                val scenario = suite.scenario("List pets") { ... }
                val result = executor.execute(scenario)
                assertEquals(ResultStatus.PASSED, result.status)
            }
        }
    }

Supported Parameter Types
^^^^^^^^^^^^^^^^^^^^^^^^^

The extension can inject the following types into ``@BeforeEach`` or ``@Test`` methods:

- ``BerryCrushSuite``: The test suite containing the OpenAPI spec
- ``BerryCrushConfiguration``: Configuration object for setting ``baseUrl``, ``timeout``, etc.
- ``BerryCrushScenarioExecutor``: Executor for running scenarios

.. note::
    The ``BerryCrushConfiguration`` object is shared between the suite and executor.
    Changes to the configuration affect the executor, even after the executor is created.

@ScenarioTest Annotation
-------------------------

The ``@ScenarioTest`` annotation provides a cleaner way to define scenarios
as JUnit test methods. Instead of manually calling ``executor.execute()``, you can
simply annotate a method that returns a ``Scenario`` and BerryCrush will automatically
discover and execute it.

Basic Usage
^^^^^^^^^^^

.. code-block:: kotlin

    import org.berrycrush.dsl.BerryCrushSuite
    import org.berrycrush.junit.BerryCrushSpec
    import org.berrycrush.junit.ScenarioTest
    import org.berrycrush.model.Scenario

    @BerryCrushSpec("petstore.yaml", baseUrl = "http://localhost:8080/api")
    class PetApiTest {

        @ScenarioTest
        fun `list all pets`(): Scenario =
            BerryCrushSuite.create().scenario("List all pets") {
                whenever("I request all pets") {
                    call("listPets")
                }
                afterwards("I get a successful response") {
                    statusCode(200)
                }
            }

        @ScenarioTest
        fun createPet(): Scenario =
            BerryCrushSuite.create().scenario("Create a new pet") {
                whenever("I create a pet") {
                    call("createPet") {
                        body(mapOf("name" to "Fluffy"))
                    }
                }
                afterwards("the pet is created") {
                    statusCode(201)
                }
            }
    }

How It Works
^^^^^^^^^^^^

1. The BerryCrush test engine discovers classes annotated with ``@BerryCrushSpec``
2. Methods annotated with ``@ScenarioTest`` that return ``Scenario`` are automatically discovered
3. Each ``@ScenarioTest`` method becomes a separate test in the JUnit test tree
4. The test engine executes each scenario and reports results to JUnit

Comparison with BerryCrushExtension
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

**Using @ScenarioTest (recommended for most cases):**

.. code-block:: kotlin

    @BerryCrushSpec("petstore.yaml")
    class PetApiTest {
        @ScenarioTest
        fun `list pets`(): Scenario =
            BerryCrushSuite.create().scenario("List pets") { ... }
    }

**Using BerryCrushExtension (for more control):**

.. code-block:: kotlin

    @ExtendWith(BerryCrushExtension::class)
    @BerryCrushSpec("petstore.yaml")
    class PetApiTest {
        @Test
        fun `list pets`(suite: BerryCrushSuite, executor: BerryCrushScenarioExecutor) {
            val scenario = suite.scenario("List pets") { ... }
            val result = executor.execute(scenario)
            assertEquals(ResultStatus.PASSED, result.status)
        }
    }

Use ``@ScenarioTest`` when you want:

- Simpler, more concise test code
- Automatic test discovery and execution
- Less boilerplate code

Use ``BerryCrushExtension`` when you need:

- Access to the ``ScenarioResult`` for custom assertions
- Dynamic configuration per test (e.g., Spring Boot's random port)
- Complex test setup that requires injected parameters

Method Naming Conventions
^^^^^^^^^^^^^^^^^^^^^^^^^

The method name is converted to a display name in the JUnit test tree:

- ``createPet`` → "Create Pet"
- ``listAllPets`` → "List All Pets"
- ``` `list all pets` ``` → "List all pets" (backtick-wrapped names)

.. note::
    Methods annotated with ``@ScenarioTest`` must:
    - Return ``Scenario`` or a subtype
    - Have no parameters (or only ``BerryCrushSuite`` if needed)
    - Be public

See Also
--------

- :doc:`/quickstart` - Getting started with BerryCrush
- :doc:`standalone-runner` - Running scenarios without JUnit
- :doc:`fragments` - Creating reusable step sequences
- :doc:`custom-steps` - Defining custom step implementations
