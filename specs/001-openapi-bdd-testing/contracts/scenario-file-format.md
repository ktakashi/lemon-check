# Scenario File Format Contract

**Feature**: 001-openapi-bdd-testing  
**Date**: 2026-04-07  
**Version**: 1.0.0

This document defines the human-readable BDD scenario file format (`.scenario` files).

---

## File Extension

`.scenario` - Plain text UTF-8 encoded files

---

## Complete Grammar (EBNF)

```ebnf
(* Top-level structure *)
scenario_file     = { metadata } , feature ;

(* Metadata section - file-level configuration *)
metadata          = "@" , identifier , ":" , value , NEWLINE ;

(* Feature definition *)
feature           = "Feature:" , text , NEWLINE ,
                    [ description ] ,
                    [ background ] ,
                    { scenario } ;

description       = { INDENT , text , NEWLINE } ;

(* Background - shared setup for all scenarios *)
background        = INDENT , "Background:" , text , NEWLINE ,
                    { step } ;

(* Scenario types *)
scenario          = INDENT , ( "Scenario:" | "Scenario Outline:" ) , text , NEWLINE ,
                    [ tags ] ,
                    { step } ,
                    [ examples ] ;

tags              = INDENT , INDENT , "@" , identifier , { " " , "@" , identifier } , NEWLINE ;

(* Steps - the BDD actions *)
step              = INDENT , INDENT , step_keyword , text , NEWLINE ,
                    [ step_table ] ;

step_keyword      = "Given " | "When " | "Then " | "And " | "But " ;

(* Step configuration table *)
step_table        = { INDENT , INDENT , INDENT , "|" , directive , "|" , value , "|" , NEWLINE } ;

directive         = "operation" | "using" | "path" | "query" | "header" | "body" 
                  | "extract" | "assert" | "auto-assert" | "include" ;

(* Examples for Scenario Outline *)
examples          = INDENT , INDENT , "Examples:" , NEWLINE ,
                    INDENT , INDENT , INDENT , header_row ,
                    { INDENT , INDENT , INDENT , data_row } ;

header_row        = "|" , { cell , "|" } , NEWLINE ;
data_row          = "|" , { cell , "|" } , NEWLINE ;

(* Terminals *)
cell              = { ? any character except "|" and NEWLINE ? } ;
text              = { ? any character except NEWLINE ? } ;
value             = { ? any character except NEWLINE ? } ;
identifier        = letter , { letter | digit | "_" | "-" } ;
INDENT            = "  " ;  (* 2 spaces *)
NEWLINE           = "\n" | "\r\n" ;
```

---

## Syntax Reference

### 1. Metadata Section

File-level configuration at the top of the file.

**Single Spec**:
```gherkin
@openapi: path/to/spec.yaml
@baseUrl: https://api.example.com
@timeout: 30s
@environment: staging
```

**Multiple Specs** (for microservices/multi-API testing):
```gherkin
@openapi: petstore=specs/petstore.yaml, inventory=specs/inventory.yaml, orders=specs/orders.yaml
@baseUrl.petstore: https://petstore.example.com
@baseUrl.inventory: https://inventory.example.com
@baseUrl.orders: https://orders.example.com
@timeout: 30s
@environment: staging
@auto-assert: true
```

| Key | Required | Description |
|-----|----------|-------------|
| `@openapi` | Yes | Path to OpenAPI spec(s). Single: `path.yaml`, Multiple: `name=path, name2=path2` |
| `@baseUrl` | No | Override base URL (supports `${env:VAR}`) |
| `@baseUrl.<name>` | No | Override base URL for specific spec (multi-spec only) |
| `@timeout` | No | Default request timeout |
| `@environment` | No | Environment name for reporting |
| `@tags` | No | Default tags for all scenarios |
| `@auto-assert` | No | Enable/disable auto-assertions globally (default: true) |

---

### 1b. Parameters Block (Alternative File-Level Configuration)

For simplified scenario files (without `Feature:` block), a `parameters:` section provides
file-level configuration that overrides bindings configuration for all scenarios in that file.

**Syntax**:
```
parameters:
  baseUrl: "http://localhost:8080"
  timeout: 60
  shareVariablesAcrossScenarios: true
  header.Authorization: "Bearer test-token"

scenario: My test scenario
  when I call the API
    call ^listPets
```

| Parameter | Type | Description |
|-----------|------|-------------|
| `baseUrl` | String | Override the base URL for API requests |
| `timeout` | Number | Request timeout in seconds |
| `environment` | String | Environment name for reporting |
| `strictSchemaValidation` | Boolean | Fail on schema validation warnings |
| `followRedirects` | Boolean | Follow HTTP redirects (default: true) |
| `logRequests` | Boolean | Log HTTP requests |
| `logResponses` | Boolean | Log HTTP responses |
| `shareVariablesAcrossScenarios` | Boolean | Share extracted variables across scenarios in this file |
| `header.<name>` | String | Add/override a default header |
| `autoAssertions.enabled` | Boolean | Enable/disable all auto-assertions |
| `autoAssertions.statusCode` | Boolean | Auto-assert correct status code |
| `autoAssertions.contentType` | Boolean | Auto-assert Content-Type header |
| `autoAssertions.schema` | Boolean | Auto-assert response matches schema |

**Example (Cross-Scenario Variable Sharing)**:
```
parameters:
  shareVariablesAcrossScenarios: true

scenario: Create a resource
  when I create a pet
    call ^createPet
      body: {"name": "Fluffy"}
    extract $.id => petId

scenario: Use the resource
  when I get the pet
    call ^getPetById
      petId: {{petId}}
  then I see the pet
    assert $.name equals "Fluffy"
```

---

### 2. Feature Block

```gherkin
Feature: Pet Store API
  As a customer
  I want to browse available pets
  So that I can find a pet to adopt
```

- **Line 1**: Feature name (required)
- **Lines 2+**: Description (optional, indented)

---

### 3. Background

Shared setup steps executed before each scenario.

```gherkin
  Background: Authenticated session
    Given I have a valid auth token
      | operation | authenticate                    |
      | body      | {"user": "test", "pass": "123"} |
      | extract   | token -> $.accessToken          |
```

---

### 4. Scenario

A single test case.

```gherkin
  Scenario: List all available pets
    @smoke @pets
    Given the pet store is accessible
      | operation | healthCheck |
      | assert    | status = 200 |
    
    When I request the pet list
      | operation | listPets |
      | query     | status = available |
    
    Then I receive a list of pets
      | assert    | status = 200 |
      | assert    | $.pets is not empty |
```

---

### 5. Scenario Outline (Parameterized)

```gherkin
  Scenario Outline: Validate pet creation
    When I create a pet named "<name>" with tag "<tag>"
      | operation | createPet |
      | body      | {"name": "<name>", "tag": "<tag>"} |
    
    Then the response status is <status>
      | assert    | status = <status> |
    
    Examples:
      | name    | tag  | status |
      | Buddy   | dog  | 201    |
      |         | cat  | 400    |
      | X*256   | bird | 400    |
```

- `<placeholder>` in steps are replaced with values from the Examples table
- Each row generates a separate test execution

---

### 6. Step Table Directives

#### `using` - Select OpenAPI Spec (Multi-Spec)

Switches which OpenAPI spec to use for operation resolution. Required when multiple specs are loaded and operationId is ambiguous.

```gherkin
| using | petstore |
| using | inventory |
```

**Example (Cross-Service Flow)**:
```gherkin
  Scenario: Cross-service order
    Given pet exists
      | using     | petstore |
      | operation | getPetById |
      | path      | petId = 123 |
      | extract   | price -> $.price |
    
    When placing order
      | using     | orders |
      | operation | createOrder |
      | body      | {"petId": 123, "price": ${price}} |
```

#### `operation` - API Call

Specifies which OpenAPI operation to invoke.

```gherkin
| operation | getPetById |
```

#### `path` - Path Parameters

```gherkin
| path | petId = 123 |
| path | petId = ${extractedId} |
```

#### `query` - Query Parameters

```gherkin
| query | limit = 10 |
| query | status = available |
| query | tags = dog,cat |
```

#### `header` - Request Headers

```gherkin
| header | Authorization = Bearer ${token} |
| header | X-Request-Id = ${env:REQUEST_ID} |
```

#### `body` - Request Body

```gherkin
| body | {"name": "Rex", "tag": "dog"} |
| body | {"items": [1, 2, 3]} |
```

Multi-line body using continuation:
```gherkin
| body | {                    |
| body |   "name": "Rex",     |
| body |   "tag": "dog"       |
| body | }                    |
```

#### `extract` - Value Extraction

```gherkin
| extract | petId -> $.id |
| extract | firstName -> $.user.name.first |
| extract | allIds -> $.items[*].id |
```

Format: `variableName -> jsonPath`

#### `assert` - Assertions

```gherkin
| assert | status = 200 |
| assert | status in 200..299 |
| assert | $.name = "Rex" |
| assert | $.pets is not empty |
| assert | $.count > 0 |
| assert | $.email matches /\S+@\S+/ |
| assert | schema valid |
| assert | response time < 500ms |
```

#### `auto-assert` - Control Auto-Assertions

Controls whether assertions are auto-generated from the OpenAPI spec for this step.

```gherkin
| auto-assert | false |      # Disable all auto-assertions
| auto-assert | true |       # Enable (default)
| auto-assert | no-schema |  # Disable only schema validation
| auto-assert | no-status |  # Disable only status code assertion
```

**When to use**: Testing error cases, partial responses, or when spec doesn't match actual behavior.

**Example (Error Case)**:
```gherkin
  Scenario: Request non-existent pet
    When I request a pet that doesn't exist
      | operation   | getPetById |
      | path        | petId = 999999 |
      | auto-assert | false |
      | assert      | status = 404 |
```

**Without any assertions**: If no `assert` directives are present and `auto-assert` is not `false`, assertions are automatically derived from the OpenAPI spec's success response (status code, schema validation, content-type).

#### `include` - Fragment Reference

```gherkin
| include | authenticate-admin |
```

---

### 7. Assertion Expressions

| Expression | Description | Example |
|------------|-------------|---------|
| `= value` | Equals | `status = 200` |
| `!= value` | Not equals | `$.status != "deleted"` |
| `> value` | Greater than | `$.count > 0` |
| `< value` | Less than | `$.price < 100` |
| `>= value` | Greater or equal | `$.age >= 18` |
| `<= value` | Less or equal | `$.items <= 50` |
| `in range` | In range | `status in 200..299` |
| `contains text` | String contains | `$.name contains "Rex"` |
| `matches /regex/` | Regex match | `$.email matches /\S+@\S+/` |
| `is empty` | Collection empty | `$.errors is empty` |
| `is not empty` | Collection has items | `$.pets is not empty` |
| `is null` | Value is null | `$.deletedAt is null` |
| `is not null` | Value exists | `$.id is not null` |
| `schema valid` | Matches OpenAPI schema | `schema valid` |
| `response time < Xms` | Performance check | `response time < 500ms` |

---

### 8. Variable Interpolation

```text
${variableName}     Extract value from previous step
${env:VAR_NAME}     Environment variable
<placeholder>       Scenario Outline parameter (in Examples)
```

**Examples**:
```gherkin
| path   | petId = ${firstPetId} |
| header | Authorization = Bearer ${env:API_TOKEN} |
| body   | {"name": "<name>"} |
```

---

### 9. Comments

Lines starting with `#` are comments:

```gherkin
# This is a comment
Feature: My Feature
  # This describes the background
  Background: Setup
```

---

### 10. Fragments (Reusable Steps)

Fragments are defined in separate files with `.fragment` extension:

**fragments/authenticate.fragment**:
```gherkin
Fragment: authenticate-admin
  Given I am authenticated as admin
    | operation | login |
    | body      | {"user": "admin", "pass": "${env:ADMIN_PASS}"} |
    | extract   | authToken -> $.token |
```

Referenced in scenarios:
```gherkin
  Scenario: Admin creates pet
    Given I am set up
      | include | authenticate-admin |
    
    When I create a pet
      | operation | createPet |
      | header    | Authorization = Bearer ${authToken} |
```

---

## Example Complete File

```gherkin
# petstore-tests.scenario
@openapi: specs/petstore.yaml
@baseUrl: ${env:PETSTORE_URL}
@timeout: 30s

Feature: Pet Store Customer Journey
  A customer browses the pet store, finds a pet they like,
  views its details, and completes a purchase.

  Background: Customer session
    Given I have a customer session
      | operation | createSession |
      | extract   | sessionId -> $.sessionId |

  Scenario: Complete pet purchase journey
    @smoke @e2e @purchase
    
    Given pets are available in the store
      | operation | listPets |
      | query     | status = available |
      | assert    | status = 200 |
      | assert    | $.pets is not empty |
      | extract   | targetPetId -> $.pets[0].id |
      | extract   | petPrice -> $.pets[0].price |
    
    When I view the pet details
      | operation | getPetById |
      | path      | petId = ${targetPetId} |
      | assert    | status = 200 |
      | assert    | $.status = "available" |
    
    And I add the pet to my cart
      | operation | addToCart |
      | body      | {"petId": "${targetPetId}", "sessionId": "${sessionId}"} |
      | assert    | status = 201 |
    
    And I complete the purchase
      | operation | checkout |
      | body      | {"sessionId": "${sessionId}", "paymentMethod": "card"} |
      | assert    | status = 200 |
      | extract   | orderId -> $.orderId |
    
    Then the pet is marked as sold
      | operation | getPetById |
      | path      | petId = ${targetPetId} |
      | assert    | status = 200 |
      | assert    | $.status = "sold" |
    
    And I can view my order
      | operation | getOrder |
      | path      | orderId = ${orderId} |
      | assert    | status = 200 |
      | assert    | $.totalPrice = ${petPrice} |

  Scenario Outline: Pet creation validation
    @validation
    
    When I attempt to create a pet with name "<name>"
      | operation | createPet |
      | body      | {"name": "<name>", "tag": "<tag>", "price": <price>} |
    
    Then the response indicates <result>
      | assert    | status = <status> |
    
    Examples:
      | name      | tag  | price | status | result  |
      | Buddy     | dog  | 99.99 | 201    | success |
      |           | cat  | 50.00 | 400    | error   |
      | X*256     | bird | 25.00 | 400    | error   |
      | Rex       | dog  | -10   | 400    | error   |
```

---

## Parser API

```kotlin
// Load and parse scenarios
val parser = ScenarioParser()

// Parse single file
val feature = parser.parse(Path.of("scenarios/petstore.scenario"))

// Parse directory
val features = parser.parseDirectory(Path.of("scenarios/"))

// Convert to executable scenarios
val suite = lemonCheck("petstore.yaml") {
    loadScenarios(features)
}

// Or load directly
val suite = lemonCheck("petstore.yaml") {
    loadScenariosFrom("scenarios/")
}

// Run all loaded scenarios
suite.runAll()
```

---

## Error Messages

Parser provides clear error messages with location:

```text
petstore.scenario:15:5: error: Unknown operation 'getPet'. Did you mean 'getPetById'?
    | operation | getPet |
                  ^~~~~~

petstore.scenario:23:3: error: 'Then' step cannot appear before 'When'
    Then I see the pet
    ^~~~

petstore.scenario:31:5: error: Variable 'petId' used but never extracted
    | path | petId = ${petId} |
                      ^~~~~~~
```

---

## Versioning

Format version is tracked via metadata:
```gherkin
@format: 1.0
```

Current version: **1.0**
