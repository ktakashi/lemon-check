---
applyTo: "*.kt"
---

# Kotlin Best Practices for BerryCrush

## Technology Stack
| Technology | Version | Notes |
|------------|---------|-------|
| Kotlin | 2.3.20 | Primary language |
| Java | 21 | Target JVM |
| JUnit Platform | 6.0.3 | Test engine integration |
| Spring Boot | 4.0.5 | Optional integration |
| ktlint | 14.0.1 | Code formatting |

## 1. Immutability

Prefer immutable data structures:

```kotlin
// GOOD: Use val for read-only properties
val scenarios = listOf(scenario1, scenario2)

// AVOID: Mutable when not needed
var scenarios = mutableListOf<Scenario>() // Only if mutation is required
```

## 2. Data Classes

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

## 3. Null Safety

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

## 4. Extension Functions

Use extension functions for utility operations:

```kotlin
// GOOD: Extension for common operations
fun Scenario.hasTag(tag: String): Boolean = tags.contains(tag)

fun List<Step>.filterByType(type: StepType): List<Step> =
    filter { it.type == type }
```

## 5. Scope Functions

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

## 6. Sealed Classes/Interfaces

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

## 7. DSL Design

For DSL builders, use `@DslMarker`:

```kotlin
@DslMarker
annotation class BerryCrushDsl

@BerryCrushDsl
class ScenarioScope {
    // Prevents scope leakage
}
```

## 8. Coroutines (If Used)

Follow structured concurrency:

```kotlin
// GOOD: Structured concurrency
suspend fun executeScenarios(scenarios: List<Scenario>) = coroutineScope {
    scenarios.map { scenario ->
        async { execute(scenario) }
    }.awaitAll()
}
```

## 9. Error Handling

Use specific exception types:

```kotlin
// GOOD: Custom exceptions
class ConfigurationException(
    message: String,
    cause: Throwable? = null
) : BerryCrushException(message, cause)

// GOOD: Descriptive error messages
throw ConfigurationException(
    "Operation '$operationId' not found in OpenAPI spec '$specPath'. " +
    "Available operations: ${availableOps.joinToString()}"
)
```

## 10. Documentation

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

## 11. Functional Programming Style

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
./gradlew :berrycrush:core:ktlintCheck
```

### Auto-Format Code
```bash
# Format all modules
./gradlew ktlintFormat

# Format specific module
./gradlew :berrycrush:core:ktlintFormat
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
   import org.berrycrush.model.*
   
   // GOOD
   import org.berrycrush.model.Scenario
   import org.berrycrush.model.Step
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

## 16. Imports and Aliases

Avoid using fully qualified names in code. Use imports, and when there are name conflicts, use import aliases:

```kotlin
// BAD: Fully qualified names in code
private fun transform(branch: org.berrycrush.scenario.ConditionBranch): org.berrycrush.model.ConditionBranch {
    return org.berrycrush.model.ConditionBranch(...)
}

// GOOD: Import with aliases for conflicting names
import org.berrycrush.model.ConditionBranch as ModelConditionBranch
import org.berrycrush.scenario.ConditionBranch as AstConditionBranch

private fun transform(branch: AstConditionBranch): ModelConditionBranch {
    return ModelConditionBranch(...)
}

// GOOD: When only one is used frequently, import the other with alias
import org.berrycrush.model.ConditionBranch as ModelConditionBranch

// AstConditionBranch is in current package, so no import needed
private fun transform(branch: ConditionBranch): ModelConditionBranch {
    return ModelConditionBranch(...)
}
```

**Naming Convention for Aliases:**
- Use `Model` prefix for runtime model classes (e.g., `ModelConditionBranch`)
- Use `Ast` prefix for AST/parsing classes (e.g., `AstConditionBranch`)
- Be consistent across the codebase

## 17. Nullable Returns and When Expressions

### Prefer `?.let {}` over Explicit Null Checks

When a function returns nullable and you need to process the result, prefer `?.let` over assigning to a variable and returning early:

```kotlin
// BAD: Explicit null check with early return
fun process(): Result? {
    val value = getValue() ?: return null
    return Result(value.transform())
}

// GOOD: Use ?.let for cleaner flow
fun process(): Result? =
    getValue()?.let { value ->
        Result(value.transform())
    }
```

### Use `return when {}` with `else -> null`

When a function returns different values based on conditions, use `return when` with explicit `else -> null` instead of returning inside each case:

```kotlin
// BAD: Return inside each case with return null at end
fun parse(keyword: String): Node? {
    when (keyword) {
        "status" -> {
            return StatusNode()
        }
        "header" -> {
            return HeaderNode()
        }
    }
    return null
}

// GOOD: Use expression form with else -> null
fun parse(keyword: String): Node? = when (keyword) {
    "status" -> StatusNode()
    "header" -> HeaderNode()
    else -> null
}

// GOOD: Complex processing still works
fun parse(keyword: String): Node? = when (keyword) {
    "status" -> {
        advance()
        skipWhitespace()
        parseStatusValue()?.let { StatusNode(it) }
    }
    "header" -> {
        advance()
        HeaderNode(parseHeaderName())
    }
    else -> null
}
```

### Combine Multiple Patterns

```kotlin
// GOOD: Combine ?.let and when expression
fun parseCondition(keyword: String): Condition? = when {
    keyword == "status" -> parseStatusValue()?.let { StatusCondition(it) }
    keyword == "header" -> HeaderCondition(parseHeaderName())
    keyword.startsWith("$") -> parseJsonPath()?.let { JsonPathCondition(it) }
    else -> null
}
```

**Key principles:**
- Use expression-body functions (`= when {...}`) when the function is primarily a dispatch
- Use `?.let` to transform nullable intermediate results
- Avoid mutable `var` for tracking state that can be derived from control flow
