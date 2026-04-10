# Scenario File Syntax

This document defines the complete syntax for LemonCheck `.scenario` and `.fragment` files.

## File Types

| Extension | Description |
|-----------|-------------|
| `.scenario` | Test scenario files |
| `.fragment` | Reusable step fragments |

## File Encoding

All files must be UTF-8 encoded.

## Complete Grammar (EBNF)

```ebnf
(* Top-level structure *)
scenario_file     = [ parameters_block ] , { feature | scenario | fragment } ;

(* Parameters block - file-level configuration *)
parameters_block  = "parameters:" , NEWLINE , { parameter_entry } ;
parameter_entry   = INDENT , parameter_name , ":" , parameter_value , NEWLINE ;

(* Tags *)
tags              = { tag } ;
tag               = "@" , identifier ;

(* Feature block with optional background *)
feature           = tags , "feature:" , feature_name , NEWLINE , 
                    [ background ] , { feature_scenario } ;
background        = INDENT , "background:" , NEWLINE , { step } ;
feature_scenario  = INDENT , tags , ( "scenario:" | "outline:" ) , 
                    scenario_name , NEWLINE , { step } , [ examples ] ;

(* Scenario definition *)
scenario          = tags , "scenario:" , scenario_name , NEWLINE , { step } , [ examples ] ;

(* Scenario outline with examples *)
outline           = tags , "outline:" , scenario_name , NEWLINE , { step } , examples ;

(* Fragment definition *)  
fragment          = "fragment:" , fragment_name , NEWLINE , { step } ;

(* Step definition *)
step              = INDENT , step_keyword , step_description , NEWLINE , { step_directive } ;
step_keyword      = "given " | "when " | "then " | "and " | "but " ;

(* Step directives *)
step_directive    = INDENT , INDENT , directive_type , [ directive_value ] , NEWLINE ;
directive_type    = "call" | "assert" | "extract" | "include" | "body:" ;

(* Examples for parameterized scenarios *)
examples          = INDENT , "examples:" , NEWLINE , example_header , { example_row } ;
example_header    = INDENT , INDENT , "|" , { cell , "|" } , NEWLINE ;
example_row       = INDENT , INDENT , "|" , { cell , "|" } , NEWLINE ;

(* Terminals *)
feature_name      = text ;
scenario_name     = text ;
fragment_name     = text ;
step_description  = text ;
parameter_name    = identifier , { "." , identifier } ;
parameter_value   = quoted_string | number | boolean ;
cell              = { character - "|" } ;
text              = { character - NEWLINE } ;
identifier        = letter , { letter | digit | "_" | "-" } ;
INDENT            = "  " ;  (* 2 spaces *)
NEWLINE           = "\n" | "\r\n" ;
```

## Tags

Tags are used to categorize and filter scenarios. They begin with `@` and must appear before the element they annotate.

```
# Tag a single scenario
@smoke @critical
scenario: Critical path test
  when I test
    call ^test

# Tag a feature (inherited by all scenarios in the feature)
@api @regression
feature: API Tests
  scenario: list items
    when I list
      call ^list
```

### Built-in Tags

| Tag | Description |
|-----|-------------|
| `@ignore` | Skip this scenario during execution |
| `@wip` | Work in progress (commonly filtered out) |
| `@slow` | Marks slow-running tests |

### Tag Filtering with JUnit

Use `@LemonCheckTags` annotation to filter scenarios:

```java
// Exclude @ignore tagged scenarios
@LemonCheckTags(exclude = {"ignore"})

// Only run @smoke tagged scenarios
@LemonCheckTags(include = {"smoke"})

// Combine include and exclude
@LemonCheckTags(include = {"api"}, exclude = {"slow", "wip"})
```

## Features and Background

Features provide logical grouping for related scenarios with shared setup via background steps.

### Basic Feature

```
feature: Pet Store API
  scenario: list all pets
    when I list pets
      call ^listPets
    then I get results
      assert status 200
  
  scenario: create a pet
    when I create a pet
      call ^createPet
        body: {"name": "Max"}
    then the pet is created
      assert status 201
```

### Feature with Background

Background steps run before **each** scenario in the feature:

```
feature: Pet Operations
  background:
    given: setup test data
      call ^createPet
        body: {"name": "TestPet"}
      assert status 201
      extract $.id => petId

  scenario: get pet by id
    when: retrieve the pet
      call ^getPetById
        petId: {{petId}}
    then: pet is returned
      assert status 200

  scenario: update pet
    when: update the pet name
      call ^updatePet
        petId: {{petId}}
        body: {"name": "UpdatedPet"}
    then: pet is updated
      assert status 200
```

### Tagged Features

Tags on a feature are inherited by all scenarios within:

```
@api @regression
feature: Authentication Tests
  background:
    given: login
      call ^login
      extract $.token => authToken

  @smoke
  scenario: access profile
    when: get profile
      call ^getProfile
        header_Authorization: "Bearer {{authToken}}"
    then: profile is returned
      assert status 200

  @ignore
  scenario: incomplete test
    when: TODO
      call ^incomplete
```

In this example:
- "access profile" has tags: `api`, `regression`, `smoke`
- "incomplete test" has tags: `api`, `regression`, `ignore`

## Quick Reference

### Basic Scenario

```
scenario: My test scenario
  given some precondition
  when I perform an action
    call ^operationId
  then I should see a result
    assert status 200
```

### With Parameters Block

```
parameters:
  baseUrl: "http://localhost:8080"
  timeout: 60
  shareVariablesAcrossScenarios: true

scenario: Test with custom configuration
  when I call the API
    call ^listPets
  then I get results
    assert status 200
```

## Step Keywords

| Keyword | Description |
|---------|-------------|
| `given` | Precondition setup |
| `when` | Action to perform |
| `then` | Expected outcome |
| `and` | Continuation of previous step type |
| `but` | Exception/negative case |

## Step Directives

### API Call (`call`)

Syntax: `call [using <spec-name>] ^<operationId>`

```
when I request pets
  call ^listPets
```

With named spec (multi-spec):
```
when I authenticate
  call using auth ^login
```

#### Call Parameters

| Parameter | Description | Example |
|-----------|-------------|---------|
| Path parameter | Replace path variable | `petId: 123` |
| Query parameter | Add query string | `status: "available"` |
| Header | Add HTTP header | `header_Authorization: "Bearer token"` |
| Body | Set request body | `body: {"name": "Fluffy"}` |

Example with all parameters:
```
when I create a pet
  call ^createPet
    petId: 123
    status: "available"
    header_Authorization: "Bearer {{token}}"
    body: {"name": "Fluffy", "category": "dog"}
```

### Assertions (`assert`)

#### Status Code
```
assert status 200
assert status 2xx         # Range: 200-299
assert status 201-204     # Range: 201-204
```

#### Body-Level Assertions
```
assert contains "expected text"         # Body contains substring
assert not contains "unexpected text"   # Body does not contain substring
assert schema                            # Validate against OpenAPI schema
```

#### JSONPath Assertions
```
assert $.name equals "Fluffy"
assert $.id exists
assert $.pets notEmpty
assert $.count greaterThan 0
assert $.tags contains "urgent"
assert $.status in ["available", "pending"]
assert $.items hasSize 5
```

#### The `not` Keyword (Negation)

The `not` keyword inverts any assertion, making it a negative check. It can be placed in two positions:

**At the beginning (for body-level assertions):**
```
assert not contains "error"              # Body does NOT contain "error"
```

**After the JSONPath (for JSONPath assertions):**
```
assert $.status not equals "sold"        # Field does NOT equal "sold"
assert $.items not hasSize 0             # Array does NOT have size 0
assert $.deleted not exists              # Field does NOT exist
```

Both positions are supported to allow natural reading: "assert body does not contain" and "assert the name does not equal".

#### Operators

| Operator | Description | Example |
|----------|-------------|---------|
| `equals` | Exact equality | `assert $.name equals "Max"` |
| `not` | Negation (see above) | `assert $.status not equals "sold"` |
| `exists` | Field exists | `assert $.id exists` |
| `notEmpty` | Array/string not empty | `assert $.pets notEmpty` |
| `greaterThan` | Numeric comparison | `assert $.price greaterThan 0` |
| `lessThan` | Numeric comparison | `assert $.age lessThan 10` |
| `contains` | Body/array contains | `assert contains "text"`, `assert $.tags contains "dog"` |
| `in` | Value in list | `assert $.status in ["x", "y"]` |
| `hasSize` | Array/string length | `assert $.items hasSize 3` |
| `matches` | Regex match | `assert $.email matches ".*@.*"` |
| `schema` | Validate against schema | `assert schema` |

### Extraction (`extract`)

Syntax: `extract <jsonPath> => <variableName>`

```
then I capture the ID
  assert status 201
  extract $.id => petId
```

Use extracted variables with `{{variableName}}`:
```
when I get the pet
  call ^getPetById
    petId: {{petId}}
```

### Fragment Inclusion (`include`)

```
scenario: Use authentication
  given I am authenticated
    include authenticate
  when I access protected resource
    call ^getProfile
```

### Request Body (`body:`)

Inline JSON:
```
when I create a pet
  call ^createPet
    body: {"name": "Fluffy", "status": "available"}
```

Multi-line body (use `>` for multi-line):
```
when I create a pet
  call ^createPet
    body: >
      {
        "name": "Fluffy",
        "status": "available",
        "tags": ["cute", "friendly"]
      }
```

## Parameters Block

Place at the top of the file to override default configuration:

```
parameters:
  baseUrl: "http://localhost:8080"
  timeout: 60
  environment: "staging"
  shareVariablesAcrossScenarios: true
  logRequests: true
  logResponses: true
  strictSchemaValidation: false
  followRedirects: true
  header.Authorization: "Bearer test-token"
  header.X-API-Key: "my-api-key"
  autoAssertions.enabled: true
  autoAssertions.schema: false
```

### Supported Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `baseUrl` | String | Override API base URL |
| `timeout` | Number | Request timeout in seconds |
| `environment` | String | Environment name for reports |
| `shareVariablesAcrossScenarios` | Boolean | Share extracted variables across scenarios |
| `logRequests` | Boolean | Enable HTTP request logging |
| `logResponses` | Boolean | Enable HTTP response logging |
| `strictSchemaValidation` | Boolean | Fail on schema validation warnings |
| `followRedirects` | Boolean | Follow HTTP redirects |
| `header.<name>` | String | Add/override default header |
| `autoAssertions.enabled` | Boolean | Enable/disable all auto-assertions |
| `autoAssertions.statusCode` | Boolean | Auto-assert correct status code |
| `autoAssertions.contentType` | Boolean | Auto-assert Content-Type header |
| `autoAssertions.schema` | Boolean | Auto-assert response matches schema |

## Variable Substitution

Variables are referenced using double curly braces: `{{variableName}}`

### Sources

1. **Bindings** - From `LemonCheckBindings.getBindings()`
2. **Extracted values** - From `extract $.path => varName`
3. **Cross-scenario** - When `shareVariablesAcrossScenarios: true`
4. **Example rows** - From `examples:` table

### Example

```
parameters:
  shareVariablesAcrossScenarios: true

scenario: Create and use resource
  when I create a pet
    call ^createPet
      body: {"name": "{{petName}}", "status": "available"}
    extract $.id => petId
  then pet is created
    assert status 201

scenario: Retrieve created resource
  when I get the pet
    call ^getPetById
      petId: {{petId}}
  then I see the pet
    assert status 200
```

### Escaping Variable Syntax

To use literal `{{` or `${` in strings without variable interpolation, escape them with a backslash:

```
# This asserts the literal text "{{petName}}" appears in the body
assert not contains "\\{{petName}}"

# In request bodies
body: {"template": "Hello \\{{name}}"}
```

The escape sequences:
- `\\{{` → literal `{{`
- `\\$` → literal `$`

## Parameterized Scenarios (Scenario Outline)

```
scenario: Create different pets
  when I create a pet
    call ^createPet
      body: {"name": "{{name}}", "category": "{{category}}"}
  then pet is created
    assert status 201
  examples:
    | name   | category |
    | Fluffy | cat      |
    | Buddy  | dog      |
    | Tweety | bird     |
```

This generates 3 scenario runs with different parameter combinations.

## Fragment Files

Fragment files (`.fragment`) define reusable step sequences:

```
# auth.fragment

fragment: authenticate
  given I have valid credentials
    call using auth ^login
      body: {"username": "test", "password": "test"}
  then authentication succeeds
    assert status 200
    extract $.token => authToken

fragment: logout
  when I log out
    call using auth ^logout
      header_Authorization: "Bearer {{authToken}}"
  then session is terminated
    assert status 200
```

Usage in scenario:
```
scenario: Authenticated API access
  given I am authenticated
    include authenticate
  when I access protected endpoint
    call ^getProfile
      header_Authorization: "Bearer {{authToken}}"
  then I see my profile
    assert status 200
```

## Comments

Lines starting with `#` are comments:

```
# This is a comment
scenario: My test
  # This describes the step
  when I do something
    call ^operation
```

## Best Practices

### 1. Use Descriptive Names
```
# Good
scenario: Create pet with valid data returns 201

# Avoid
scenario: Test1
```

### 2. Group Related Assertions
```
then the pet is created correctly
  assert status 201
  assert $.id exists
  assert $.name equals "Fluffy"
  assert $.status equals "available"
```

### 3. Extract Values for Data Flow
```
when I create a pet
  call ^createPet
    body: {"name": "Test"}
  extract $.id => petId

when I retrieve the pet
  call ^getPetById
    petId: {{petId}}
```

### 4. Use Fragments for Common Setup
```
# fragments/auth.fragment
fragment: authenticate
  given I log in
    call using auth ^login
      body: {"username": "test", "password": "test"}
  then I have a token
    extract $.token => authToken
```

### 5. Enable Variable Sharing for Workflows
```
parameters:
  shareVariablesAcrossScenarios: true

scenario: Step 1 - Create
  # Creates resource, extracts ID

scenario: Step 2 - Update
  # Uses ID from Step 1

scenario: Step 3 - Delete
  # Uses ID from Step 1
```

## Error Messages

### Parse Errors

```
ScenarioParseException: Line 5: Expected step keyword (given/when/then/and/but)
  Found: "invalid text here"
```

### Runtime Errors

```
ConfigurationException: Operation 'unknownOperation' not found in OpenAPI spec
```

```
AssertionError: Expected status 200 but got 404
  Request: GET /api/pets/999
```
