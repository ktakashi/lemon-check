---
name: berrycrush
description: Guide for writing BerryCrush scenario and fragment files. Use this when working with .scenario or .fragment files.
argument-hint: Scenario file syntax and best practices
user-invocable: true
---

# BerryCrush Scenario Syntax Guide

This skill provides guidance for writing `.scenario` and `.fragment` files for the BerryCrush OpenAPI-driven BDD testing library.

## File Types

| Extension | Description |
|-----------|-------------|
| `.scenario` | Test scenario files |
| `.fragment` | Reusable step fragments |

## File Encoding

All files must be UTF-8 encoded.

## Basic Structure

### Scenario File
```
scenario: Scenario name
  given precondition description
    # actions (call, extract, include)
  when action description
    # actions
  then assertion description
    # assertions
```

### Fragment File
```
fragment: fragment_name
  given step description
    # actions
```

## Keywords

### Step Keywords
- `given` - Preconditions/setup
- `when` - Actions being tested
- `then` - Expected outcomes
- `and` - Additional steps (same type as previous)
- `but` - Contrasting conditions

### Action Keywords
- `call` - API call using operation ID
- `extract` - Extract values from response
- `assert` - Verify response
- `include` - Include a fragment

## API Calls

### Basic Call
```
call ^operationId
```

### Call with Parameters
```
call ^getPetById
  petId: 123
  header_Authorization: "Bearer {{token}}"
```

### Call with Body (Inline JSON)
```
call ^createPet
  body: {"name": "Fluffy", "status": "available"}
```

### Call with Structured Body
Uses OpenAPI schema defaults for unspecified fields:
```
call ^createPet
  body:
    name: Fluffy
    status: available
```

### Call with Multi-line Body (Triple Quotes)
```
call ^createPet
  body:
    """
    {
      "name": "Fluffy",
      "status": "available",
      "tags": ["cute", "friendly"]
    }
    """
```

### Call with External Body File

Load request body from external files (supports variable interpolation):
```
call ^createPet
  bodyFile: "classpath:templates/pet.json"
```

**Supported path formats:**
- `classpath:path/to/file.json` - Load from classpath (recommended)
- `file:./relative/path.json` - Load from file system
- `/absolute/path.json` - Load from absolute path

**Example template file (`templates/pet.json`):**
```json
{
  "name": "{{petName}}",
  "category": "{{category}}",
  "status": "available"
}
```

> Note: If both `body` and `bodyFile` are specified, inline `body` takes precedence.

### Multi-Spec Call
```
call using petstore ^listPets
call using inventory ^getItems
```

## Assertions

### Status Code
```
assert status 200
assert status 2xx         # Range: 200-299
assert status 201-204     # Range: 201-204
```

### JSON Path Assertions
```
assert $.name equals "Fluffy"
assert $.items notEmpty
assert $.count greaterThan 0
assert $.items[0].id exists
assert $.items hasSize 10
assert $.status not equals "error"    # Negated
```

### Header Assertions
```
assert header Content-Type = "application/json"
assert header X-Request-Id exists
```

### Schema Validation
```
assert schema              # Validate against OpenAPI schema
```

### Response Time
```
assert responseTime 1000   # milliseconds
```

## Conditional Assertions

Conditional assertions allow different assertions based on response conditions. Useful when APIs return different status codes for different scenarios.

### Basic If Condition
```
call ^createPet
if status 201
  assert $.status equals "available"
```

### If-Else
```
call ^createPet
if status 201
  assert $.status equals "created"
else
  fail "Expected status 201"
```

### If-Else If-Else
```
call ^createPet
if status 201
  assert $.status equals "created"
  extract $.id => petId
else if status 200
  assert $.status equals "exists"
else
  fail "status must be 200 or 201"
```

### Condition Types

**Status Code:**
```
if status 201
if status 2xx          # Pattern matching
if status 200-299      # Range
```

**JSON Path:**
```
if $.status equals "active"
if $.count greaterThan 0
if $.items exists
if $.error notExists
```

**Header:**
```
if header Content-Type equals "application/json"
if header X-Cache exists
```

### Condition Operators
- `equals` - Exact match
- `notEquals` - Not equal
- `contains` - String/array contains
- `notContains` - Does not contain
- `matches` - Regex match
- `exists` - Value exists
- `notExists` - Value does not exist
- `greaterThan` - Numeric comparison
- `lessThan` - Numeric comparison

### Fail Action
```
fail "Custom error message"
```

## Variable Extraction

### Extract to Variable
```
extract $.id => petId
extract header X-Request-Id => requestId
```

### Using Variables
```
call ^getPetById
  petId: {{petId}}
body: {"name": "{{name}}"}
```

## Fragments

### Define Fragment
```
fragment: authenticate
  given I have credentials
    call ^login
      body: {"username": "admin", "password": "secret"}
    extract $.token => authToken
```

### Use Fragment
```
scenario: Protected endpoint
  given I am authenticated
    include authenticate
  when I access resource
    call ^getProtectedData
      header_Authorization: "Bearer {{authToken}}"
```

## Tags

```
@smoke @critical
scenario: Critical test
  ...

@api @regression
feature: API Tests
  scenario: List items
    ...
```

### Built-in Tags
- `@ignore` - Skip scenario
- `@wip` - Work in progress
- `@slow` - Marks slow tests

## Features and Backgrounds

```
@api
feature: Pet Store API
  background:
    given user is authenticated
      include authenticate

  scenario: List all pets
    when I list pets
      call ^listPets
    then I get results
      assert status 200

  scenario: Create a pet
    when I create a pet
      call ^createPet
        body:
          name: NewPet
    then it is created
      assert status 201
```

## Scenario Outlines (Parameterized)

```
outline: Test multiple pets
  when I get a pet
    call ^getPetById
      petId: {{petId}}
  then I see the correct name
    assert $.name equals {{expectedName}}
  examples:
    | petId | expectedName |
    | 1     | "Fluffy"     |
    | 2     | "Buddy"      |
```

## Parameters Block

### File-Level Parameters

Place at top of file for configuration:
```
parameters:
  baseUrl: "http://localhost:8080"
  timeout: 60
  shareVariablesAcrossScenarios: true
  logRequests: true
  logResponses: true
  header.Authorization: "Bearer test-token"
```

### Feature-Level Parameters

Configure only scenarios within a specific feature (overrides file-level):
```
feature: Pet CRUD Operations
  parameters:
    shareVariablesAcrossScenarios: true
  
  scenario: Create pet
    when: I create a pet
      call ^createPet
        body: {"name": "SharedPet"}
      extract $.id => petId
  
  scenario: Use shared variable
    # Can access {{petId}} from previous scenario within this feature
    when: I get the pet
      call ^getPetById
        petId: {{petId}}
    then: I see the pet
      assert status 200
```

### Supported Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| `baseUrl` | String | Override API base URL |
| `timeout` | Number | Request timeout in seconds |
| `shareVariablesAcrossScenarios` | Boolean | Share extracted variables |
| `logRequests` | Boolean | Enable HTTP request logging |
| `logResponses` | Boolean | Enable HTTP response logging |
| `strictSchemaValidation` | Boolean | Fail on schema validation warnings |
| `header.<name>` | String | Add/override default header |

## Auto-Generated Tests

Generate invalid request and security tests automatically from OpenAPI schema:

### Basic Usage
```
scenario: Auto-generated tests for createPet
  when I create a pet with invalid data
    call ^createPet
      auto: [invalid security]
      body:
        name: "Fluffy"
        status: "available"
  
  if status 4xx
    # Test passed - invalid request rejected
  else
    fail "Expected 4xx for {{test.type}}: {{test.description}}"
```

### Test Types
- `invalid` - Violates OpenAPI constraints (minLength, maxLength, pattern, required, enum, type)
- `security` - Attack payloads (SQL injection, XSS, path traversal, command injection)

### Path Parameter Tests
```
scenario: Auto-generated tests for getPetById
  when I get a pet with invalid ID
    call ^getPetById
      auto: [invalid security]
      petId: 1
  
  if status 4xx
    # Test passed
```

### Context Variables
- `test.type` - "invalid" or "security"
- `test.field` - Field being tested (e.g., "name", "petId")
- `test.description` - Human-readable description
- `test.value` - The invalid/attack value used
- `test.location` - Where parameter is ("request body", "path variable", "query parameter", "header")

### Test Display Names
Auto-tests appear in reports as:
```
[Invalid request] request body name with value <empty string>
[Security SQL Injection] path variable petId with value ' OR '1'='1
```

## Best Practices

1. **Keep scenarios focused** - One behavior per scenario
2. **Use meaningful names** - Describe what is being tested
3. **Extract reusable steps** - Use fragments for common patterns
4. **Use structured body** - Leverage schema defaults for cleaner tests
5. **Tag appropriately** - Enable flexible test filtering
6. **Use backgrounds** - Share setup across feature scenarios

## Example Complete Scenario

```
parameters:
  shareVariablesAcrossScenarios: true

@smoke
scenario: Pet CRUD operations
  given user is authenticated
    include authenticate
  when I create a new pet
    call ^createPet
      body:
        name: TestPet
        status: available
    extract $.id => petId
  then the pet is created
    assert status 201
    assert $.name equals "TestPet"
  and I can retrieve it
    call ^getPetById
      petId: {{petId}}
    assert status 200
    assert $.name equals "TestPet"
  when I delete the pet
    call ^deletePetById
      petId: {{petId}}
  then it is removed
    assert status 204
```

## Custom Steps

Custom steps allow you to extend BerryCrush with reusable, domain-specific steps implemented in Kotlin or Java.

### Defining Custom Steps

Use the `@Step` annotation:
```kotlin
import org.berrycrush.step.Step
import org.berrycrush.step.StepContext

class PetstoreSteps {
    @Step("create a test pet named {string}")
    fun createTestPet(name: String, context: StepContext) {
        // Perform actions
        context.setVariable("petName", name)
    }

    @Step("I have {int} pets with status {word}")
    fun setupPets(count: Int, status: String, context: StepContext) {
        // Create multiple pets
    }
}
```

### Pattern Placeholders

| Placeholder | Matches | Example |
|-------------|---------|---------|
| `{string}` | Quoted string | `"hello"` |
| `{int}` | Integer | `42` |
| `{float}` | Decimal | `3.14` |
| `{word}` | Single word | `available` |
| `{any}` | Any text (greedy) | `hello world` |

### StepContext API

The `StepContext` provides access to the execution context:

| Method | Description |
|--------|-------------|
| `variable(name)` | Get a variable by name |
| `setVariable(name, value)` | Set a scenario-scoped variable |
| `setSharedVariable(name, value)` | Set a suite-scoped (shared) variable |
| `allVariables()` | Get all current variables |
| `lastResponse` | The last HTTP response (or null) |
| `configuration` | The current execution configuration |

### Using Custom Steps

```
scenario: Create and verify pet
  given: create a test pet named "Fluffy"
  then: the pet should have status "available"
```

### Configuration

Register step classes in `@BerryCrushConfiguration`:
```java
@BerryCrushConfiguration(
    openApiSpec = "petstore.yaml",
    stepClasses = {PetstoreSteps.class}
)
public class PetstoreScenarioTest {}
```

Or via package scanning:
```java
@BerryCrushConfiguration(
    openApiSpec = "petstore.yaml",
    stepPackages = {"com.example.steps"}
)
public class PetstoreScenarioTest {}
```

## Custom Assertions

Custom assertions extend the `assert` directive with domain-specific validation logic.

### Defining Custom Assertions

Use the `@Assertion` annotation:
```kotlin
import org.berrycrush.assertion.Assertion
import org.berrycrush.assertion.AssertionContext
import org.berrycrush.assertion.AssertionResult

class PetstoreAssertions {
    @Assertion("pet name is {string}")
    fun assertPetName(expectedName: String, context: AssertionContext): AssertionResult {
        val actualName: String? = context.response.jsonPath.read("$.name")
        return if (actualName == expectedName) {
            AssertionResult.success()
        } else {
            AssertionResult.failure("Expected name '$expectedName' but got '$actualName'")
        }
    }

    @Assertion("pet is available")
    fun assertPetAvailable(context: AssertionContext): AssertionResult {
        val status: String? = context.response.jsonPath.read("$.status")
        return if (status == "available") {
            AssertionResult.success()
        } else {
            AssertionResult.failure("Pet status is '$status', expected 'available'")
        }
    }
}
```

### Using Custom Assertions

```
scenario: Verify pet response
  when: I get the pet
    call ^getPetById
      petId: 123
  then: the pet exists
    assert status 200
    assert pet name is "Fluffy"
    assert pet is available
```

### Configuration

Register assertion classes in `@BerryCrushConfiguration`:
```java
@BerryCrushConfiguration(
    openApiSpec = "petstore.yaml",
    assertionClasses = {PetstoreAssertions.class}
)
public class PetstoreScenarioTest {}
```

## Variable Scopes

Variables can be set with different scopes:

### Scenario-Scoped Variables (Default)

Variables set with `setVariable()` are isolated to the current scenario:
```kotlin
context.setVariable("tempId", generateId())  // Only visible in current scenario
```

### Suite-Scoped Variables (Shared)

Variables set with `setSharedVariable()` are shared across scenarios when sharing is enabled:
```kotlin
context.setSharedVariable("authToken", getAuthToken())  // Shared when enabled
```

> If `shareVariablesAcrossScenarios` is disabled, `setSharedVariable()` behaves like `setVariable()`.

### Variable Lookup Priority

1. Scenario-scoped variables (checked first)
2. Shared variables (checked second)

## Escaping Variable Syntax

To use literal `{{` or `${` in strings without interpolation, escape with backslash:

```
# Assert literal text "{{petName}}" appears in body
assert not contains "\\{{petName}}"

# In request bodies
body: {"template": "Hello \\{{name}}"}
```

Escape sequences:
- `\\{{` → literal `{{`
- `\\$` → literal `$`

