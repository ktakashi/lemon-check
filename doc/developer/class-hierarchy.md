# Class Hierarchy

This document provides a comprehensive view of the key classes and interfaces in BerryCrush.

## Core Module (`berrycrush/core`)

### Domain Model

```text
org.berrycrush.model
├── Scenario                    # Test scenario with steps
├── Step                        # Single step in a scenario
├── Directive (sealed)          # Canonical step directive model
│   ├── CallDirective           # Operation/raw call + request settings
│   ├── AssertionDirective      # Single assertion
│   ├── ConditionalDirective    # Conditional assertion tree
│   ├── ExtractionDirective     # Response extraction
│   ├── IncludeDirective        # Fragment include + parameters
│   ├── FailDirective           # Unconditional fail action
│   └── WebhookDirective        # Webhook server setup
├── StepType                    # Enum: GIVEN, WHEN, THEN, AND, BUT
├── Fragment                    # Reusable step collection
├── ExampleRow                  # Parameter row for scenario outline
├── Extraction                  # Value extraction definition
├── Assertion (sealed)          # Unified assertion model
│   ├── BuiltinAssertion        # Condition-based assertion
│   ├── ConditionalAssertion    # if/else assertion tree
│   └── CustomAssertion         # Programmatic assertion callback
├── ScenarioResult              # Execution result for a scenario
├── StepResult                  # Execution result for a step
├── AssertionResult             # Result of an assertion
├── AssertionResults            # Aggregated assertion evaluation result
├── ResultStatus                # Enum: PASSED, FAILED, SKIPPED, ERROR
├── ValidationError             # Schema validation error
└── FragmentRegistry            # Registry for fragments by name
```

### Step Compatibility Notes

- `Step` is now canonically represented by `directives: List<Directive>`.
- Legacy constructor parameters and getter properties on `Step` are preserved as `@Deprecated` compatibility members.
- Legacy-to-directive mapping examples:
    - `step.operationId` -> `step.callDirective?.operationId`
    - `step.assertions` -> `step.directiveAssertions`
    - `step.extractions` -> `step.directiveExtractions`
    - `step.fragmentName` -> `step.includeDirective?.fragmentName`
    - `step.webhookConfig` -> `step.webhookDirective?.config`
- `Step.copy(...)` now follows the canonical data-class signature (`type`, `description`, `directives`, `sourceLocation`).

### DSL Classes

```text
org.berrycrush.dsl
├── @BerryCrushDsl              # DSL marker annotation
├── BerryCrushSuite             # Main entry point for DSL
│   ├── spec(path)              # Register OpenAPI spec
│   ├── configure { }           # Configure execution
│   ├── scenario(name) { }      # Define scenario
│   ├── scenarioOutline(name) { }  # Define parameterized scenario
│   └── fragment(name) { }      # Define fragment
├── ScenarioScope               # DSL scope for scenarios
│   ├── given(description) { }
│   ├── when(description) { }
│   ├── then(description) { }
│   ├── and(description) { }
│   ├── but(description) { }
│   └── include(fragment)
├── ScenarioOutlineScope        # DSL scope for parameterized scenarios
│   ├── (same as ScenarioScope)
│   └── examples { }
├── StepScope                   # DSL scope for step definition
│   ├── using(specName)
│   ├── call(operationId) { }
│   ├── extractTo(var, jsonPath)
│   ├── statusCode(expected)
│   ├── bodyEquals(path, value)
│   └── bodyArrayNotEmpty(path)
├── CallScope                   # DSL scope for API calls
│   ├── pathParam(name, value)
│   ├── queryParam(name, value)
│   ├── header(name, value)
│   ├── body(content)
│   ├── bearerToken(token)
│   ├── basicAuth(user, pass)
│   └── apiKey(key)
└── FragmentScope               # DSL scope for fragments
```

### Scenario Parsing

```text
org.berrycrush.scenario
├── Lexer                       # Tokenizes scenario files
│   └── tokenize(source): List<Token>
├── Parser                      # Parses tokens into AST
│   └── parse(source): ParseResult
├── ScenarioLoader              # Loads scenarios from files/strings
│   ├── loadScenariosFromFile(path)
│   ├── loadScenariosFromString(source)
│   ├── loadFileContent(path)   # Returns ScenarioFileContent
│   └── loadFragmentsFromFile(path)
├── ScenarioFileContent         # Parsed file with ordered top-level entries and parameters
├── Story (sealed)              # Ordered loader/runtime top-level entry
│   ├── ScenarioEntry
│   └── FeatureGroup
├── Token                       # Lexer token
├── TokenType                   # Token type enum
└── AST Node Classes
    ├── StoryNode (sealed)      # Ordered top-level AST entry
    ├── ScenarioNode
    ├── FeatureNode
    ├── StepNode
    ├── FragmentNode
    └── ParametersNode
```

### Execution Engine

```text
org.berrycrush.executor
├── ScenarioExecutor            # Main executor
│   ├── execute(scenario, context)
│   └── execute(scenario, sharedContext, sourceFile)
├── HttpRequestBuilder          # Builds HTTP requests
│   ├── build(operation, params)
│   └── substituteVariables(template, context)
├── AssertionExecutor           # Unified assertion execution entrypoint
│   └── runAssertions(response, assertions, context): AssertionResults
└── ResponseProcessor           # Processes HTTP responses
    └── process(step, response, context)
```

### OpenAPI Integration

```text
org.berrycrush.openapi
├── SpecRegistry                # Manages OpenAPI specs
│   ├── registerDefault(path, config)
│   ├── register(name, path, config)
│   ├── getDefault(): OpenAPI?
│   └── get(name): OpenAPI?
├── OperationResolver           # Resolves operations
│   ├── resolve(operationId): ResolvedOperation?
│   └── resolve(specName, operationId): ResolvedOperation?
├── OpenApiLoader               # Loads OpenAPI specs
│   └── load(path): OpenAPI
└── ResolvedOperation           # Resolved operation data
    ├── method: HttpMethod
    ├── path: String
    ├── parameters: List<Parameter>
    └── requestBody: RequestBody?
```

### Step Definitions

```text
org.berrycrush.step
├── @Step                       # Annotation for step methods
│   ├── pattern: String         # Step pattern with placeholders
│   └── description: String     # Optional description
├── StepDefinition              # Represents a step definition
│   ├── pattern: String
│   ├── method: Method
│   ├── instance: Any?
│   └── description: String
├── StepRegistry                # Interface for step lookup
│   ├── register(definition)
│   ├── registerAll(definitions)
│   └── find(stepText): StepMatch?
├── DefaultStepRegistry         # Default implementation
├── StepMatcher                 # Matches step text to patterns
│   └── match(pattern, text): MatchResult?
├── AnnotationStepScanner       # Scans classes for @Step
│   ├── scan(clazz): List<StepDefinition>
│   └── scanAll(vararg classes)
├── PackageStepScanner          # Scans packages for steps
│   └── scan(packageName): List<StepDefinition>
└── StepDsl                     # DSL for step definitions
    └── step(pattern) { ... }
```

### Plugin System

```text
org.berrycrush.plugin
├── BerryCrushPlugin            # Plugin interface
│   ├── id: String
│   ├── name: String
│   ├── priority: Int
│   ├── onTestExecutionStart()
│   ├── onTestExecutionEnd()
│   ├── onScenarioStart(context)
│   ├── onScenarioEnd(context, result)
│   ├── onStepStart(context)
│   └── onStepEnd(context, result)
├── PluginRegistry              # Manages plugins
│   ├── register(plugin)
│   ├── register(pluginClass)
│   ├── registerByName(name)
│   ├── replace(plugin)
│   ├── dispatchTestExecutionStart()
│   ├── dispatchTestExecutionEnd()
│   ├── dispatchScenarioStart(context)
│   ├── dispatchScenarioEnd(context, result)
│   ├── dispatchStepStart(context)
│   └── dispatchStepEnd(context, result)
├── PluginNameResolver          # Resolves plugin names
│   └── resolve(name): BerryCrushPlugin
├── ScenarioContext             # Context for scenario lifecycle
├── StepContext                 # Context for step lifecycle
├── ScenarioResult              # Plugin-facing result
└── StepResult                  # Plugin-facing step result
```

### Reporting

```text
org.berrycrush.report
├── ReportPlugin (abstract)     # Base class for report plugins
│   ├── outputPath: Path
│   ├── generateReport()
│   ├── buildReport(): TestReport
│   └── formatReport(report): String  # Abstract
├── TextReportPlugin            # Human-readable text format
├── JsonReportPlugin            # JSON format
├── XmlReportPlugin             # XML format
├── JunitReportPlugin           # JUnit XML format (CI/CD)
├── TestReport                  # Report data model
├── ScenarioReportEntry         # Scenario in report
├── StepReportEntry             # Step in report
└── TestSummaryBuilder          # Builds test summary
```

### Configuration

```text
org.berrycrush.config
├── Configuration               # Main configuration
│   ├── baseUrl: String?
│   ├── timeout: Duration
│   ├── defaultHeaders: Map
│   ├── environment: String?
│   ├── autoAssertions: AutoAssertionConfig
│   ├── strictSchemaValidation: Boolean
│   ├── followRedirects: Boolean
│   ├── logRequests: Boolean
│   ├── logResponses: Boolean
│   ├── httpLogger: HttpLogger?
│   ├── logFormatter: HttpLogFormatter?
│   └── shareVariablesAcrossScenarios: Boolean
├── AutoAssertionConfig         # Auto-assertion settings
│   ├── enabled: Boolean
│   ├── statusCode: Boolean
│   ├── contentType: Boolean
│   └── schema: Boolean
└── SpecConfiguration           # Per-spec configuration
    ├── baseUrl: String?
    └── headers: Map
```

### Context and Variables

```text
org.berrycrush.context
├── ExecutionContext            # Runtime variable storage
│   ├── get(name): Any?
│   ├── set(name, value)
│   ├── createChild(): ExecutionContext
│   ├── allVariables(): Map
│   └── substitute(template): String
└── ValueExtractor              # Extracts values from responses
    ├── extractJsonPath(json, path): Any?
    └── extractHeader(response, name): String?
```

### Logging

```text
org.berrycrush.logging
├── HttpLogger                  # Interface for HTTP logging
│   ├── logRequest(method, url, headers, body)
│   └── logResponse(method, url, response, duration)
├── HttpLogFormatter            # Interface for formatting
│   ├── formatRequest(method, url, headers, body)
│   └── formatResponse(method, url, response, duration)
├── ConsoleHttpLogger           # Console output logger
├── JulHttpLogger               # java.util.logging logger
└── HttpLoggerFactory           # Creates default loggers
```

### Exceptions

```text
org.berrycrush.exception
├── BerryCrushException         # Base exception
├── ConfigurationException      # Configuration errors
├── ScenarioParseException      # Parsing errors
├── StepExecutionException      # Step execution errors
└── AssertionException          # Assertion failures
```

## JUnit Module (`berrycrush/junit`)

### Annotations

```text
org.berrycrush.junit
├── @BerryCrushScenarios        # Specify scenario locations
│   ├── locations: Array<String>
│   └── fragments: Array<String>
├── @BerryCrushConfiguration    # Configure execution
│   ├── bindings: KClass
│   ├── timeout: Long
│   ├── plugins: Array<String>
│   ├── pluginClasses: Array<KClass>
│   ├── stepClasses: Array<KClass>
│   └── stepPackages: Array<String>
├── @BerryCrushSpec             # Specify OpenAPI specs
│   ├── paths: Array<String>
│   ├── baseUrl: String
│   └── name: String
├── @BerryCrushTags             # Filter by tags
│   ├── include: Array<String>
│   └── exclude: Array<String>
└── @BerryCrushTimeout          # Scenario timeout
    ├── value: Long
    └── unit: TimeUnit
```

### Bindings

```
org.berrycrush.junit
├── BerryCrushBindings          # Interface for runtime bindings
│   ├── getBindings(): Map<String, Any>
│   ├── configure(config)
│   ├── getPlugins(): List<BerryCrushPlugin>
│   └── getStepClasses(): Array<Class<*>>
├── DefaultBindings             # No-op implementation
└── ScenarioTest (abstract)     # Base class for DSL tests
    ├── configureSuite()        # Override to configure
    └── defineScenarios()       # Override to define scenarios
```

### Configuration Values

```
org.berrycrush.config
└── OpenApiSpecValue            # OpenAPI spec configuration
    ├── location: String
    └── baseUrl: String?
```

### Test Engine

```
org.berrycrush.junit.engine
├── BerryCrushTestEngine        # JUnit 5 TestEngine
│   ├── getId(): String         # "berrycrush"
│   ├── discover(request, id): TestDescriptor
│   └── execute(request)
├── BerryCrushEngineDescriptor  # Root test descriptor
├── ClassTestDescriptor         # Per-class descriptor
├── ScenarioFileDescriptor      # Per-file descriptor
├── ScenarioTestDescriptor      # Per-scenario descriptor
└── TestDescriptors             # Descriptor utilities
```

### Discovery

```
org.berrycrush.junit.discovery
├── ScenarioDiscovery           # Discovers scenario files
│   └── discoverScenarios(classLoader, patterns)
├── FragmentDiscovery           # Discovers fragment files
│   └── discoverFragments(classLoader, patterns)
└── ResourceDiscovery           # General resource discovery
    └── discoverResources(classLoader, patterns)
```

### SPI

```
org.berrycrush.junit.spi
└── BindingsProvider            # SPI for custom bindings creation
    ├── supports(testClass): Boolean
    ├── priority(): Int
    ├── initialize(testClass)
    ├── createBindings(testClass, bindingsClass)
    └── cleanup(testClass)
```

## Spring Module (`berrycrush/spring`)

```
org.berrycrush.spring
├── @BerryCrushContextConfiguration  # Spring integration annotation
├── SpringBindingsProvider      # BindingsProvider for Spring
│   ├── supports(testClass): Boolean
│   ├── priority(): Int         # 100 (high priority)
│   ├── initialize(testClass)
│   ├── createBindings(testClass, bindingsClass)
│   └── cleanup(testClass)
├── SpringContextAdapter        # Spring ApplicationContext bridge
│   ├── initializeContext()
│   ├── getBean<T>(type): T
│   └── cleanup()
└── SpringStepDiscovery         # Auto-discover steps from beans
    └── discoverSteps(context): List<StepDefinition>
```

## Class Relationships

### Scenario Execution Flow

```
BerryCrushTestEngine
    │
    ├── uses ──▶ ScenarioDiscovery
    │               │
    │               └── returns ──▶ Scenario files
    │
    ├── uses ──▶ ScenarioLoader
    │               │
    │               └── returns ──▶ List<Scenario>
    │
    ├── creates ──▶ ScenarioExecutor
    │                   │
    │                   ├── uses ──▶ SpecRegistry
    │                   ├── uses ──▶ Configuration
    │                   ├── uses ──▶ PluginRegistry
    │                   └── uses ──▶ StepRegistry
    │
    └── reports ──▶ JUnit Platform
```

### Plugin Hierarchy

```
BerryCrushPlugin (interface)
    │
    ├── ReportPlugin (abstract)
    │       │
    │       ├── TextReportPlugin
    │       ├── JsonReportPlugin
    │       ├── XmlReportPlugin
    │       └── JunitReportPlugin
    │
    └── Custom plugins...
```

### Bindings Provider Chain

```
ServiceLoader<BindingsProvider>
    │
    ├── SpringBindingsProvider (priority: 100)
    │       │
    │       └── If @BerryCrushContextConfiguration present
    │
    └── DefaultBindingsProvider (priority: 0)
            │
            └── Reflection-based instantiation
```

## Key Extension Points

### 1. Custom Plugins

Implement `BerryCrushPlugin`:

```kotlin
class MyPlugin : BerryCrushPlugin {
    override val name = "my-plugin"
    override val priority = 50
    
    override fun onScenarioStart(context: ScenarioContext) {
        // Custom logic
    }
}
```

### 2. Custom Step Definitions

Use `@Step` annotation:

```kotlin
class MySteps {
    @Step("I have {int} items")
    fun setItemCount(count: Int) {
        // Custom logic
    }
}
```

### 3. Custom Bindings Provider

Implement `BindingsProvider` SPI:

```kotlin
class MyBindingsProvider : BindingsProvider {
    override fun supports(testClass: Class<*>) = true
    override fun priority() = 50
    override fun createBindings(testClass, bindingsClass) = ...
}
```

### 4. Custom HTTP Logger

Implement `HttpLogger`:

```kotlin
class MyLogger : HttpLogger {
    override fun logRequest(method, url, headers, body) {
        // Custom logging
    }
}
```
