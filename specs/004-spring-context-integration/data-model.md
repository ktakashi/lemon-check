# Data Model: Spring Context Integration

**Feature**: 004-spring-context-integration  
**Date**: 2026-04-08

## Entities

### LemonCheckContextConfiguration (Annotation)

Annotation that marks a test class for Spring context integration with lemon-check scenarios.

| Attribute | Type | Required | Description |
|-----------|------|----------|-------------|
| bindings | Class | No | The Spring-managed bindings class (defaults to class from @LemonCheckConfiguration) |

**Relationships**:
- Applied to test classes alongside `@SpringBootTest`
- Works with existing `@LemonCheckScenarios` and `@LemonCheckConfiguration` annotations

**Constraints**:
- Test class MUST have `@SpringBootTest` present
- Bindings class MUST be a Spring component (or creatable by Spring)

---

### BindingsProvider (Interface/SPI)

Service Provider Interface for creating LemonCheckBindings instances.

| Method | Return Type | Description |
|--------|-------------|-------------|
| supports(Class<?> testClass) | boolean | Returns true if this provider can handle the test class |
| priority() | int | Priority when multiple providers match (higher = preferred) |
| createBindings(Class<?> testClass, Class<? extends LemonCheckBindings> bindingsClass) | LemonCheckBindings | Creates the bindings instance |
| initialize(Class<?> testClass) | void | Called before test execution to initialize context |
| cleanup(Class<?> testClass) | void | Called after test execution to cleanup context |

**Relationships**:
- Discovered via Java ServiceLoader
- Implemented by `SpringBindingsProvider` in spring module
- Used by `LemonCheckTestEngine` during execution

---

### SpringBindingsProvider (SPI Implementation)

Implementation of BindingsProvider that integrates with Spring TestContext.

| Field | Type | Description |
|-------|------|-------------|
| testContextManagers | Map | Cache of TestContextManager per test class |
| testInstances | Map | Cache of test instances per test class |

**State Transitions**:
1. **Uninitialized** → `supports()` returns true for @LemonCheckContextConfiguration classes
2. **Initialized** → `initialize()` creates TestContextManager, starts Spring context
3. **Ready** → `createBindings()` retrieves bean from ApplicationContext
4. **Cleaned** → `cleanup()` releases context following Spring semantics

---

### SpringContextAdapter (Internal)

Bridge between LemonCheck engine and Spring TestContext framework.

| Method | Description |
|--------|-------------|
| initializeContext(Class<?> testClass) | Creates TestContextManager and starts context |
| getBean(Class<T> beanClass) | Retrieves bean from Spring ApplicationContext |
| getApplicationContext() | Returns the Spring ApplicationContext |
| cleanup() | Releases Spring context |

**Responsibilities**:
- Manages TestContextManager lifecycle
- Handles test instance creation for Spring injection
- Provides access to ApplicationContext for bean retrieval

---

## Entity Relationships

```
┌──────────────────────────────┐
│      Test Class              │
│  @SpringBootTest             │
│  @LemonCheckContextConfiguration
│  @LemonCheckScenarios        │
│  @LemonCheckConfiguration    │
└──────────────┬───────────────┘
               │ annotated with
               ▼
┌──────────────────────────────┐
│  SpringBindingsProvider      │──────────────┐
│  (discovers annotation)      │              │
└──────────────┬───────────────┘              │
               │ creates                       │
               ▼                               │
┌──────────────────────────────┐              │
│  SpringContextAdapter        │              │
│  (manages TestContextManager)│              │
└──────────────┬───────────────┘              │
               │ retrieves bean from          │
               ▼                               │
┌──────────────────────────────┐              │
│  ApplicationContext          │              │ returns
│  (Spring container)          │              │
└──────────────┬───────────────┘              │
               │ contains                      │
               ▼                               │
┌──────────────────────────────┐              │
│  Bindings Bean               │◄─────────────┘
│  @Component                  │
│  implements LemonCheckBindings│
│  @LocalServerPort injected   │
└──────────────────────────────┘
```

## Validation Rules

1. **@LemonCheckContextConfiguration present** → Test class MUST also have @SpringBootTest
2. **Bindings class** → MUST be discoverable as Spring bean (component scan or explicit config)
3. **TestContextManager** → MUST be initialized before scenario execution
4. **ApplicationContext** → MUST be available before createBindings() is called
