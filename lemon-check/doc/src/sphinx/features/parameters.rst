File-Level Parameters
=====================

The parameters section provides a way to override configuration settings at the
scenario file level. This allows you to customize behavior for specific scenario
files without changing the global configuration.

Syntax
------

Place the ``parameters:`` block at the top of your scenario file, before any
scenarios or fragments:

.. code-block:: text

   parameters:
     baseUrl: "http://localhost:8080"
     timeout: 60
     shareVariablesAcrossScenarios: true

   scenario: My test scenario
     when I call the API
       call ^listPets

Supported Parameters
--------------------

Configuration Overrides
^^^^^^^^^^^^^^^^^^^^^^^

.. list-table::
   :header-rows: 1
   :widths: 25 50 25

   * - Parameter
     - Description
     - Example Value
   * - ``baseUrl``
     - Override the base URL for API requests
     - ``"http://localhost:8080"``
   * - ``timeout``
     - Request timeout in seconds
     - ``60``
   * - ``environment``
     - Environment name for reporting
     - ``"staging"``
   * - ``strictSchemaValidation``
     - Fail on schema validation warnings
     - ``true`` / ``false``
   * - ``followRedirects``
     - Follow HTTP redirects
     - ``true`` / ``false``
   * - ``logRequests``
     - Log HTTP requests
     - ``true`` / ``false``
   * - ``logResponses``
     - Log HTTP responses
     - ``true`` / ``false``
   * - ``shareVariablesAcrossScenarios``
     - Share extracted variables across scenarios
     - ``true`` / ``false``

Header Overrides
^^^^^^^^^^^^^^^^

Use the ``header.`` prefix to add or override default headers:

.. code-block:: text

   parameters:
     header.Authorization: "Bearer test-token"
     header.X-Api-Key: "my-api-key"
     header.Accept: "application/json"

Auto-Assertion Overrides
^^^^^^^^^^^^^^^^^^^^^^^^

Control automatic assertion generation:

.. list-table::
   :header-rows: 1
   :widths: 35 65

   * - Parameter
     - Description
   * - ``autoAssertions.enabled``
     - Enable/disable all auto-assertions
   * - ``autoAssertions.statusCode``
     - Auto-assert correct status code
   * - ``autoAssertions.contentType``
     - Auto-assert Content-Type header
   * - ``autoAssertions.schema``
     - Auto-assert response matches schema

Example:

.. code-block:: text

   parameters:
     autoAssertions.enabled: false

Use Cases
---------

Environment-Specific Settings
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Configure different base URLs or authentication for different environments:

.. code-block:: text

   # production-tests.scenario
   parameters:
     baseUrl: "https://api.production.example.com"
     header.Authorization: "Bearer prod-readonly-token"
     logRequests: false

   scenario: Production health check
     when I check the API health
       call ^healthCheck
     then the API is healthy
       assert status 200

Cross-Scenario Variable Sharing
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Enable variable sharing for integration test flows:

.. code-block:: text

   # crud-workflow.scenario
   parameters:
     shareVariablesAcrossScenarios: true
     logRequests: true

   scenario: Create a resource
     when I create a pet
       call ^createPet
         body: {"name": "Fluffy", "tag": "dog"}
       extract $.id => petId

   scenario: Verify the resource
     when I retrieve the pet
       call ^getPetById
         petId: {{petId}}
     then the pet exists
       assert $.name equals "Fluffy"

   scenario: Clean up
     when I delete the pet
       call ^deletePet
         petId: {{petId}}
     then deletion succeeded
       assert status 200

Testing with Debug Logging
^^^^^^^^^^^^^^^^^^^^^^^^^^

Enable verbose logging for troubleshooting:

.. code-block:: text

   parameters:
     logRequests: true
     logResponses: true
     timeout: 120

   scenario: Debug complex flow
     when I perform complex operation
       call ^complexOperation
         body: {"debug": true}

Programmatic Usage
------------------

When using the ``ScenarioLoader`` programmatically, you can access parameters
through the ``ScenarioFileContent`` class:

.. code-block:: kotlin

   val loader = ScenarioLoader()
   val content = loader.loadFileContent(path)

   // Access scenarios
   val scenarios = content.scenarios

   // Access parameters
   val parameters = content.parameters
   val baseUrl = parameters["baseUrl"] as? String
   val timeout = parameters["timeout"] as? Long

To run scenarios with file-level parameters, use the ``runWithParameters`` method:

.. code-block:: kotlin

   val runner = ScenarioRunner(specRegistry, configuration, pluginRegistry)

   // Run with file-level parameter overrides
   val result = runner.runWithParameters(content.scenarios, content.parameters)

   // Or apply parameters to configuration manually
   val modifiedConfig = configuration.withParameters(content.parameters)
   // Execute with modifiedConfig...

JUnit Engine Integration
------------------------

File-level parameters work with both JUnit tests and standalone ``ScenarioRunner``.
When using JUnit tests, parameters in the ``.scenario`` file are applied and take
precedence over the bindings configuration for that specific file.

For JUnit tests, you can also configure settings in your bindings class:

.. code-block:: kotlin

   class MyBindings : LemonCheckBindings() {
       override fun configure(configuration: Configuration) {
           // These can be overridden by file-level parameters
           configuration.logRequests = true
       }
   }

Parameters defined in the scenario file will override the bindings configuration
for the scenarios in that specific file. This allows you to have default settings
in your bindings while customizing behavior for specific test files.

Configuration Priority
----------------------

Parameters are applied in the following order (later values override earlier):

1. Default ``Configuration`` values
2. Programmatic configuration changes
3. File-level ``parameters:`` section (highest priority for that file)

Notes
-----

- Parameters only affect scenarios in the same file
- Empty parameter values are ignored
- Unknown parameter names are silently ignored
- Boolean values can be ``true``/``false`` or ``"true"``/``"false"`` strings
- Timeout values must be integers (seconds)

See Also
--------

- :doc:`standalone-runner` - Running scenarios without JUnit
- :doc:`kotlin-dsl` - Programmatic scenario configuration
