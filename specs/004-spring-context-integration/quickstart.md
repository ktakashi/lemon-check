# Quickstart: Spring Context Integration

**Feature**: 004-spring-context-integration  
**Module**: lemon-check/spring

## Goal

Enable `@LocalServerPort` and `@Autowired` injection in lemon-check bindings classes when running scenario tests with Spring Boot.

## Prerequisites

- Spring Boot 3.x application
- JUnit 5 test infrastructure
- Existing lemon-check scenario files (`.scenario`)

## Step 1: Add Dependency

Add the lemon-check/spring module to your test dependencies:

```kotlin
// build.gradle.kts
dependencies {
    testImplementation(project(":lemon-check:spring"))
    // or when published:
    // testImplementation("io.github.ktakashi:lemon-check-spring:x.y.z")
}
```

## Step 2: Create Spring-Managed Bindings

Make your bindings class a Spring component with `@Lazy` for proper `@LocalServerPort` timing:

```java
@Component
@Lazy  // Required for @LocalServerPort - port is set after server starts
public class MyBindings implements LemonCheckBindings {
    
    @LocalServerPort
    private int port;
    
    @Override
    public Map<String, Object> getBindings() {
        return Map.of(
            "baseUrl", "http://localhost:" + port + "/api"
        );
    }
    
    @Override
    public String getOpenApiSpec() {
        return "my-api.yaml";
    }
}
```

## Step 3: Configure Test Class

Add `@LemonCheckContextConfiguration` alongside your existing annotations:

```java
@Suite
@IncludeEngines("lemoncheck")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@LemonCheckContextConfiguration  // <-- Add this
@LemonCheckScenarios(locations = "scenarios/*.scenario")
@LemonCheckConfiguration(bindings = MyBindings.class, openApiSpec = "my-api.yaml")
public class MyApiScenarioTest {
}
```

## Step 4: Run Tests

```bash
./gradlew test
```

The Spring application context starts before scenarios execute, and `@LocalServerPort` is injected with the allocated port.

## Complete Example

```java
// src/test/java/com/example/PetstoreScenarioTest.java
@Suite
@IncludeEngines("lemoncheck")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@LemonCheckContextConfiguration
@LemonCheckScenarios(locations = "scenarios/*.scenario")
@LemonCheckConfiguration(bindings = PetstoreBindings.class, openApiSpec = "petstore.yaml")
public class PetstoreScenarioTest {
}

// src/test/java/com/example/PetstoreBindings.java
@Component
@Lazy  // Required for @LocalServerPort timing
public class PetstoreBindings implements LemonCheckBindings {
    
    @LocalServerPort
    private int port;
    
    @Override
    public Map<String, Object> getBindings() {
        return Map.of("baseUrl", "http://localhost:" + port + "/api/v1");
    }
    
    @Override
    public String getOpenApiSpec() {
        return "petstore.yaml";
    }
}
```

## Troubleshooting

### "Missing @SpringBootTest" Error

Ensure your test class has both annotations:
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@LemonCheckContextConfiguration
```

### "Bindings not a Spring bean" Error

Add `@Component` to your bindings class, or ensure it's in a package scanned by Spring Boot.

### Port is 0 or 8080

Verify `webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT` is set. Without this, Spring doesn't start an embedded server.

## Migration from @Disabled Test

If you have a test marked `@Disabled("Requires Spring context integration")`:

1. Remove `@Disabled` annotation
2. Add `@LemonCheckContextConfiguration`
3. Ensure bindings class has `@Component`
4. Run tests

## Test Isolation Notes

When using Spring context integration, be aware of these considerations:

### Shared Database State
- All scenarios within a `@SpringBootTest` class share the same Spring context
- Database operations in one scenario affect subsequent scenarios
- Order scenarios using filename prefixes (e.g., `01-read-tests.scenario`, `99-delete-tests.scenario`)

### H2 Sequence Reset
- When using `data.sql` with explicit IDs, reset auto-increment to avoid conflicts:
  ```sql
  ALTER TABLE pets ALTER COLUMN id RESTART WITH 100;
  ```

### @Lazy for @LocalServerPort
- The `@LocalServerPort` property is only available after the server starts
- Add `@Lazy` annotation to bindings classes that use `@LocalServerPort`

## Known Limitations

1. **Test ordering**: Scenarios execute alphabetically by filename. Use numeric prefixes to control order.
2. **Context caching**: Spring context is cached and shared across scenarios in the same test class.
3. **Transaction rollback**: Scenarios do not automatically rollback database changes between tests.
