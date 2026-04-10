# AGENTS.md - LemonCheck Development Best Practices

This document provides guidance for AI agents and developers working on the LemonCheck codebase.

## Project Overview

LemonCheck is an OpenAPI-driven BDD-style API testing library for Kotlin and Java. It consists of three main modules:
- `lemon-check/core` - Standalone execution engine
- `lemon-check/junit` - JUnit 5 TestEngine integration
- `lemon-check/spring` - Spring Boot integration

## Technology Stack

| Technology | Version | Notes |
|------------|---------|-------|
| Kotlin | 2.3.20 | Primary language |
| Java | 21 | Target JVM |
| JUnit Platform | 6.0.3 | Test engine integration |
| Spring Boot | 4.0.5 | Optional integration |
| Gradle | 8.x | Build system (Kotlin DSL) |
| ktlint | 14.0.1 | Code formatting |

## Kotlin Best Practices

### 1. Immutability

Prefer immutable data structures:

```kotlin
// GOOD: Use val for read-only properties
val scenarios = listOf(scenario1, scenario2)

// AVOID: Mutable when not needed
var scenarios = mutableListOf<Scenario>() // Only if mutation is required
```

### 2. Data Classes

Use data classes for domain models:

```kotlin
// GOOD: Data class for value objects
data class Scenario(
    val name: String,
    val steps: List<Step>,
    val tags: Set<String> = emptySet(),
)

// Note: Trailing comma for better diffs
```

### 3. Null Safety

Leverage Kotlin's null safety:

```kotlin
// GOOD: Explicit nullability
fun findStep(name: String): Step? {
    return steps.find { it.name == name }
}

// GOOD: Safe calls and elvis operator
val stepName = step?.name ?: "Unknown"

// GOOD: Early return for null checks
fun process(step: Step?) {
    val s = step ?: return
    // Process non-null step
}
```

### 4. Extension Functions

Use extension functions for utility operations:

```kotlin
// GOOD: Extension for common operations
fun Scenario.hasTag(tag: String): Boolean = tags.contains(tag)

fun List<Step>.filterByType(type: StepType): List<Step> =
    filter { it.type == type }
```

### 5. Scope Functions

Use appropriate scope functions:

```kotlin
// let - Transform nullable to non-null
val result = maybeValue?.let { transform(it) }

// also - Side effects while keeping subject
return Scenario(name, steps).also {
    logger.info("Created scenario: ${it.name}")
}

// apply - Configure an object
Configuration().apply {
    baseUrl = "http://localhost"
    timeout(30)
}

// run - Execute with receiver
spec.run {
    paths.forEach { validatePath(it) }
}

// with - Work with non-null receiver
with(scenario) {
    execute(steps)
    generateReport(name)
}
```

### 6. Sealed Classes/Interfaces

Use sealed types for restricted hierarchies:

```kotlin
// GOOD: Sealed for exhaustive when
sealed interface StepResult {
    data class Success(val value: Any?) : StepResult
    data class Failure(val error: Throwable) : StepResult
    data object Skipped : StepResult
}

// Exhaustive when (no else needed)
when (result) {
    is StepResult.Success -> handleSuccess(result.value)
    is StepResult.Failure -> handleFailure(result.error)
    is StepResult.Skipped -> handleSkipped()
}
```

### 7. DSL Design

For DSL builders, use `@DslMarker`:

```kotlin
@DslMarker
annotation class LemonCheckDsl

@LemonCheckDsl
class ScenarioScope {
    // Prevents scope leakage
}
```

### 8. Coroutines (If Used)

Follow structured concurrency:

```kotlin
// GOOD: Structured concurrency
suspend fun executeScenarios(scenarios: List<Scenario>) = coroutineScope {
    scenarios.map { scenario ->
        async { execute(scenario) }
    }.awaitAll()
}
```

### 9. Error Handling

Use specific exception types:

```kotlin
// GOOD: Custom exceptions
class ConfigurationException(
    message: String,
    cause: Throwable? = null
) : LemonCheckException(message, cause)

// GOOD: Descriptive error messages
throw ConfigurationException(
    "Operation '$operationId' not found in OpenAPI spec '$specPath'. " +
    "Available operations: ${availableOps.joinToString()}"
)
```

### 10. Documentation

Add KDoc for public APIs:

```kotlin
/**
 * Executes a scenario against the configured API.
 *
 * @param scenario The scenario to execute
 * @param sharedContext Optional shared context for cross-scenario variables
 * @return Execution result with step details
 * @throws ConfigurationException if OpenAPI spec is not loaded
 */
fun execute(
    scenario: Scenario,
    sharedContext: ExecutionContext? = null
): ScenarioResult
```

### 11. Functional Programming Style

Prefer functional programming patterns for clean, maintainable code:

```kotlin
// GOOD: Use runCatching with fold for error handling
fun execute(scenario: Scenario): ScenarioResult =
    runCatching { executeInternal(scenario) }
        .fold(
            onSuccess = { it },
            onFailure = { ScenarioResult.failed(it.message ?: "Unknown error") }
        )

// GOOD: Use getOrElse for fallback values
val config = loadConfig().getOrElse { defaultConfig() }

// GOOD: Chain operations with map/filter/forEach
scenarios
    .filter { it.isEnabled }
    .map { executeScenario(it) }
    .forEach { reportResult(it) }

// GOOD: Avoid mutable state - prefer immutable transformations
val results = scenarios.map { execute(it) }  // Not: results.add(execute(it))

// GOOD: Use small focused functions with single responsibility
private fun buildContext() = ExecutionContext()
private fun executeSteps(steps: List<Step>) = steps.map { executeStep(it) }
private fun aggregateResults(results: List<StepResult>) = ScenarioResult(results)

// GOOD: Extract context objects as immutable data classes
private data class FileExecutionContext(
    val executor: ScenarioExecutor,
    val sharedContext: ExecutionContext?,
    val scenarioPath: String,
)

// GOOD: Use onSuccess/onFailure for side effects
runCatching { cleanup() }
    .onFailure { logger.warn("Cleanup failed: ${it.message}") }
```

**Key principles:**
- Prefer `runCatching`/`fold` over try-catch where appropriate
- Use immutable data structures and transformations
- Keep functions small and focused (< 30 lines ideal)
- Avoid side effects in pure functions
- Use data classes for context/state objects

## ktlint Usage

### Check Code Formatting

```bash
# Check all modules
./gradlew ktlintCheck

# Check specific module
./gradlew :lemon-check:core:ktlintCheck
```

### Auto-Format Code

```bash
# Format all modules
./gradlew ktlintFormat

# Format specific module
./gradlew :lemon-check:core:ktlintFormat
```

### Before Every Commit

Always run ktlintFormat before committing:

```bash
./gradlew ktlintFormat && git add -A && git commit -m "your message"
```

### Common ktlint Rules

1. **No wildcard imports**
   ```kotlin
   // BAD
   import io.github.ktakashi.lemoncheck.model.*
   
   // GOOD
   import io.github.ktakashi.lemoncheck.model.Scenario
   import io.github.ktakashi.lemoncheck.model.Step
   ```

2. **Consistent indentation (4 spaces)**
   ```kotlin
   // GOOD
   fun example() {
       val x = 1
       if (x > 0) {
           println(x)
       }
   }
   ```

3. **Trailing commas in multi-line**
   ```kotlin
   // GOOD
   data class Config(
       val baseUrl: String,
       val timeout: Long,
       val headers: Map<String, String>,  // Trailing comma
   )
   ```

4. **No blank lines at start/end of blocks**
   ```kotlin
   // BAD
   fun example() {
   
       doSomething()
   
   }
   
   // GOOD
   fun example() {
       doSomething()
   }
   ```

5. **Spacing around operators**
   ```kotlin
   // BAD
   val x=1+2
   
   // GOOD
   val x = 1 + 2
   ```

## Project Structure Conventions

### Package Organization

```
io.github.ktakashi.lemoncheck
├── config/          # Configuration classes
├── context/         # Execution context
├── dsl/             # Kotlin DSL
├── exception/       # Custom exceptions
├── executor/        # Scenario execution
├── logging/         # HTTP logging
├── model/           # Domain models
├── openapi/         # OpenAPI integration
├── plugin/          # Plugin system
├── report/          # Reporting
├── runner/          # Standalone runner
├── scenario/        # Scenario parsing
└── step/            # Step definitions
```

### File Naming

| Type | Convention | Example |
|------|------------|---------|
| Classes | PascalCase | `ScenarioExecutor.kt` |
| Interfaces | PascalCase | `LemonCheckPlugin.kt` |
| Objects | PascalCase | `Parser.kt` |
| Extensions | Subject + Extensions | `ScenarioExtensions.kt` |
| Tests | Subject + Test | `ScenarioExecutorTest.kt` |

### Test Organization

```
src/test/kotlin/
├── io.github.ktakashi.lemoncheck/
│   ├── unit/             # Unit tests (mocked dependencies)
│   ├── integration/      # Integration tests
│   └── fixture/          # Test fixtures
└── resources/
    ├── scenarios/        # Test scenario files
    └── specs/            # Test OpenAPI specs
```

## Commit Message Format

Use conventional commits:

```
type(scope): description

[optional body]

[optional footer]
```

**Types:**
- `feat` - New feature
- `fix` - Bug fix
- `refactor` - Code restructuring
- `test` - Adding/updating tests
- `docs` - Documentation
- `style` - Formatting (ktlint)
- `chore` - Maintenance

**Scopes:**
- `core` - Core module
- `junit` - JUnit module
- `spring` - Spring module
- `docs` - Documentation
- `build` - Build configuration

**Examples:**
```
feat(core): add support for parameterized scenarios

fix(junit): resolve scenario discovery race condition

style(core): apply ktlintFormat to step package

docs: update developer guide with architecture diagram
```

## Testing Best Practices

### Test Structure

```kotlin
class ScenarioExecutorTest {
    @Test
    fun `execute should pass when all steps succeed`() {
        // Given
        val scenario = createTestScenario()
        val executor = createExecutor()
        
        // When
        val result = executor.execute(scenario)
        
        // Then
        assertThat(result.status).isEqualTo(ResultStatus.PASSED)
        assertThat(result.stepResults).hasSize(3)
    }
}
```

### Test Naming

Use descriptive names with backticks:

```kotlin
@Test
fun `given invalid operationId when executing then throws ConfigurationException`()

@Test
fun `extract should save variable to context when JSONPath matches`()
```

### Test Fixtures

Create fixtures for common test data:

```kotlin
object TestFixtures {
    fun createSimpleScenario(name: String = "Test"): Scenario =
        Scenario(
            name = name,
            steps = listOf(createStep()),
        )
}
```

## Common Patterns

### Builder Pattern with DSL

```kotlin
class Configuration {
    var baseUrl: String? = null
    var timeout: Duration = Duration.ofSeconds(30)
    
    fun timeout(seconds: Long) {
        timeout = Duration.ofSeconds(seconds)
    }
}

// Usage
val config = Configuration().apply {
    baseUrl = "http://localhost"
    timeout(60)
}
```

### Service Provider Interface (SPI)

```kotlin
// Define interface
interface BindingsProvider {
    fun supports(testClass: Class<*>): Boolean
    fun priority(): Int
    fun createBindings(testClass: Class<*>): LemonCheckBindings
}

// Discover via ServiceLoader
val providers = ServiceLoader.load(BindingsProvider::class.java)
    .toList()
    .sortedByDescending { it.priority() }
```

### Result Pattern

```kotlin
sealed class ParseResult {
    data class Success(val scenario: Scenario) : ParseResult()
    data class Error(val message: String, val line: Int) : ParseResult()
}

// Usage
when (val result = parser.parse(source)) {
    is ParseResult.Success -> execute(result.scenario)
    is ParseResult.Error -> reportError(result.message, result.line)
}
```

## Debugging Tips

1. **Enable HTTP logging:**
   ```kotlin
   config.logRequests = true
   config.logResponses = true
   ```

2. **Verbose test output:**
   ```bash
   ./gradlew test --info
   ```

3. **Debug single test:**
   ```bash
   ./gradlew test --tests "*.MyTest" --debug-jvm
   ```

4. **Parser debugging:**
   ```kotlin
   val result = Parser.parse(source)
   result.errors.forEach { println(it) }
   ```

## Performance Considerations

1. **Lazy initialization:**
   ```kotlin
   private val providers: List<BindingsProvider> by lazy {
       ServiceLoader.load(BindingsProvider::class.java).toList()
   }
   ```

2. **Avoid repeated spec parsing:**
   - Use `SpecRegistry` to cache parsed specs
   
3. **Connection pooling:**
   - Reuse `HttpClient` instances

## Security Best Practices

1. **Mask sensitive headers in logs:**
   ```kotlin
   val sensitiveHeaders = setOf("Authorization", "X-Api-Key", "Cookie")
   ```

2. **No credentials in code:**
   - Use environment variables or secure vaults
   
3. **Dependency scanning:**
   ```bash
   ./gradlew dependencyCheckAnalyze
   ```
