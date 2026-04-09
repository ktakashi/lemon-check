Kotlin DSL
==========

LemonCheck provides a type-safe Kotlin DSL for defining API test scenarios programmatically.
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

    import io.github.ktakashi.lemoncheck.dsl.lemonCheck

    val suite = lemonCheck("petstore.yaml") {
        baseUrl = "https://api.example.com"
    }

    val scenario = suite.scenario("List all pets") {
        `when`("I request all pets") {
            call("listPets")
        }

        then("I receive a list") {
            statusCode(200)
            bodyArrayNotEmpty("$.pets")
        }
    }

Creating a Test Suite
---------------------

Single Spec
^^^^^^^^^^^

For APIs with a single OpenAPI specification:

.. code-block:: kotlin

    val suite = lemonCheck("petstore.yaml") {
        baseUrl = "https://api.example.com"
        timeout(30) // seconds
        header("Accept", "application/json")
    }

Multi-Spec
^^^^^^^^^^

For APIs with multiple OpenAPI specifications:

.. code-block:: kotlin

    val suite = lemonCheck {
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

Basic Structure
^^^^^^^^^^^^^^^

Scenarios follow the Given-When-Then pattern:

.. code-block:: kotlin

    suite.scenario("Create and retrieve a pet") {
        given("the API is available") {
            // Optional setup
        }

        `when`("I create a new pet") {
            call("createPet") {
                body(mapOf(
                    "name" to "Fluffy",
                    "status" to "available"
                ))
            }
        }

        then("the pet is created") {
            statusCode(201)
            bodyEquals("$.name", "Fluffy")
        }
    }

.. note::
    The ``when`` keyword requires backticks (``` `when` ```) because it's a Kotlin reserved word.

Scenario with Tags
^^^^^^^^^^^^^^^^^^

Add tags for filtering and organization:

.. code-block:: kotlin

    suite.scenario("Smoke test", tags = setOf("smoke", "critical")) {
        `when`("I request the health endpoint") {
            call("healthCheck")
        }

        then("the service is healthy") {
            statusCode(200)
        }
    }

API Calls
---------

Basic Call
^^^^^^^^^^

Call an operation by its OpenAPI ``operationId``:

.. code-block:: kotlin

    `when`("I get a pet") {
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

    `when`("I authenticate") {
        using("auth")  // Switch to auth spec
        call("login") {
            body(mapOf("username" to "test", "password" to "secret"))
        }
    }

Variable Extraction
-------------------

Extract values from responses for use in subsequent steps:

.. code-block:: kotlin

    `when`("I create a pet") {
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

    val suite = lemonCheck("petstore.yaml") {
        shareVariablesAcrossScenarios = true
    }

    suite.scenario("Create a pet") {
        `when`("I create a pet") {
            call("createPet") { body(mapOf("name" to "Fluffy")) }
            extractTo("petId", "$.id")
        }
    }

    suite.scenario("Use the created pet") {
        `when`("I get the pet from the previous scenario") {
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

    then("the response is successful") {
        statusCode(200)           // Exact match
        statusCode(200..299)      // Range match
    }

Body Assertions
^^^^^^^^^^^^^^^

.. code-block:: kotlin

    then("the response body is correct") {
        bodyContains("Fluffy")                    // Contains substring
        bodyEquals("$.name", "Fluffy")            // JSONPath equals
        bodyMatches("$.email", ".*@.*\\.com")     // JSONPath matches regex
        bodyArrayNotEmpty("$.pets")               // Array is not empty
        bodyArraySize("$.pets", 10)               // Array has specific size
    }

Header Assertions
^^^^^^^^^^^^^^^^^

.. code-block:: kotlin

    then("the headers are correct") {
        headerExists("Content-Type")
        headerEquals("Content-Type", "application/json")
    }

Schema Validation
^^^^^^^^^^^^^^^^^

.. code-block:: kotlin

    then("the response matches the OpenAPI schema") {
        matchesSchema()
    }

Scenario Outlines
-----------------

Create parameterized scenarios with multiple data sets:

.. code-block:: kotlin

    suite.scenarioOutline("Filter pets by status: <status>") {
        `when`("I filter pets") {
            call("findPetsByStatus") {
                queryParam("status", "<status>")
            }
        }

        then("I receive matching pets") {
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

        `when`("I access the protected endpoint") {
            call("protectedResource") {
                bearerToken($$"${authToken}")
            }
        }

        then("I get the data") {
            statusCode(200)
        }
    }

JUnit Integration
-----------------

Extend ``ScenarioTest`` for JUnit integration:

.. code-block:: kotlin

    import io.github.ktakashi.lemoncheck.junit.LemonCheckSpec
    import io.github.ktakashi.lemoncheck.junit.ScenarioTest

    @LemonCheckSpec("petstore.yaml")
    class PetstoreKotlinScenarios : ScenarioTest() {
        
        override fun configureSuite() {
            configure {
                baseUrl = "https://api.example.com"
                timeout(30)
            }
        }

        override fun defineScenarios() {
            scenario("List all pets") {
                `when`("I request pets") {
                    call("listPets")
                }
                then("I get a list") {
                    statusCode(200)
                }
            }
        }
    }

Complete Example
----------------

.. code-block:: kotlin

    @LemonCheckSpec("petstore.yaml")
    class PetstoreApiTests : ScenarioTest() {

        override fun configureSuite() {
            configure {
                baseUrl = "https://petstore.swagger.io/v2"
                timeout(30)
                header("Accept", "application/json")
                shareVariablesAcrossScenarios = true
            }
        }

        override fun defineScenarios() {
            // Simple scenario
            scenario("List available pets") {
                `when`("I request available pets") {
                    call("findPetsByStatus") {
                        queryParam("status", "available")
                    }
                }

                then("I receive a list") {
                    statusCode(200)
                    bodyArrayNotEmpty("$")
                }
            }

            // CRUD flow with variable extraction
            scenario("Create a new pet") {
                `when`("I create a pet") {
                    call("addPet") {
                        body(mapOf(
                            "name" to "TestPet",
                            "photoUrls" to listOf("https://example.com/photo.jpg"),
                            "status" to "available"
                        ))
                    }
                    extractTo("createdPetId", "$.id")
                }

                then("the pet is created") {
                    statusCode(200)
                    bodyEquals("$.name", "TestPet")
                }
            }

            scenario("Retrieve the created pet") {
                `when`("I get the pet by ID") {
                    call("getPetById") {
                        pathParam("petId", $$"${createdPetId}")
                    }
                }

                then("I see the pet details") {
                    statusCode(200)
                    matchesSchema()
                }
            }

            // Parameterized scenario
            scenarioOutline("Filter pets by <status>") {
                `when`("I filter") {
                    call("findPetsByStatus") {
                        queryParam("status", "<status>")
                    }
                }

                then("I get results") {
                    statusCode(200)
                }

                examples(
                    row("status" to "available"),
                    row("status" to "pending"),
                    row("status" to "sold")
                )
            }
        }
    }

API Reference
-------------

LemonCheckSuite
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

+----------------------+------------------------------------------------+
| Method               | Description                                    |
+======================+================================================+
| ``given(desc, block)`` | Define a precondition step                   |
+----------------------+------------------------------------------------+
| ``when(desc, block)``  | Define an action step                        |
+----------------------+------------------------------------------------+
| ``then(desc, block)``  | Define an assertion step                     |
+----------------------+------------------------------------------------+
| ``and(desc, block)``   | Continue previous step type                  |
+----------------------+------------------------------------------------+
| ``but(desc, block)``   | Define an exception/negative case            |
+----------------------+------------------------------------------------+
| ``include(fragment)``  | Include a fragment's steps                   |
+----------------------+------------------------------------------------+

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

See Also
--------

- :doc:`/quickstart` - Getting started with LemonCheck
- :doc:`standalone-runner` - Running scenarios without JUnit
- :doc:`fragments` - Creating reusable step sequences
- :doc:`custom-steps` - Defining custom step implementations
