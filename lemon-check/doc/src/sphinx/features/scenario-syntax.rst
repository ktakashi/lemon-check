Scenario File Syntax
====================

LemonCheck uses a custom scenario DSL (Domain-Specific Language) for defining API tests.
This page covers the complete syntax for ``.scenario`` and ``.fragment`` files.

File Types
----------

* ``.scenario`` - Test scenario files containing test definitions
* ``.fragment`` - Reusable step fragments that can be included in scenarios

All files must be UTF-8 encoded.

Basic Scenario
--------------

A minimal scenario consists of a name and steps:

.. code-block:: text

    scenario: List all pets
      when: I request all pets
        call ^listPets
      then: I receive a 200 response
        assert status 200

Step Keywords
-------------

Steps begin with keywords that describe their purpose:

* ``given`` - Precondition setup
* ``when`` - Action to perform  
* ``then`` - Expected outcome verification
* ``and`` - Continuation of previous step type
* ``but`` - Exception or negative case

Example:

.. code-block:: text

    scenario: Create and verify pet
      given: API is ready
        call ^health
        assert status 200
      when: I create a pet
        call ^createPet
          body: {"name": "Max", "status": "available"}
      then: pet is created
        assert status 201
        extract $.id => petId
      and: I can retrieve it
        call ^getPetById
          petId: {{petId}}
        assert status 200

API Calls
---------

Use ``call`` to invoke an OpenAPI operation:

.. code-block:: text

    call ^operationId

For multi-spec projects, specify the spec name:

.. code-block:: text

    call using auth ^login

Call Parameters
^^^^^^^^^^^^^^^

============== ================================ ==================================
Parameter Type Description                      Example
============== ================================ ==================================
Path           Replace path variable            ``petId: 123``
Query          Add query string parameter       ``status: "available"``
Header         Add HTTP header                  ``header_Authorization: "Bearer {{token}}"``
Body           Set request body (inline)        ``body: {"name": "Max"}``
BodyFile       Set request body (external file) ``bodyFile: "classpath:templates/pet.json"``
============== ================================ ==================================

Example with all parameter types:

.. code-block:: text

    when: I create a pet
      call ^createPet
        petId: 123
        status: "available"
        header_Authorization: "Bearer {{token}}"
        body: {"name": "Max", "category": "dog"}

External Body Files
^^^^^^^^^^^^^^^^^^^

For large or reusable request bodies, use ``bodyFile`` to load content from external files:

.. code-block:: text

    when: I create a pet with template
      call ^createPet
        bodyFile: "classpath:templates/create-pet.json"

**Supported path formats:**

* ``classpath:path/to/file.json`` - Load from classpath (recommended)
* ``file:./relative/path.json`` - Load from file system (relative to working directory)
* ``/absolute/path.json`` - Load from absolute file path

**Variable interpolation** is supported in external body files. Use ``{{variableName}}`` syntax:

**templates/create-pet.json:**

.. code-block:: json

    {
      "name": "{{petName}}",
      "category": "{{category}}",
      "status": "available"
    }

Variables from previous extractions are automatically substituted when the file is loaded.

.. note::

    If both ``body`` and ``bodyFile`` are specified, the inline ``body`` takes precedence.

Assertions
----------

Status Code Assertions
^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: text

    assert status 200
    assert statusCode 201

JSONPath Assertions
^^^^^^^^^^^^^^^^^^^

**Equality:**

.. code-block:: text

    assert $.name equals "Max"
    assert $.id = 123

**Pattern Matching (regex):**

.. code-block:: text

    assert $.email matches ".*@.*\\.com"
    assert $.total matches "\\d+"

**Array Assertions:**

.. code-block:: text

    assert $.pets notEmpty
    assert $.items size 5

**Header Assertions:**

.. code-block:: text

    # Check header exists
    assert header Content-Type

    # Check header value
    assert header Content-Type = "application/json"
    assert header Content-Type: "application/json"

**Response Time:**

.. code-block:: text

    # Response must complete within 5000ms
    assert responseTime 5000

**Schema Validation:**

.. code-block:: text

    # Validate response against OpenAPI schema
    assert schema

Variable Extraction
-------------------

Extract values from responses for use in subsequent steps:

.. code-block:: text

    extract $.id => petId
    extract $.token => authToken

Use extracted variables with double curly braces:

.. code-block:: text

    call ^getPetById
      petId: {{petId}}

Tags
----

Tags categorize and filter scenarios. They begin with ``@`` and appear before the element they annotate:

.. code-block:: text

    @smoke @critical
    scenario: Critical path test
      when: I test
        call ^test

Built-in Tags
^^^^^^^^^^^^^

* ``@ignore`` - Skip this scenario during execution
* ``@wip`` - Work in progress
* ``@slow`` - Marks slow-running tests

Tag Filtering
^^^^^^^^^^^^^

Use ``@LemonCheckTags`` annotation in Java/Kotlin:

.. code-block:: java

    // Exclude @ignore tagged scenarios
    @LemonCheckTags(exclude = {"ignore"})
    
    // Only run @smoke tagged scenarios
    @LemonCheckTags(include = {"smoke"})
    
    // Combine include and exclude (exclude takes precedence)
    @LemonCheckTags(include = {"api"}, exclude = {"slow", "wip"})

Features and Background
------------------------

Features group related scenarios with optional shared setup:

Basic Feature
^^^^^^^^^^^^^

.. code-block:: text

    feature: Pet Store API
      scenario: list all pets
        when: I list pets
          call ^listPets
        then: I get results
          assert status 200
      
      scenario: create a pet
        when: I create a pet
          call ^createPet
            body: {"name": "Max"}
        then: the pet is created
          assert status 201

Feature with Background
^^^^^^^^^^^^^^^^^^^^^^^

Background steps run before **each** scenario in the feature:

.. code-block:: text

    feature: Pet Operations
      background:
        given: setup test data
          call ^createPet
            body: {"name": "TestPet"}
          extract $.id => petId

      scenario: get pet by id
        when: retrieve the pet
          call ^getPetById
            petId: {{petId}}
        then: pet is returned
          assert status 200

      scenario: delete pet
        when: delete the pet
          call ^deletePet
            petId: {{petId}}
        then: pet is deleted
          assert status 204

Tagged Features
^^^^^^^^^^^^^^^

Tags on a feature are inherited by all scenarios within:

.. code-block:: text

    @api @regression
    feature: Authentication Tests
      
      @smoke
      scenario: valid login
        when: login with credentials
          call ^login
        then: success
          assert status 200

      @ignore
      scenario: incomplete test
        when: TODO
          call ^incomplete

In this example:

* "valid login" has tags: ``api``, ``regression``, ``smoke``
* "incomplete test" has tags: ``api``, ``regression``, ``ignore``

Parameterized Scenarios (Outline)
---------------------------------

Create data-driven tests with scenario outlines:

.. code-block:: text

    outline: Create different pets
      when: I create a pet
        call ^createPet
          body: {"name": "{{name}}", "category": "{{category}}"}
      then: pet is created
        assert status 201
      examples:
        | name   | category |
        | Fluffy | cat      |
        | Buddy  | dog      |
        | Tweety | bird     |

This generates 3 scenario runs with different parameter combinations.

Fragment Files
--------------

Fragment files (``.fragment``) define reusable step sequences:

.. code-block:: text

    # auth.fragment
    
    fragment: authenticate
      given: login with credentials
        call ^login
          body: {"username": "test", "password": "test"}
        assert status 200
        extract $.token => authToken

Use fragments in scenarios with ``include``:

.. code-block:: text

    scenario: Access protected API
      given: I am authenticated
        include authenticate
      when: I access protected endpoint
        call ^getProfile
          header_Authorization: "Bearer {{authToken}}"
      then: I see my profile
        assert status 200

Parameters Block
----------------

Override default configuration at the file level:

.. code-block:: text

    parameters:
      baseUrl: "http://localhost:8080"
      timeout: 60
      shareVariablesAcrossScenarios: true

    scenario: Test with custom configuration
      when: I call the API
        call ^listPets
      then: I get results
        assert status 200

Supported Parameters
^^^^^^^^^^^^^^^^^^^^

========================== ======= ========================================
Parameter                  Type    Description
========================== ======= ========================================
``baseUrl``                String  Override API base URL
``timeout``                Number  Request timeout in seconds
``shareVariablesAcrossScenarios`` Boolean Share extracted variables
``logRequests``            Boolean Enable HTTP request logging
``logResponses``           Boolean Enable HTTP response logging
``strictSchemaValidation`` Boolean Fail on schema validation warnings
``header.<name>``          String  Add/override default header
========================== ======= ========================================

Comments
--------

Lines starting with ``#`` are comments:

.. code-block:: text

    # This is a comment describing the test
    scenario: My test
      # Explain what this step does
      when: I do something
        call ^operation

Best Practices
--------------

1. **Use descriptive names** - ``Create pet with valid data returns 201`` is better than ``Test1``

2. **Group related assertions** - Keep assertions in a single ``then`` step when verifying one outcome

3. **Use tags for organization** - Apply ``@smoke``, ``@regression``, ``@slow`` tags consistently

4. **Extract reusable steps to fragments** - Authentication flows, common setup/teardown

5. **Leverage background for shared setup** - When multiple scenarios need the same preconditions

See Also
--------

* :doc:`parameters` - Detailed parameter configuration
* :doc:`fragments` - Reusable step fragments
* :doc:`kotlin-dsl` - Alternative Kotlin DSL approach
* :doc:`multi-spec` - Working with multiple OpenAPI specs
