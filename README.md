# Lemon-Check

**BDD-style API testing framework for Java/Kotlin with OpenAPI integration**

Lemon-Check enables you to write human-readable scenario files that test your REST APIs against their OpenAPI specifications. It integrates seamlessly with JUnit 5 and optionally with Spring Boot for dependency injection.

## Features

- **BDD Scenario Format**: Write tests in plain text using Given/When/Then syntax
- **OpenAPI Integration**: Validate requests/responses against your API spec
- **Auto-Generated Tests**: Automatically generate invalid request and security tests from OpenAPI schemas
- **JUnit 5 Engine**: Run scenarios as JUnit tests with full IDE support
- **Spring Boot Integration**: Inject `@LocalServerPort`, `@Autowired` in bindings
- **JSONPath Assertions**: Validate response data with JSONPath expressions
- **Variable Extraction**: Extract values from responses for use in subsequent calls
- **Multi-Spec Support**: Work with multiple OpenAPI specifications in a single test suite
- **Fragments**: Create reusable scenario steps that can be included across tests
- **Plugin System**: Extend functionality with plugins for reporting, logging, and custom actions
- **Custom Steps**: Define domain-specific steps via annotations, DSL, or registration API

## Quick Start

### 1. Add Dependencies

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("io.github.ktakashi:lemon-check-junit:1.0.0")
    
    // Optional: For Spring Boot integration
    testImplementation("io.github.ktakashi:lemon-check-spring:1.0.0")
}
```

### 2. Create a Scenario File

Create `src/test/resources/scenarios/pet-api.scenario`:

```
# Pet API Scenarios

scenario: List all pets
  when I request all pets
    call ^listPets
  then I get a successful response
    assert status 200
    assert $.pets notEmpty

scenario: Create a new pet
  when I create a pet
    call ^createPet
      body: {"name": "Fluffy", "category": "cat", "status": "available"}
  then the pet is created
    assert status 201
    assert $.name equals "Fluffy"
    extract $.id => petId

scenario: Get pet by ID
  when I request the pet
    call ^getPetById
      petId: {{petId}}
  then I see the pet details
    assert status 200
    assert $.name equals "Fluffy"
```

### 3. Create a Test Class

#### Without Spring Integration

```java
@Suite
@IncludeEngines("lemoncheck")
@LemonCheckScenarios(locations = "scenarios/*.scenario")
@LemonCheckConfiguration(bindings = PetBindings.class, openApiSpec = "petstore.yaml")
public class PetApiTest {
}
```

```java
public class PetBindings implements LemonCheckBindings {
    
    @Override
    public Map<String, Object> getBindings() {
        return Map.of("baseUrl", "http://localhost:8080/api");
    }
    
    @Override
    public String getOpenApiSpec() {
        return "petstore.yaml";
    }
}
```

#### With Spring Boot Integration

```java
@Suite
@IncludeEngines("lemoncheck")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@LemonCheckContextConfiguration
@LemonCheckScenarios(locations = "scenarios/*.scenario")
@LemonCheckConfiguration(bindings = PetBindings.class, openApiSpec = "petstore.yaml")
public class PetApiTest {
}
```

```java
@Component
@Lazy  // Required for @LocalServerPort
public class PetBindings implements LemonCheckBindings {
    
    @LocalServerPort
    private int port;
    
    @Override
    public Map<String, Object> getBindings() {
        return Map.of("baseUrl", "http://localhost:" + port + "/api");
    }
    
    @Override
    public String getOpenApiSpec() {
        return "petstore.yaml";
    }
    
    @Override
    public void configure(Configuration config) {
        config.setBaseUrl("http://localhost:" + port + "/api");
    }
}
```

### 4. Run Tests

```bash
./gradlew test
```

## Scenario File Syntax

### Basic Structure

```
scenario: <Scenario Name>
  given <precondition description>
    <actions>
  when <action description>
    <actions>
  then <expected outcome>
    <actions>
```

### API Calls

Reference operations by their OpenAPI `operationId` with `^` prefix:

```
call ^operationId
  pathParam: value
  queryParam: value
  body: {"json": "payload"}
```

### Path Parameters

```
call ^getPetById
  petId: 123
```

### Query Parameters

```
call ^listPets
  status: available
  limit: 10
```

### Request Body

#### Inline Body

```
call ^createPet
  body: {"name": "Fluffy", "status": "available"}
```

#### External Body File

For large or reusable request bodies, use external files with `bodyFile`:

```
call ^createPet
  bodyFile: "classpath:templates/create-pet.json"
```

**Supported path formats:**
- `classpath:path/to/file.json` - Load from classpath
- `file:./relative/path.json` - Load from file system (relative)
- `/absolute/path.json` - Load from absolute path

**Variable interpolation** is supported in external body files. Use `{{variableName}}` syntax:

**templates/create-pet.json:**
```json
{
  "name": "{{petName}}",
  "category": "{{category}}",
  "status": "available"
}
```

Variables from previous extractions or example rows are automatically substituted.

### Assertions

| Assertion | Description |
|-----------|-------------|
| `assert status <code>` | Check HTTP status code |
| `assert $.path equals <value>` | Check JSONPath value equals |
| `assert $.path notEmpty` | Check JSONPath result is not empty |
| `assert $.path contains <text>` | Check value contains text |

### Variable Extraction

Extract values for use in later scenarios:

```
extract $.id => petId
```

Use variables with double braces:

```
call ^getPetById
  petId: {{petId}}
```

### Conditional Assertions

Handle different response scenarios with `if/else if/else/fail`:

```
when I upsert a resource
  call ^updateOrCreate
    id: 123
    body: {"name": "Test"}
    
  if status 201
    # Resource was created
    assert $.id notEmpty
    extract $.id => createdId
  else if status 200
    # Resource was updated
    assert $.name equals "Test"
  else
    fail "Expected status 200 or 201"
```

Conditions support status codes, JSON path values, and headers:
- `if status 200`
- `if $.count greaterThan 0`
- `if header Content-Type equals "application/json"`

### Auto-Generated Tests

Automatically generate invalid request and security tests based on OpenAPI schema:

```
scenario: Auto-generated tests for createPet
  when I create a pet with invalid input
    call ^createPet
      auto: [invalid security]
      body:
        name: "TestPet"
        status: "available"
  
  if status 4xx
    # Invalid/security test rejected - passed
  else
    fail "Expected 4xx for {{test.type}}: {{test.description}}"
```

Available test types:
- `invalid` - Violates OpenAPI constraints (minLength, maxLength, pattern, required, enum)
- `security` - Attack payloads (SQL injection, XSS, path traversal, command injection)

Context variables available during auto-tests:
- `test.type` - "invalid" or "security"
- `test.field` - Field being tested
- `test.description` - Human-readable description
- `test.location` - "request body", "path variable", "query parameter", or "header"

### File-Level Parameters

Override configuration settings at the file level using a `parameters:` block:

```
parameters:
  shareVariablesAcrossScenarios: true
  timeout: 60
  header.Authorization: "Bearer test-token"

scenario: Create a resource
  when I create a pet
    call ^createPet
      body: {"name": "Fluffy"}
    extract $.id => petId

scenario: Use the resource (variables shared across scenarios)
  when I get the pet
    call ^getPetById
      petId: {{petId}}
  then I see the pet
    assert $.name equals "Fluffy"
```

Available parameters:
- `baseUrl` - Override the base URL
- `baseUrl.<specName>` - Override base URL for a specific spec (multi-host testing)
- `timeout` - Request timeout in seconds
- `shareVariablesAcrossScenarios` - Share extracted variables across scenarios in the file
- `logRequests`, `logResponses` - Enable request/response logging
- `header.<name>` - Add/override default headers

### Multi-Spec Support

Register multiple OpenAPI specs in your bindings:

```java
@Override
public Map<String, String> getAdditionalSpecs() {
    return Map.of("auth", "auth.yaml", "inventory", "inventory.yaml");
}
```

For microservices testing with different hosts, provide per-spec base URLs:

```java
@Override
public Map<String, String> getSpecBaseUrls() {
    return Map.of(
        "default", "http://petstore-service:" + petstorePort,
        "auth", "http://auth-service:" + authPort,
        "inventory", "http://inventory-service:" + inventoryPort
    );
}
```

Use the `using` keyword to call operations from named specs:

```
call using auth ^login
  body: {"username": "test", "password": "test"}
```

### Fragments

Create reusable steps in `.fragment` files. By default, fragments are discovered from
`lemoncheck/fragments/*.fragment`:

**lemoncheck/fragments/auth.fragment:**
```
fragment: authenticate
  given I have valid credentials
    call using auth ^login
      body: {"username": "test", "password": "test"}
    extract $.token => authToken
  then authentication is successful
    assert status 200
```

Include fragments in scenarios:

```
scenario: Authenticated API access
  given I am logged in
    include authenticate
  when I request protected data
    call ^getSecretData
      header_Authorization: Bearer {{authToken}}
```

Configure custom fragment locations (overriding defaults):

```java
@LemonCheckScenarios(
    locations = {"scenarios/*.scenario"},
    fragments = {"auth/*.fragment", "common/*.fragment"}
)
public class MyApiTest {
}
```

## Module Overview

| Module | Description |
|--------|-------------|
| `lemon-check/core` | Scenario parser, executor, assertions |
| `lemon-check/junit` | JUnit 5 TestEngine integration |
| `lemon-check/spring` | Spring TestContext integration |
| `lemon-check/doc` | Sphinx documentation |
| `samples/petstore` | Complete working example |

## Annotations Reference

| Annotation | Module | Purpose |
|------------|--------|---------|
| `@LemonCheckScenarios` | junit | Specify scenario and fragment file locations (defaults: `lemoncheck/scenarios/*.scenario`, `lemoncheck/fragments/*.fragment`) |
| `@LemonCheckConfiguration` | junit | Configure bindings class and OpenAPI spec |
| `@LemonCheckSpec` | junit | Specify additional OpenAPI specs |
| `@LemonCheckContextConfiguration` | spring | Enable Spring context integration |

## Spring Integration Notes

### @Lazy Requirement

When using `@LocalServerPort`, the bindings class must be annotated with `@Lazy`:

```java
@Component
@Lazy  // Port is set after server starts
public class MyBindings implements LemonCheckBindings {
    @LocalServerPort
    private int port;
}
```

### Test Isolation

- Scenarios in the same test class share the Spring context
- Database changes persist across scenarios
- Control execution order with filename prefixes: `01-setup.scenario`, `99-cleanup.scenario`

## Example Project

See the complete working example in [samples/petstore](samples/petstore):

```
samples/petstore/
├── src/main/java/           # Spring Boot application
├── src/test/java/           # Test classes
│   └── PetstoreScenarioTest.java
│   └── PetstoreBindings.java
└── src/test/resources/
    ├── petstore.yaml        # OpenAPI specification
    └── scenarios/           # Scenario files
        ├── 01-get-pet.scenario
        ├── create-pet.scenario
        └── 99-delete-pet.scenario
```

## Building from Source

```bash
git clone https://github.com/ktakashi/lemon-check.git
cd lemon-check
./gradlew build
```

## Requirements

- Java 21 or higher
- Kotlin 2.x (for Kotlin DSL scenarios)
- JUnit Platform 6.0+
- Spring Boot 4.x (for Spring integration)

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.
