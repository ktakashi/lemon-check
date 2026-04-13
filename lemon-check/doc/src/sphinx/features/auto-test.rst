Auto-Generated Tests
====================

LemonCheck can automatically generate invalid request and security tests based on your OpenAPI schema constraints.
This feature helps ensure your API properly validates input and rejects common attack patterns.

Overview
--------

The ``auto:`` directive in API calls generates multiple test variations that:

* Violate OpenAPI schema constraints (invalid tests)
* Include common attack payloads (security tests)

Each generated test appears as a separate test in your test reports, making it easy to identify which validations
your API handles correctly.

Basic Syntax
------------

Add the ``auto:`` directive to any API call:

.. code-block:: text

    call ^operationId
      auto: [<test-types>]
      <base-parameters>

Where ``<test-types>`` is a space-separated list of:

* ``invalid`` - Generate tests that violate OpenAPI schema constraints
* ``security`` - Generate tests with common attack payloads

You can use one or both types:

.. code-block:: text

    auto: [invalid]          # Only invalid tests
    auto: [security]         # Only security tests
    auto: [invalid security] # Both types

Example
-------

.. code-block:: text

    scenario: Auto-generated tests for createPet
      when: I create a pet with invalid input
        call ^createPet
          auto: [invalid security]
          body:
            name: "TestPet"
            status: "available"
      
      if status 4xx
        # Test passed - invalid request rejected
      else
        fail "Expected 4xx for {{test.type}}: {{test.description}}"

This generates tests for each field in the request body based on the OpenAPI schema constraints.

Invalid Tests
-------------

Invalid tests are generated based on OpenAPI schema properties:

======================== =================================================
Schema Property          Generated Test
======================== =================================================
``minLength``            String shorter than minimum
``maxLength``            String longer than maximum
``minimum``              Number below minimum value
``maximum``              Number above maximum value
``pattern``              String that violates the regex pattern
``format: email``        Invalid email (e.g., "not-an-email")
``format: uuid``         Invalid UUID (e.g., "not-a-uuid")
``format: date``         Invalid date format
``format: date-time``    Invalid date-time format
``required``             Missing required fields
``enum``                 Value not in allowed list
``type``                 Wrong type (e.g., string instead of number)
======================== =================================================

Security Tests
--------------

Security tests inject common attack payloads to verify your API properly sanitizes input:

SQL Injection
^^^^^^^^^^^^^

.. code-block:: text

    ' OR '1'='1
    "; DROP TABLE users; --
    ' UNION SELECT * FROM users --

Cross-Site Scripting (XSS)
^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: text

    <script>alert('XSS')</script>
    javascript:alert(1)
    <img src=x onerror=alert(1)>

Path Traversal
^^^^^^^^^^^^^^

.. code-block:: text

    ../../etc/passwd
    ....//....//etc/passwd
    ..%2F..%2Fetc%2Fpasswd

Command Injection
^^^^^^^^^^^^^^^^^

.. code-block:: text

    ; ls -la
    $(whoami)
    `id`
    | cat /etc/passwd

LDAP Injection
^^^^^^^^^^^^^^

.. code-block:: text

    *)(uid=*))(|(uid=*
    admin)(&)

Parameter Locations
-------------------

Auto-tests are generated for parameters in different locations:

================== ====================================== ========================
Location           Description                            Display Name
================== ====================================== ========================
Request body       JSON body fields                       ``request body``
Path parameter     URL path variables                     ``path variable``
Query parameter    Query string parameters                ``query parameter``
Header             HTTP headers                           ``header``
================== ====================================== ========================

Path Parameter Example
^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: text

    scenario: Auto-generated tests for getPetById
      when: I get a pet with invalid ID
        call ^getPetById
          auto: [invalid security]
          petId: 1
      
      if status 4xx
        # Invalid ID rejected - test passed

Context Variables
-----------------

During auto-test execution, these variables are available for use in assertions:

================== ================================================ ========================
Variable           Description                                      Example Values
================== ================================================ ========================
``test.type``      Test category                                    ``"invalid"``, ``"security"``
``test.field``     Field being tested                               ``"name"``, ``"petId"``
``test.description`` Human-readable test description                ``"SQL Injection"``, ``"minLength violation"``
``test.value``     The invalid/attack value used                    ``"' OR '1'='1"``
``test.location``  Parameter location                               ``"request body"``, ``"path variable"``
================== ================================================ ========================

Using Context Variables in Assertions
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: text

    scenario: Auto-generated security tests
      when: I create a pet with attack payloads
        call ^createPet
          auto: [security]
          body:
            name: "TestPet"
      
      if status 4xx and test.type equals security
        # Security attack blocked - expected
      else if status 2xx
        fail "Security vulnerability: {{test.description}} not blocked for field {{test.field}}"

Test Display Names
------------------

Auto-tests appear in test reports with descriptive names:

.. code-block:: text

    [Invalid request] request body name with value <empty string>
    [Invalid request] request body status with value INVALID_ENUM_VALUE
    [Invalid request] path variable petId with value not-a-number
    [Security SQL Injection] request body name with value ' OR '1'='1
    [Security XSS] request body name with value <script>alert('XSS')</script>
    [Security Path Traversal] path variable petId with value ../../etc/passwd

This format allows you to:

* Quickly identify which tests failed
* Understand what type of validation is missing
* Locate the affected field and value

Best Practices
--------------

1. **Provide valid base parameters**
   
   Auto-tests modify one parameter at a time while keeping others valid:

   .. code-block:: text

       call ^createPet
         auto: [invalid security]
         body:
           name: "ValidName"      # This is the base value
           status: "available"    # This is also a base value

2. **Use conditional assertions**
   
   Handle different test types appropriately:

   .. code-block:: text

       if status 4xx and test.type equals invalid
         # Invalid input correctly rejected
       else if status 4xx and test.type equals security
         # Security attack blocked
       else
         fail "{{test.type}} test should return 4xx: {{test.description}}"

3. **Expect 4xx responses**
   
   Both invalid and security tests should be rejected by a secure, well-validated API.

4. **Review generated tests**
   
   The number of tests depends on schema constraints. Complex schemas with many constraints
   generate more tests. Run tests with logging enabled to see what's being generated.

5. **Combine with regular tests**
   
   Auto-tests supplement but don't replace targeted functional tests:

   .. code-block:: text

       # Regular functional test
       scenario: Create pet successfully
         when: I create a pet
           call ^createPet
             body:
               name: "Fluffy"
               status: "available"
         then: pet is created
           assert status 201
       
       # Auto-generated validation tests
       scenario: Auto-tests for createPet validation
         when: I create a pet with invalid data
           call ^createPet
             auto: [invalid security]
             body:
               name: "Fluffy"
               status: "available"
         if status 4xx
           # Test passed

Integration with JUnit
----------------------

Auto-tests integrate seamlessly with JUnit. Each generated test case appears as a separate
test in the JUnit report:

.. code-block:: text

    PetStoreTest
    ├── 01-create-pet.scenario
    │   └── Create pet successfully
    └── 98-auto-tests.scenario
        └── Auto-tests for createPet validation
            ├── [Invalid request] request body name with value <empty string>
            ├── [Invalid request] request body name with value <too long>
            ├── [Security SQL Injection] request body name with value ' OR '1'='1
            └── ... (more tests)

Limitations
-----------

* Auto-tests are generated at runtime, not during JUnit discovery
* Tests are generated only for parameters with OpenAPI schema constraints
* Nested object properties are tested but deeply nested structures may generate many tests
* Custom validation rules not expressed in the OpenAPI schema are not tested

See Also
--------

* :doc:`scenario-syntax` - Complete scenario syntax reference
* :doc:`parameters` - Configuration options
* :doc:`reporting` - Test report formats
