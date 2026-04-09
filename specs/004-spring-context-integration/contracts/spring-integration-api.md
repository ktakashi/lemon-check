# Contract: Spring Context Integration API

**Module**: lemon-check/spring  
**Package**: io.github.ktakashi.lemoncheck.spring

## Purpose

Provides Spring TestContext integration for lemon-check scenarios, enabling dependency injection in bindings classes.

## Public API

### @LemonCheckContextConfiguration

Entry point annotation for Spring context integration.

```kotlin
package io.github.ktakashi.lemoncheck.spring

import kotlin.reflect.KClass

/**
 * Enables Spring TestContext integration for lemon-check scenarios.
 * 
 * When present on a test class alongside @SpringBootTest, the bindings
 * class is obtained from Spring's ApplicationContext instead of direct
 * instantiation, enabling dependency injection.
 * 
 * Usage:
 * ```java
 * @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
 * @LemonCheckContextConfiguration
 * @LemonCheckScenarios(locations = "scenarios/*.scenario")
 * @LemonCheckConfiguration(bindings = MyBindings.class, openApiSpec = "api.yaml")
 * public class MyApiTest {
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class LemonCheckContextConfiguration
```

## Usage Patterns

### Basic Spring Integration

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@LemonCheckContextConfiguration
@IncludeEngines("lemoncheck")
@LemonCheckScenarios(locations = "scenarios/*.scenario")
@LemonCheckConfiguration(bindings = MyBindings.class, openApiSpec = "api.yaml")
public class MyApiTest {
}

@Component
public class MyBindings implements LemonCheckBindings {
    
    @LocalServerPort
    private int port;
    
    @Override
    public Map<String, Object> getBindings() {
        return Map.of("baseUrl", "http://localhost:" + port);
    }
}
```

### With Autowired Dependencies

```java
@Component
public class MyBindings implements LemonCheckBindings {
    
    private final SomeService service;
    
    @Autowired
    public MyBindings(SomeService service) {
        this.service = service;
    }
    
    @Override
    public Map<String, Object> getBindings() {
        return Map.of("token", service.getAuthToken());
    }
}
```

## Requirements

| Requirement | Description |
|-------------|-------------|
| @SpringBootTest required | Test class MUST have @SpringBootTest annotation |
| Bindings as Spring component | Bindings class MUST be discoverable by Spring (via @Component or @Bean) |
| Classpath dependency | lemon-check/spring module MUST be on test classpath |

## Error Messages

| Condition | Error Message |
|-----------|---------------|
| Missing @SpringBootTest | "Test class [ClassName] has @LemonCheckContextConfiguration but is missing @SpringBootTest" |
| Bindings not a Spring bean | "Bindings class [ClassName] is not registered as a Spring bean. Add @Component or define a @Bean method." |
| Context initialization fails | "Failed to start Spring ApplicationContext for test class [ClassName]: [cause]" |
