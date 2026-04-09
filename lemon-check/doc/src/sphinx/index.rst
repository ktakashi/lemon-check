LemonCheck Documentation
========================

LemonCheck is an OpenAPI-driven BDD-style API testing library for Kotlin and Java.

.. note::
   This library is currently in early development. APIs may change.

.. toctree::
   :maxdepth: 2
   :caption: Getting Started

   quickstart
   tutorial

.. toctree::
   :maxdepth: 2
   :caption: Features

   features/kotlin-dsl
   features/standalone-runner
   features/parameters
   features/plugins
   features/custom-steps
   features/reporting
   features/multi-spec
   features/fragments

.. toctree::
   :maxdepth: 2
   :caption: Guides

   migration
   troubleshooting

.. toctree::
   :maxdepth: 1
   :caption: Reference

   API Documentation <https://ktakashi.github.io/lemon-check/api/>

Key Features
------------

* **OpenAPI-driven**: Automatically validate requests and responses against your OpenAPI spec
* **BDD-style scenarios**: Write readable tests using a Gherkin-like DSL
* **JUnit 5 integration**: Seamless integration with your existing test infrastructure
* **Spring Boot support**: Auto-discover bindings and configuration from Spring context
* **Plugin system**: Extend functionality with custom plugins for reporting, logging, and more
* **Custom steps**: Define reusable step definitions with annotations, DSL, or registration API
* **Multi-spec support**: Work with multiple OpenAPI specifications in a single test suite
* **Fragments**: Create reusable scenario steps that can be included across tests

Quick Example
-------------

.. code-block:: kotlin

    @IncludeEngines("lemoncheck")
    @LemonCheckScenarios(locations = "scenarios/pet-api.scenario")
    @LemonCheckConfiguration(
        bindings = PetStoreBindings::class,
        openApiSpec = "petstore.yaml"
    )
    class PetApiTest

Scenario file (``pet-api.scenario``):

.. code-block:: gherkin

    Feature: Pet API
      Background:
        Given the petstore API is available

      Scenario: List all pets
        When I request GET /api/pets
        Then the response status should be 200
        And the response body should match the schema

Installation
------------

Add to your ``build.gradle.kts``:

.. code-block:: kotlin

    dependencies {
        testImplementation("io.github.ktakashi.lemoncheck:core:0.1.0")
        testImplementation("io.github.ktakashi.lemoncheck:junit:0.1.0")
        // For Spring Boot projects
        testImplementation("io.github.ktakashi.lemoncheck:spring:0.1.0")
    }

Indices and tables
==================

* :ref:`genindex`
* :ref:`search`
