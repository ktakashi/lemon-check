Custom Steps
============

BerryCrush provides multiple ways to define custom step definitions, allowing you to
create reusable, domain-specific step implementations.

Step Binding Mechanisms
-----------------------

There are four ways to define custom steps:

1. **Annotation-based** - Using ``@Step`` annotation (recommended for Java users)
2. **Registration API** - Programmatic step registration
3. **Kotlin DSL** - Type-safe DSL builder (recommended for Kotlin)
4. **Package scanning** - Auto-discovery of step classes

Annotation-Based Steps
----------------------

The simplest way to define steps is using the ``@Step`` annotation:

.. code-block:: kotlin

    class PetSteps {
        @Step("a pet exists with name {string}")
        fun createPet(name: String) {
            // Create a pet with the given name
            PetService.create(name)
        }

        @Step("I have {int} pets")
        fun setPetCount(count: Int) {
            // Set up the specified number of pets
            repeat(count) { PetService.createRandom() }
        }

        @Step("the pet should have {int} legs")
        fun verifyLegCount(expected: Int) {
            val actual = PetService.getCurrentPet().legs
            assert(actual == expected) { "Expected $expected legs but found $actual" }
        }
    }

Pattern Placeholders
^^^^^^^^^^^^^^^^^^^^

The ``@Step`` pattern supports these placeholders:

* ``{int}`` - Matches an integer (e.g., ``42``, ``-10``)
* ``{string}`` - Matches a quoted string (e.g., ``"hello"``, ``'world'``)
* ``{word}`` - Matches a single word (e.g., ``active``, ``pending``)
* ``{float}`` - Matches a floating-point number (e.g., ``3.14``, ``-2.5``)
* ``{any}`` - Matches any text (greedy)

Registering Annotation-Based Steps
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Register step classes via the configuration annotation:

.. code-block:: kotlin

    @BerryCrushConfiguration(
        stepClasses = [PetSteps::class, UserSteps::class]
    )
    class MyApiTest

Or via package scanning:

.. code-block:: kotlin

    @BerryCrushConfiguration(
        stepPackages = ["com.example.steps"]
    )
    class MyApiTest

Registration API
----------------

For dynamic step registration, use the ``StepRegistry`` API:

.. code-block:: kotlin

    val registry = DefaultStepRegistry()
    val scanner = AnnotationStepScanner()

    // Register from a class
    val definitions = scanner.scan(PetSteps::class.java)
    registry.registerAll(definitions)

    // Or register manually
    registry.register(StepDefinition(
        pattern = "the status is {word}",
        method = MySteps::class.java.getMethod("setStatus", String::class.java),
        instance = MySteps(),
        description = "Sets the current status"
    ))

Kotlin DSL
----------

The Kotlin DSL provides a type-safe, concise way to define steps:

.. code-block:: kotlin

    val registry = DefaultStepRegistry()

    steps {
        step("I have {int} pets") { count: Int ->
            PetService.setCount(count)
        }

        step("the pet name is {string}") { name: String ->
            PetService.setName(name)
        }

        step("I add {int} and {int}") { a: Int, b: Int ->
            a + b  // Return value is stored
        }

        step("the setup is complete", description = "Verifies setup") {
            // No parameters
            verifySetup()
        }
    }.registerTo(registry)

Type-Safe Parameters
^^^^^^^^^^^^^^^^^^^^

The DSL supports up to 5 typed parameters:

.. code-block:: kotlin

    step<Int>("single param {int}") { value -> ... }
    step<Int, String>("two params {int} {string}") { a, b -> ... }
    step<Int, Int, Int>("{int} + {int} + {int}") { a, b, c -> ... }

Package Scanning
----------------

BerryCrush can automatically discover step classes in specified packages:

.. code-block:: kotlin

    @BerryCrushConfiguration(
        stepPackages = [
            "com.example.steps.pets",
            "com.example.steps.users"
        ]
    )
    class MyApiTest

All classes in these packages with ``@Step`` annotated methods will be discovered
and registered.

Spring Integration
------------------

With the Spring module, steps can be Spring-managed beans:

.. code-block:: kotlin

    @Component
    class PetSteps(
        private val petRepository: PetRepository
    ) {
        @Step("a pet exists with name {string}")
        fun createPet(name: String, context: StepContext) {
            val pet = petRepository.save(Pet(name = name))
            context.variables["petId"] = pet.id
        }
    }

Enable auto-discovery with the Spring configuration:

.. code-block:: kotlin

    @SpringBootTest
    @Import(SpringStepDiscovery::class)
    @IncludeEngines("berrycrush")
    @BerryCrushScenarios(locations = "scenarios/pets.scenario")
    class PetApiTest {
        @Autowired
        lateinit var stepRegistry: StepRegistry
    }

Step Context
------------

Steps can receive a ``StepContext`` parameter to access the execution context for
variables, HTTP responses, and configuration. The ``StepContext`` must be the
**last parameter** of the step method.

.. code-block:: kotlin

    @Step("I save the pet ID as {word}")
    fun savePetId(variableName: String, context: StepContext) {
        val response = context.lastResponse
        val petId = JsonPath.read<Int>(response?.body(), "$.id")
        context.setVariable(variableName, petId)
    }

    @Step("I request the saved pet")
    fun getSavedPet(context: StepContext) {
        val petId = context.variable("petId")
        // Make HTTP request using petId
    }

StepContext API
^^^^^^^^^^^^^^^

The ``StepContext`` interface provides the following methods and properties:

**Variables**

* ``variable(name: String): Any?`` - Get a variable by name
* ``variable(name: String, type: Class<T>): T?`` - Get a typed variable by name
* ``setVariable(name: String, value: Any?)`` - Set a scenario-scoped variable
* ``setSharedVariable(name: String, value: Any?)`` - Set a suite-scoped (shared) variable
* ``allVariables(): Map<String, Any?>`` - Get all current variables

**HTTP Response**

* ``lastResponse: HttpResponse<String>?`` - The last HTTP response received (null if no request made)

**Configuration**

* ``configuration: BerryCrushConfiguration`` - The current execution configuration

Variable Scopes
^^^^^^^^^^^^^^^

Variables can be set with two different scopes:

1. **Scenario-scoped** (default): Variables set with ``setVariable()`` are isolated to the
   current scenario and will NOT be shared with other scenarios.

2. **Suite-scoped**: Variables set with ``setSharedVariable()`` will be shared across
   scenarios when variable sharing is enabled in the suite configuration. If sharing is
   disabled, ``setSharedVariable()`` behaves like ``setVariable()``.

.. code-block:: kotlin

    @Step("I set up test data")
    fun setupData(context: StepContext) {
        // Scenario-scoped: only visible in current scenario
        context.setVariable("tempId", generateId())

        // Suite-scoped: shared across scenarios when sharing enabled
        context.setSharedVariable("authToken", getAuthToken())
    }

Variable lookup prioritizes scenario-scoped variables over shared variables when both exist.

Best Practices
--------------

1. **Keep steps reusable**: Steps should be generic enough to use across scenarios
2. **Use descriptive patterns**: Make step text readable and self-documenting
3. **Handle return values**: Steps can return values for chaining or verification
4. **Use context variables**: Share data between steps via ``setVariable()``
5. **Document complex steps**: Use the ``description`` parameter for documentation
6. **Group related steps**: Organize step classes by domain (e.g., ``PetSteps``, ``UserSteps``)
7. **StepContext as last parameter**: Always place ``StepContext`` as the last method parameter

Example: Complete Step Library
------------------------------

Here's a complete example of a step library for API testing:

.. code-block:: kotlin

    class ApiSteps {
        @Step("the API is available at {string}")
        fun setBaseUrl(url: String, context: StepContext) {
            // Store base URL for later use
            context.setVariable("baseUrl", url)
        }

        @Step("I have an auth token {string}")
        fun setAuthToken(token: String, context: StepContext) {
            // Share token across scenarios
            context.setSharedVariable("authToken", token)
        }

        @Step("the response status should be {int}")
        fun verifyStatus(expected: Int, context: StepContext) {
            val response = context.lastResponse
                ?: throw AssertionError("No HTTP response available")
            val actual = response.statusCode()
            assert(actual == expected) { 
                "Expected status $expected but got $actual" 
            }
        }

        @Step("the response body at {string} should be {string}")
        fun verifyJsonPath(path: String, expected: String, context: StepContext) {
            val response = context.lastResponse
                ?: throw AssertionError("No HTTP response available")
            val actual = JsonPath.read<Any>(response.body(), path)
            assert(actual.toString() == expected) {
                "At $path: expected $expected but got $actual"
            }
        }
    }

Custom Assertions
-----------------

In addition to custom steps, BerryCrush supports custom assertions via the ``@Assertion``
annotation. Custom assertions are similar to custom steps but are specifically designed
for validation logic and return an ``AssertionResult``.

Defining Custom Assertions
^^^^^^^^^^^^^^^^^^^^^^^^^^

Define custom assertions using the ``@Assertion`` annotation:

.. code-block:: kotlin

    class MyAssertions {
        @Assertion("the {word} should have status {string}")
        fun assertStatus(entityType: String, expectedStatus: String, context: AssertionContext): AssertionResult {
            val response = context.lastResponse
                ?: return AssertionResult.failed("No HTTP response available")

            val actualStatus = parseStatus(response, entityType)
            return if (actualStatus == expectedStatus) {
                AssertionResult.passed()
            } else {
                AssertionResult.failed(
                    message = "Expected $entityType status '$expectedStatus' but got '$actualStatus'",
                    expectedValue = expectedStatus,
                    actualValue = actualStatus
                )
            }
        }

        @Assertion("the response should contain {int} items")
        fun assertItemCount(expected: Int, context: AssertionContext): AssertionResult {
            val response = context.lastResponse
                ?: return AssertionResult.failed("No HTTP response available")

            val items = JsonPath.read<List<*>>(response.body(), "$")
            val actual = items.size

            return if (actual == expected) {
                AssertionResult.passed()
            } else {
                AssertionResult.failed(
                    message = "Expected $expected items but got $actual",
                    expectedValue = expected,
                    actualValue = actual
                )
            }
        }
    }

AssertionResult
^^^^^^^^^^^^^^^

Custom assertions should return an ``AssertionResult`` to indicate success or failure:

.. code-block:: kotlin

    // Passed assertion
    AssertionResult.passed()
    AssertionResult.passed("Optional success message")

    // Failed assertion
    AssertionResult.failed("Failure reason")
    AssertionResult.failed(
        message = "Values don't match",
        expectedValue = expected,
        actualValue = actual
    )

If a custom assertion method:

* Returns ``AssertionResult.passed()`` → Assertion passes
* Returns ``AssertionResult.failed(...)`` → Assertion fails with message
* Returns ``Unit``/void → Assumed to pass (unless exception thrown)
* Throws ``AssertionError`` → Treated as failed assertion
* Throws other exception → Treated as error (not assertion failure)

AssertionContext
^^^^^^^^^^^^^^^^

The ``AssertionContext`` provides read-only access to the execution context:

**Variables (read-only)**

* ``variable(name: String): Any?`` - Get a variable by name
* ``variable(name: String, type: Class<T>): T?`` - Get a typed variable
* ``allVariables(): Map<String, Any?>`` - Get all variables

**HTTP Response**

* ``lastResponse: HttpResponse<String>?`` - The last HTTP response (may be null)

**Configuration**

* ``configuration: BerryCrushConfiguration`` - The current configuration

Registering Custom Assertions
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Register assertion classes via the configuration annotation:

.. code-block:: kotlin

    @BerryCrushConfiguration(
        assertionClasses = [MyAssertions::class, ValidationAssertions::class]
    )
    class MyApiTest

Or via package scanning:

.. code-block:: kotlin

    @BerryCrushConfiguration(
        assertionPackages = ["com.example.assertions"]
    )
    class MyApiTest

Assertion vs Step
^^^^^^^^^^^^^^^^^

Choose between ``@Step`` and ``@Assertion`` based on purpose:

* **Use @Step** for actions, setup, and general-purpose logic
* **Use @Assertion** for validation and verification that returns pass/fail results

+------------------+--------------------------------+--------------------------------+
| Feature          | @Step                          | @Assertion                     |
+==================+================================+================================+
| Purpose          | Actions & setup                | Validation & verification      |
+------------------+--------------------------------+--------------------------------+
| Context          | StepContext (read/write)       | AssertionContext (read-only)   |
+------------------+--------------------------------+--------------------------------+
| Return type      | Any (including StepResult)     | AssertionResult (pass/fail)    |
+------------------+--------------------------------+--------------------------------+
| Variable access  | Read and write                 | Read-only                      |
+------------------+--------------------------------+--------------------------------+

Using Custom Assertions in Scenario Files
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Custom assertions can be used in scenario files using the ``assert`` keyword:

**Basic Usage**

.. code-block:: yaml

    scenario: Verify item availability
      when I get the product details
        call ^getProduct
          productId: 123
      then the product should be available
        assert the product should have status "available"
        assert the response should contain 5 items

**Pattern Matching**

Custom assertion patterns are matched against registered ``@Assertion`` methods.
The parser captures the assertion text after ``assert`` and looks for a matching
pattern in the ``AssertionRegistry``:

.. code-block:: kotlin

    // Definition
    @Assertion("the {word} should have status {string}")
    fun assertStatus(entityType: String, expectedStatus: String, context: AssertionContext): AssertionResult

.. code-block:: yaml

    # Usage in scenario file
    assert the product should have status "available"
    # Matches pattern: the {word} should have status {string}
    # Parameters: entityType="product", expectedStatus="available"

**Combining with Built-in Assertions**

Custom assertions can be used alongside built-in assertions in the same step:

.. code-block:: yaml

    scenario: Complete verification
      when I get the product
        call ^getProduct
          productId: 123
      then everything should be correct
        assert status 200
        assert $.id equals 123
        assert the product should have status "available"
        assert header Content-Type equals "application/json"

**Execution Context**

Custom assertions execute after an HTTP request has been made. They have read-only
access to:

* The last HTTP response (body, status code, headers)
* All extracted variables from the scenario
* Configuration settings

If no HTTP response is available when a custom assertion runs, it should handle
the null case gracefully:

.. code-block:: kotlin

    @Assertion("data should be valid")
    fun assertDataValid(context: AssertionContext): AssertionResult {
        val response = context.lastResponse
            ?: return AssertionResult.failed("No HTTP response available")
        
        // Validate response...
        return AssertionResult.passed()
    }

**Pattern Guidelines**

For effective custom assertions:

1. **Use clear, descriptive patterns** that read naturally in scenario files
2. **Prefer {string} for exact matches** and {word} for identifiers
3. **Include context in the pattern** (e.g., "the user" vs just "user")
4. **Return descriptive failure messages** that help diagnose issues

Sample Code Reference
---------------------

For complete working examples of custom steps and assertions, see the Petstore sample:

* **Custom Steps:** ``samples/petstore/scenario/src/test/kotlin/.../steps/PetstoreSteps.kt``
* **Custom Assertions:** ``samples/petstore/scenario/src/test/kotlin/.../assertions/PetstoreAssertions.kt``
* **Step Scenarios:** ``samples/petstore/scenario/src/test/resources/scenarios/80-custom-steps.scenario``
* **Assertion Scenarios:** ``samples/petstore/scenario/src/test/resources/scenarios/90-custom-assertions.scenario``
* **Configuration:** ``samples/petstore/scenario/src/test/kotlin/.../CustomStepsTest.kt``

These samples demonstrate:

* Defining step methods with ``@Step`` annotation
* Defining assertion methods with ``@Assertion`` annotation
* Using ``StepContext`` for variable management
* Using ``AssertionContext`` for validation
* Registering custom classes via ``@BerryCrushConfiguration``
* Writing scenario files that use custom steps and assertions
