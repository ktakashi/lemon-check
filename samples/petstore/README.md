# Petstore Sample Application

This directory contains a sample Pet Store API application and tests demonstrating BerryCrush's capabilities.

## Directory Structure

```
petstore/
├── app/              # Spring Boot Pet Store API application
├── kotlin-dsl/       # Kotlin DSL tests using BerryCrush programmatic API
├── scenario/         # BDD-style tests using .scenario files
└── README.md         # This file
```

## Modules

### app

The Pet Store API is a Spring Boot application that provides REST endpoints for managing pets:

- `GET /api/v1/pets` - List all pets
- `GET /api/v1/pets/{id}` - Get a pet by ID
- `POST /api/v1/pets` - Create a new pet
- `PUT /api/v1/pets/{id}` - Update a pet
- `DELETE /api/v1/pets/{id}` - Delete a pet

The application uses H2 in-memory database with test data seeded via `data.sql`.

**Run the application:**
```bash
./gradlew :samples:petstore:app:bootRun
```

### kotlin-dsl

Demonstrates BerryCrush's Kotlin DSL for programmatic test definition. This approach is ideal for:

- IDE auto-completion and type safety
- Dynamic test generation
- Integration with existing Kotlin/Java test frameworks
- Custom test logic and assertions

**Example:**
```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
class PetstoreDslTest {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `list all pets`() {
        val suite = berrycrush(specPath) {
            baseUrl = "http://localhost:$port/api/v1"
        }
        
        val scenario = suite.scenario("List pets") {
            whenever("I request all pets") {
                call("listPets")
            }
            afterwards("I receive a successful response") {
                statusCode(200)
                bodyArrayNotEmpty("$.pets")
            }
        }
        
        val result = ScenarioExecutor(suite.specRegistry, suite.configuration).execute(scenario)
        assertEquals(ResultStatus.PASSED, result.status)
    }
}
```

**Run the tests:**
```bash
./gradlew :samples:petstore:kotlin-dsl:test
```

### scenario

Demonstrates BerryCrush's `.scenario` file format with the BerryCrush TestEngine. This approach is ideal for:

- Human-readable BDD-style specifications
- Non-programmer stakeholder collaboration
- Living documentation
- Rapid test authoring

**Example scenario file:**
```
scenario: List all pets
  when I request the list of pets
    call ^listPets
  then I receive a successful response
    assert status 200
    assert $.pets notEmpty
```

**Run the tests:**
```bash
./gradlew :samples:petstore:scenario:test
```

## Running All Tests

```bash
./gradlew :samples:petstore:scenario:test :samples:petstore:kotlin-dsl:test
```

## Key Features Demonstrated

| Feature | kotlin-dsl | scenario |
|---------|------------|----------|
| Basic scenarios | ✓ | ✓ |
| Path/query parameters | ✓ | ✓ |
| Request body | ✓ | ✓ |
| Status code assertions | ✓ | ✓ |
| JSON path assertions | ✓ | ✓ |
| Header assertions | ✓ | ✓ |
| Variable extraction | ✓ | ✓ |
| Fragments | ✓ | ✓ |
| Scenario outlines | ✓ | ✓ |
| Conditional assertions | - | ✓ |
| Auto-generated tests | - | ✓ |
| Tags and filtering | ✓ | ✓ |
| Spring Boot integration | ✓ | ✓ |

## OpenAPI Specification

Both test modules use the OpenAPI specification located at:
- `kotlin-dsl/src/test/resources/petstore.yaml`
- `scenario/src/test/resources/petstore.yaml`

The specification defines all available operations and their request/response schemas.
