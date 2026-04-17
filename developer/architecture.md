# BerryCrush Architecture

This document describes the high-level architecture of BerryCrush and how its components interact.

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              BerryCrush System                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌───────────┐ │
│  │   Kotlin    │     │  Scenario   │     │   JUnit 5   │     │  Spring   │ │
│  │     DSL     │     │   Files     │     │   Tests     │     │   Boot    │ │
│  └──────┬──────┘     └──────┬──────┘     └──────┬──────┘     └─────┬─────┘ │
│         │                   │                   │                   │       │
│         └───────────┬───────┴───────────────────┼───────────────────┘       │
│                     ▼                           ▼                           │
│            ┌─────────────────┐         ┌─────────────────┐                  │
│            │  ScenarioLoader │         │ BerryCrushTest- │                  │
│            │                 │         │     Engine      │                  │
│            └────────┬────────┘         └────────┬────────┘                  │
│                     │                           │                           │
│                     └────────────┬──────────────┘                           │
│                                  ▼                                          │
│                         ┌─────────────────┐                                 │
│                         │ ScenarioExecutor │◄──────┐                        │
│                         └────────┬────────┘        │                        │
│                                  │                 │                        │
│         ┌────────────────────────┼─────────────────┼──────────┐             │
│         ▼                        ▼                 ▼          ▼             │
│  ┌─────────────┐        ┌─────────────┐    ┌──────────┐ ┌───────────┐       │
│  │ SpecRegistry│        │ HttpRequest │    │  Plugin  │ │   Step    │       │
│  │             │        │   Builder   │    │ Registry │ │ Registry  │       │
│  └─────────────┘        └──────┬──────┘    └──────────┘ └───────────┘       │
│                                │                                            │
│                                ▼                                            │
│                        ┌─────────────────┐                                  │
│                        │  HTTP Client    │                                  │
│                        │ (java.net.http) │                                  │
│                        └─────────────────┘                                  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Core Components

### 1. Scenario Definition Layer

Scenarios can be defined in two ways:

#### 1.1 Text-Based Scenario Files (`.scenario`)

Human-readable BDD-style files parsed by the `ScenarioLoader`:

```
scenario: List all pets
  when I request the pet list
    call ^listPets
  then I receive pets
    assert status 200
```

#### 1.2 Kotlin DSL

Type-safe programmatic scenario definition:

```kotlin
suite.scenario("List all pets") {
    `when`("I request the pet list") {
        call("listPets")
    }
    then("I receive pets") {
        statusCode(200)
    }
}
```

### 2. Parsing and Loading

```
┌────────────────┐     ┌────────────────┐     ┌────────────────┐
│     Lexer      │────▶│     Parser     │────▶│ ScenarioLoader │
│                │     │                │     │                │
│ Tokenizes text │     │ Builds AST     │     │ Creates models │
└────────────────┘     └────────────────┘     └────────────────┘
```

**Key Classes:**
- `Lexer` - Tokenizes scenario file content
- `Parser` - Builds Abstract Syntax Tree from tokens
- `ScenarioLoader` - Transforms AST into executable `Scenario` objects

### 3. OpenAPI Integration

BerryCrush uses an abstraction layer to support multiple OpenAPI versions (3.0.x, 3.1.x, and future versions).

```
┌─────────────────┐     ┌───────────────────┐     ┌─────────────────┐
│   OpenAPI Spec  │────▶│   OpenApiParser   │────▶│   OpenApiSpec   │
│  (YAML/JSON)    │     │                   │     │   (interface)   │
│  3.0.x / 3.1.x  │     │ Version-agnostic  │     │                 │
└─────────────────┘     └───────────────────┘     └────────┬────────┘
                                                           │
                        ┌──────────────────────────────────┘
                        ▼
┌─────────────────┐     ┌───────────────────┐     ┌─────────────────┐
│   SpecRegistry  │────▶│ OperationResolver │────▶│   HttpRequest   │
│                 │     │                   │     │    Builder      │
│ Manages specs   │     │ Resolves ops by   │     │                 │
│ (multi-API)     │     │ operationId       │     │                 │
└─────────────────┘     └───────────────────┘     └─────────────────┘
```

**Key Interfaces:**
- `OpenApiSpec` - Unified interface for accessing OpenAPI specs (any version)
- `OpenApiParser` - Parses YAML/JSON into `OpenApiSpec` abstraction
- `OpenApiVersion` - Enum for version detection (V3_0_X, V3_1_X, etc.)

**Key Classes:**
- `SpecRegistry` - Manages one or more OpenAPI specifications
- `OperationResolver` - Resolves `operationId` to HTTP method, path, and parameters
- `SwaggerParserAdapter` - Default `OpenApiParser` implementation

**Version-Specific Features:**
- 3.1.x webhooks accessible via `spec.webhooks`
- JSON Schema 2020-12 support via `SchemaSpec` abstraction
- Feature detection: `spec.hasWebhooks()`, `spec.hasComponents()`

For detailed documentation, see [OpenAPI Version Support](openapi-versioning.md).

### 4. Execution Engine

```
┌─────────────────┐
│ ScenarioExecutor│
└────────┬────────┘
         │
         ├── For each step:
         │
         ├──────────────┐
         │              ▼
         │    ┌──────────────────┐
         │    │  StepRegistry    │
         │    │                  │
         │    │ Custom step      │
         │    │ lookup           │
         │    └────────┬─────────┘
         │             │
         │             │ Found?
         │             │
         │    ┌────────┴─────────┐
         │    │                  │
         ▼    ▼                  ▼
┌──────────────────┐    ┌──────────────────┐
│ HttpRequestBuilder│    │   Custom Step    │
│                  │    │   Execution      │
│ Build request    │    │                  │
│ from operation   │    │ User-defined     │
└────────┬─────────┘    └──────────────────┘
         │
         ▼
┌──────────────────┐
│ ResponseHandler  │
│                  │
│ Process response │
│ Run assertions   │
│ Extract values   │
└──────────────────┘
```

**Key Classes:**
- `ScenarioExecutor` - Orchestrates scenario execution
- `HttpRequestBuilder` - Constructs HTTP requests from OpenAPI operations
- `ResponseHandler` - Processes responses, runs assertions, extracts values
- `StepRegistry` - Manages custom step definitions

### 5. Plugin System

```
┌─────────────────────────────────────────────────────────────────┐
│                        Plugin System                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐                                           │
│  │  PluginRegistry │                                           │
│  └────────┬────────┘                                           │
│           │                                                     │
│           │ Dispatches lifecycle events                         │
│           │                                                     │
│           ├─────────────────────────────────────────────────┐   │
│           │                                                 │   │
│           ▼                                                 ▼   │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ ReportPlugin    │  │ LoggingPlugin   │  │ Custom Plugin   │ │
│  │                 │  │                 │  │                 │ │
│  │ - TextReport    │  │ - Console       │  │ - User-defined  │ │
│  │ - JsonReport    │  │ - HTTP Logger   │  │                 │ │
│  │ - JunitReport   │  │                 │  │                 │ │
│  │ - XmlReport     │  │                 │  │                 │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Plugin Lifecycle:**
1. `onTestExecutionStart()` - Before first scenario
2. `onScenarioStart(context)` - Before each scenario
3. `onStepStart(context)` - Before each step
4. `onStepEnd(context, result)` - After each step
5. `onScenarioEnd(context, result)` - After each scenario
6. `onTestExecutionEnd()` - After all scenarios

### 6. JUnit Integration

```
┌─────────────────────────────────────────────────────────────────┐
│                     JUnit Platform                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────┐                                       │
│  │ BerryCrushTestEngine│ ◄── Registered via ServiceLoader       │
│  │   (ENGINE_ID =      │     META-INF/services/                 │
│  │    "berrycrush")    │     org.junit.platform.engine.TestEngine│
│  └──────────┬──────────┘                                       │
│             │                                                   │
│             │ discover()                                        │
│             ▼                                                   │
│  ┌─────────────────────┐                                       │
│  │ ScenarioDiscovery   │                                       │
│  │                     │                                       │
│  │ Finds @BerryCrush-  │                                       │
│  │ Scenarios classes   │                                       │
│  └──────────┬──────────┘                                       │
│             │                                                   │
│             │ Creates test descriptors                          │
│             ▼                                                   │
│  ┌─────────────────────┐  ┌─────────────────────┐              │
│  │ ClassTestDescriptor │──│ ScenarioFileDesc.  │              │
│  │                     │  │                     │              │
│  │ Per test class      │  │ Per .scenario file  │              │
│  └─────────────────────┘  └──────────┬──────────┘              │
│                                      │                          │
│                                      ▼                          │
│                           ┌─────────────────────┐              │
│                           │ScenarioTestDescriptor│              │
│                           │                     │              │
│                           │ Per scenario        │              │
│                           └─────────────────────┘              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 7. Spring Context Integration

```
┌─────────────────────────────────────────────────────────────────┐
│                   Spring Integration                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  @SpringBootTest                                                │
│  @BerryCrushContextConfiguration(bindings = MyBindings.class)   │
│                                                                 │
│  ┌────────────────────┐     ┌────────────────────┐             │
│  │ SpringBindings-    │────▶│ SpringContext-     │             │
│  │ Provider           │     │ Adapter            │             │
│  │                    │     │                    │             │
│  │ ServiceLoader SPI  │     │ Manages Spring     │             │
│  │ discovery          │     │ ApplicationContext │             │
│  └────────────────────┘     └─────────┬──────────┘             │
│                                       │                         │
│                                       ▼                         │
│                            ┌────────────────────┐              │
│                            │ ApplicationContext │              │
│                            │                    │              │
│                            │ @LocalServerPort   │              │
│                            │ @Autowired beans   │              │
│                            └────────────────────┘              │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Data Flow

### Scenario Execution Flow

```
1. Load OpenAPI spec(s)
   │
   ▼
2. Parse scenario files / Build from DSL
   │
   ▼
3. For each scenario:
   │
   ├── 3a. Execute background steps (if any)
   │
   ├── 3b. For each step:
   │   │
   │   ├── Look up custom step definition
   │   │   │
   │   │   └── If found: Execute custom step
   │   │
   │   ├── If API call step:
   │   │   │
   │   │   ├── Resolve operation from OpenAPI spec
   │   │   │
   │   │   ├── Build HTTP request
   │   │   │   - Substitute variables ({{var}})
   │   │   │   - Apply path/query/header params
   │   │   │   - Set request body
   │   │   │
   │   │   ├── Send request
   │   │   │
   │   │   └── Process response
   │   │       - Schema validation (if enabled)
   │   │       - Run assertions
   │   │       - Extract values
   │   │
   │   └── Record step result
   │
   └── 3c. Record scenario result
   │
   ▼
4. Generate reports
```

### Variable Substitution

Variables are stored in `ExecutionContext` and can be:

1. **Predefined bindings** - From `BerryCrushBindings.getBindings()`
2. **Extracted values** - From response via JSONPath (`extract $.id => petId`)
3. **Cross-scenario** - When `shareVariablesAcrossScenarios = true`

Substitution syntax: `{{variableName}}`

## Key Design Decisions

### 1. OpenAPI-First Validation

All API calls are validated against the OpenAPI specification, ensuring:
- Request parameters match schema
- Response status codes are expected
- Response bodies conform to schemas

### 2. Pluggable Step Definitions

Four mechanisms for custom steps:
1. **Annotation-based** - `@Step("pattern with {int}")`
2. **Registration API** - `registry.register(stepDefinition)`
3. **Kotlin DSL** - `step("pattern") { ... }`
4. **Package scanning** - Auto-discovery

### 3. Modular Design

The library is split into modules:
- `core` - Standalone execution, no framework dependencies
- `junit` - JUnit 5 integration
- `spring` - Spring Boot integration

### 4. Priority-Based Plugin Execution

Plugins execute in priority order (lower first), enabling:
- Setup plugins (negative priority)
- Standard plugins (0)
- Cleanup plugins (positive priority)

## Thread Safety

- `SpecRegistry` is thread-safe for read access
- `ExecutionContext` is not thread-safe (one per scenario)
- `PluginRegistry` dispatches events sequentially
- HTTP client uses `HttpClient` (thread-safe)

## Error Handling

### Scenario Parsing Errors
- `ScenarioParseException` with line numbers and detailed messages

### Execution Errors
- `ConfigurationException` for setup issues
- `AssertionError` for failed assertions
- `StepExecutionException` for step execution failures

### Plugin Errors
- Plugin exceptions fail the entire test run (fail-fast)
- Built-in plugins handle errors gracefully
