# Scenario File Syntax

This document defines the complete syntax for BerryCrush `.scenario` and `.fragment` files.

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

(* Parameters block - file-level or feature-level configuration *)
parameters_block  = "parameters:" , NEWLINE , { parameter_entry } ;
parameter_entry   = INDENT , parameter_name , ":" , parameter_value , NEWLINE ;

(* Tags *)
tags              = { tag } ;
tag               = "@" , identifier ;

(* Feature block with optional parameters and background *)
feature           = tags , "feature:" , feature_name , NEWLINE , 
                    [ INDENT , parameters_block ] ,
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
directive_type    = "call" | "assert" | "extract" | "include" | "body:" | conditional ;

(* Assertions *)
assertion         = "assert" , [ "not" ] , condition ;

(* Conditional branching - if/else if/else *)
conditional       = if_branch , { else_if_branch } , [ else_branch ] ;
if_branch         = "if" , condition , NEWLINE , { conditional_action } ;
else_if_branch    = "else if" , condition , NEWLINE , { conditional_action } ;
else_branch       = "else" , NEWLINE , { conditional_action } ;
conditional_action = assertion | extraction | fail_action | conditional ;
fail_action       = "fail" , quoted_string ;

(* Conditions - unified for both 'if' and 'assert' *)
condition         = status_condition | jsonpath_condition | header_condition 
                  | body_contains | schema_condition | response_time
                  | variable_condition ;
status_condition  = "status" , ( number | status_range ) ;
status_range      = digit , "xx" | number , "-" , number ;
jsonpath_condition = jsonpath , [ "not" ] , operator , [ value ] ;
header_condition  = "header" , header_name , [ "not" ] , operator , [ value ] ;
body_contains     = "contains" , quoted_string ;
schema_condition  = "schema" ;
response_time     = "responseTime" , number ;
variable_condition = variable_path , [ "not" ] , operator , [ value ] ;

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

Use `@BerryCrushTags` annotation to filter scenarios:

```java
// Exclude @ignore tagged scenarios
@BerryCrushTags(exclude = {"ignore"})

// Only run @smoke tagged scenarios
@BerryCrushTags(include = {"smoke"})

// Combine include and exclude
@BerryCrushTags(include = {"api"}, exclude = {"slow", "wip"})
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
| Body (inline) | Set JSON body directly | `body: {"name": "Fluffy"}` |
| Body (structured) | Set body with properties | `body:` newline + indented properties |

Example with inline body:
```
when I create a pet
  call ^createPet
    petId: 123
    status: "available"
    header_Authorization: "Bearer {{token}}"
    body: {"name": "Fluffy", "category": "dog"}
```

Example with structured body:
```
when I create a pet
  call ^createPet
    body:
      name: Fluffy
      category: dog
```

### Assertions (`assert`)

> **Note:** Assertions and `if` conditions share the same condition evaluation logic.
> The same operators and syntax work identically in both contexts.

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

#### Response Time Assertions
```
assert responseTime 1000       # Response must complete within 1000ms
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

#### Header Assertions
```
assert header Content-Type equals "application/json"
assert header X-Request-Id exists
assert header Cache-Control contains "no-cache"
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

### Conditional Assertions (`if/else if/else/fail`)

Conditional assertions allow branching logic based on response status, JSON path values, or headers.

> **Note:** `if` conditions and `assert` statements share the same condition evaluation logic.
> This means the same condition types (status, jsonpath, header) work identically in both contexts.

#### Basic Syntax

```
when I make a request
  call ^operation
  
  if status 201
    # Executed if status is 201 (created)
    assert $.id notEmpty
    extract $.id => createdId
  else if status 200
    # Executed if status is 200 (already exists)
    assert $.id notEmpty
  else
    # Executed if none of the above matched
    fail "Expected status 200 or 201"
```

#### Condition Types

Both `if` conditions and `assert` statements share a unified condition syntax:

| Type | Syntax | Example |
|------|--------|---------|
| Status code | `status <code>` | `status 201` |
| Status range | `status <range>` | `status 2xx` |
| JSON path | `$.path <op> <value>` | `$.count greaterThan 0` |
| Header | `header <name> <op> <value>` | `header Content-Type equals "application/json"` |
| Body contains | `contains <text>` | `contains "success"` |
| Schema validation | `schema` | `schema` |
| Response time | `responseTime <ms>` | `responseTime 1000` |
| Variable | `<variable> <op> <value>` | `test.type equals "invalid"` |

> **Note:** While all condition types are syntactically valid in both contexts, some are more commonly used in assertions (schema, responseTime) and some in conditionals (variable).

#### JSON Path Operators

Same operators work in both `if` and `assert` contexts: `equals`, `exists`, `notEmpty`, `greaterThan`, `lessThan`, `contains`, `in`, `hasSize`, `matches`.

```
if $.items greaterThan 0
  assert $.items[0].id notEmpty
else
  # Empty list is acceptable
  assert $.items hasSize 0
```

#### Nested Conditionals

Conditionals can be nested for complex logic:

```
if status 200
  if $.status equals "available"
    assert $.price notEmpty
  else
    assert $.status notEmpty
else if status 201
  assert $.id notEmpty
else
  fail "Unexpected status"
```

#### The `fail` Action

Use `fail` to explicitly fail a scenario with a custom message:

```
else
  fail "Expected status 200 or 201 but got something else"
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

The `body:` keyword supports two modes:

#### Inline JSON (Raw Mode)

Specify JSON directly on the same line:

```
when I create a pet
  call ^createPet
    body: {"name": "Fluffy", "status": "available"}
```

#### Structured Properties

Use indented properties to generate JSON with OpenAPI schema defaults:

```
when I create a pet
  call ^createPet
    body:
      name: Fluffy
      status: available
```

This generates a JSON body by:
1. Extracting default values from the OpenAPI requestBody schema
2. Merging with user-provided properties (user values take precedence)
3. Generating the final JSON

**Example with partial data:**
```
# Only specify name, other fields use schema defaults
when I create a pet
  call ^createPet
    body:
      name: MyPet
```

**Example with nested objects:**
```
when I create a pet
  call ^createPet
    body:
      name: NestedPet
      metadata:
        source: test
        version: 1.0
```

#### Multi-line Body (Triple Quotes)

Use triple quotes (`"""`) for multi-line raw body content. The common indentation is automatically removed:

```
when I create a pet
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

This sends the JSON exactly as written (with common indentation stripped). This is useful for:
- Large JSON payloads that are hard to read on one line
- Copying JSON from other sources
- Complex nested structures

#### Multi-line JSON (Legacy)

Use `>` for multi-line raw JSON:
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

## Auto-Generated Tests (`auto:`)

The `auto:` directive enables automatic generation of invalid request and security tests based on OpenAPI schema constraints and common attack patterns.

### Syntax

```
call ^operationId
  auto: [<test-types>]
  <base-parameters>
```

Where `<test-types>` is a space-separated list of:
- `invalid` - Generate tests that violate OpenAPI schema constraints
- `security` - Generate tests with common attack payloads

### Basic Example

```
scenario: Auto-generated tests for createPet
  when I create a pet with invalid request
    call ^createPet
      auto: [invalid security]
      body:
        name: "Fluffy"
        status: "available"
  
  if status 4xx and test.type equals invalid
    # Invalid tests should return 4xx - test passed
  else if status 4xx and test.type equals security
    # Security tests should return 4xx - attack blocked
  else
    fail "Expected 4xx for {{test.type}} test: {{test.description}}"
```

### Test Types

#### Invalid Tests (`invalid`)

Generate tests that violate OpenAPI schema constraints:

| Constraint | Test Generated |
|------------|----------------|
| `minLength` | String with length below minimum |
| `maxLength` | String with length above maximum |
| `minimum` | Number below minimum value |
| `maximum` | Number above maximum value |
| `pattern` | String that violates regex pattern |
| `format` (email) | Invalid email format |
| `format` (uuid) | Invalid UUID format |
| `format` (date) | Invalid date format |
| `required` | Missing required fields |
| `enum` | Value not in allowed enum |
| Type mismatch | Wrong type (e.g., string instead of number) |

#### Security Tests (`security`)

Generate tests with common attack payloads:

| Category | Examples |
|----------|----------|
| SQL Injection | `' OR '1'='1`, `"; DROP TABLE users; --` |
| XSS | `<script>alert('XSS')</script>`, `javascript:alert(1)` |
| Path Traversal | `../../etc/passwd`, `....//....//etc/passwd` |
| Command Injection | `; ls -la`, `$(whoami)`, `\`id\`` |
| LDAP Injection | `*)(uid=*))(|(uid=*`, `admin)(&)` |
| XXE | `<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>` |

### Parameter Locations

Auto-tests are generated for parameters in different locations:

| Location | Description | Example |
|----------|-------------|---------|
| `request body` | JSON body fields | `name`, `status` |
| `path variable` | URL path parameters | `petId` in `/pets/{petId}` |
| `query parameter` | Query string params | `status` in `?status=available` |
| `header` | HTTP headers | `Authorization`, `X-Api-Key` |

### Context Variables

During auto-test execution, these variables are set:

| Variable | Description | Example Values |
|----------|-------------|----------------|
| `test.type` | Test category | `"invalid"`, `"security"` |
| `test.field` | Field being tested | `"name"`, `"petId"` |
| `test.description` | Test description | `"SQL Injection"`, `"minLength violation"` |
| `test.value` | Attack/invalid value | `"' OR '1'='1"` |
| `test.location` | Parameter location | `"request body"`, `"path variable"` |

### Complete Example

```
scenario: Auto-generated path parameter tests for getPetById
  when I get a pet with invalid ID
    call ^getPetById
      auto: [invalid security]
      petId: 1
  
  if status 4xx
    # Test passed - invalid request rejected
  else
    fail "Expected 4xx for [{{test.type}}] {{test.location}} test: {{test.description}}"

scenario: Auto-generated create pet tests
  when I create a pet with invalid data
    call ^createPet
      auto: [invalid security]
      body:
        name: "TestPet"
        status: "available"
  
  if status 4xx and test.type equals invalid
    # Invalid input correctly rejected
  else if status 4xx and test.type equals security
    # Security attack blocked
  else if not status 2xx
    # Unexpected error
    fail "Unexpected error for {{test.type}}: {{test.description}}"
```

### Test Display Names

Auto-tests appear in test reports with descriptive names:

```
[Invalid request] request body name with value <empty string>
[Invalid request] path variable petId with value not-a-number
[Security SQL Injection] request body name with value ' OR '1'='1
[Security Path Traversal] path variable petId with value ../../etc/passwd
```

### Best Practices

1. **Provide valid base parameters** - Auto-tests modify one parameter at a time while keeping others valid
2. **Use conditional assertions** - Check `test.type` to handle invalid vs security tests differently
3. **Expect 4xx responses** - Both invalid and security tests should be rejected by a secure API
4. **Review generated tests** - The number of tests depends on schema constraints; complex schemas generate more tests

## Parameters Block

Parameters can be specified at two levels:

### File-Level Parameters

Place at the top of the file to configure all scenarios:

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

### Feature-Level Parameters

Place inside a feature block to configure only scenarios in that feature:

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
    # Can access {{petId}} from previous scenario
    when: I get the pet
      call ^getPetById
        petId: {{petId}}
    then: I see the pet
      assert status 200
```

Feature-level parameters override file-level parameters for scenarios in that feature.

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

1. **Bindings** - From `BerryCrushBindings.getBindings()`
2. **Extracted values** - From `extract $.path => varName`
3. **Cross-scenario (file-level)** - When `shareVariablesAcrossScenarios: true` at file level
4. **Cross-scenario (feature-level)** - When `shareVariablesAcrossScenarios: true` inside a feature
5. **Example rows** - From `examples:` table

### Scope Rules

- File-level sharing: Variables shared across ALL scenarios in the file
- Feature-level sharing: Variables shared only within that feature's scenarios
- Standalone scenarios: Not affected by feature-level sharing

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

## Custom Steps

Custom steps allow you to extend BerryCrush with reusable, domain-specific steps implemented in Kotlin.

### Defining Custom Steps

Use the `@Step` annotation to define custom steps:

```kotlin
import org.berrycrush.step.Step
import org.berrycrush.step.StepContext

class PetstoreSteps {
    @Step("create a test pet named {string}")
    fun createTestPet(context: StepContext, name: String) {
        val response = context.httpClient.post("/pet") {
            json(mapOf("name" to name, "status" to "available"))
        }
        context.variables["petId"] = response.jsonPath.read<Int>("$.id")
    }
    
    @Step("the pet should have status {string}")
    fun verifyPetStatus(context: StepContext, expectedStatus: String) {
        val petId = context.variables["petId"]
        val response = context.httpClient.get("/pet/$petId")
        val actualStatus: String = response.jsonPath.read("$.status")
        check(actualStatus == expectedStatus) {
            "Expected status '$expectedStatus' but got '$actualStatus'"
        }
    }
}
```

### Step Patterns

Step patterns use `{string}`, `{int}`, `{float}` placeholders:

| Placeholder | Matches | Example |
|-------------|---------|---------|
| `{string}` | Quoted string | `"hello"` |
| `{int}` | Integer | `42` |
| `{float}` | Decimal | `3.14` |

### Using Custom Steps in Scenarios

```
scenario: Create and verify pet
  given: create a test pet named "Fluffy"
  then: the pet should have status "available"
```

### StepContext

The `StepContext` provides access to:

| Property | Description |
|----------|-------------|
| `variables` | Read/write scenario variables |
| `httpClient` | Preconfigured HTTP client |
| `specRegistry` | OpenAPI specs |
| `config` | Current configuration |

### Configuration

Register step classes in `@BerryCrushConfiguration`:

```java
@BerryCrushConfiguration(
    openApiSpec = "petstore.yaml",
    stepClasses = {PetstoreSteps.class, CommonSteps.class}
)
public class PetstoreScenarioTest {}
```

## Custom Assertions

Custom assertions allow you to extend BerryCrush's `assert` directive with domain-specific validation logic.

### Defining Custom Assertions

Use the `@Assertion` annotation to define custom assertions:

```kotlin
import org.berrycrush.assertion.Assertion
import org.berrycrush.assertion.AssertionContext
import org.berrycrush.assertion.AssertionResult

class PetstoreAssertions {
    @Assertion("pet name is {string}")
    fun assertPetName(context: AssertionContext, expectedName: String): AssertionResult {
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

### Assertion Patterns

Assertion patterns use the same placeholders as custom steps:
- `{string}` - Quoted string
- `{int}` - Integer
- `{float}` - Decimal number

### Using Custom Assertions in Scenarios

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

### AssertionContext

The `AssertionContext` provides access to:

| Property | Description |
|----------|-------------|
| `response` | HTTP response with jsonPath access |
| `variables` | Current scenario variables |
| `config` | Current configuration |

### AssertionResult

Return from assertion methods:
- `AssertionResult.success()` - Assertion passed
- `AssertionResult.failure("message")` - Assertion failed with message

### Configuration

Register assertion classes in `@BerryCrushConfiguration`:

```java
@BerryCrushConfiguration(
    openApiSpec = "petstore.yaml",
    stepClasses = {PetstoreSteps.class},
    assertionClasses = {PetstoreAssertions.class}
)
public class PetstoreScenarioTest {}
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
