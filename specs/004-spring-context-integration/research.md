# Research: Spring Context Integration

**Feature**: 004-spring-context-integration  
**Date**: 2026-04-08

## Research Tasks

### 1. Spring TestContext Framework Integration

**Question**: How to integrate a custom JUnit engine with Spring's TestContext framework?

**Findings**:
- Spring TestContext provides `TestContextManager` as the entry point for managing test contexts
- `TestContextManager.prepareTestInstance(testInstance)` triggers dependency injection
- `SpringExtension` (for JUnit Jupiter) shows the pattern for integration
- Key lifecycle hooks: `beforeTestClass()`, `prepareTestInstance()`, `afterTestClass()`

**Decision**: Use `TestContextManager` directly, instantiating it with the test class and calling lifecycle methods at appropriate points in `LemonCheckTestEngine.execute()`.

**Rationale**: This is the same approach used by `SpringExtension` and provides full Spring TestContext integration including context caching.

**Alternatives Considered**:
1. **Using `@ExtendWith(SpringExtension.class)`** - Rejected because our custom engine doesn't process JUnit Jupiter extensions
2. **Creating ApplicationContext manually** - Rejected because it bypasses Spring's context caching and lifecycle management

---

### 2. Bindings Instance Creation via Spring

**Question**: How to obtain Spring-managed bindings instance instead of direct reflection instantiation?

**Findings**:
- Current code: `bindingsClass.getDeclaredConstructor().newInstance()` in `LemonCheckTestEngine.createBindings()`
- Spring approach: Get bean from `ApplicationContext.getBean(bindingsClass)`
- TestContextManager exposes context via `testContextManager.testContext.applicationContext`

**Decision**: Introduce `BindingsProvider` SPI in `lemon-check/junit`. The `lemon-check/spring` module provides `SpringBindingsProvider` that retrieves beans from Spring's ApplicationContext.

**Rationale**: SPI pattern keeps `lemon-check/junit` Spring-agnostic while allowing Spring integration when `lemon-check/spring` is on classpath.

**Alternatives Considered**:
1. **Direct modification of LemonCheckTestEngine** - Rejected to avoid Spring dependency in junit module
2. **Reflection-based detection** - Rejected as fragile and harder to test

---

### 3. @LocalServerPort Injection Timing

**Question**: When is `@LocalServerPort` available and how to ensure it's injected before scenario execution?

**Findings**:
- `@LocalServerPort` is injected by Spring during `prepareTestInstance()`
- The server port is only available after `@SpringBootTest` starts the embedded server
- With `webEnvironment = RANDOM_PORT`, the port is allocated at context initialization time

**Decision**: Call `TestContextManager.beforeTestClass()` during test discovery/execution start, then `prepareTestInstance()` to inject port values before running scenarios.

**Rationale**: Following Spring's lifecycle ensures the port is available when bindings are configured.

---

### 4. ServiceLoader Discovery

**Question**: How to discover `SpringBindingsProvider` without explicit configuration?

**Findings**:
- Java `ServiceLoader` is the standard SPI mechanism
- Create `META-INF/services/io.github.ktakashi.lemoncheck.junit.spi.BindingsProvider` file
- ServiceLoader automatically discovers implementations on classpath

**Decision**: Use `ServiceLoader` in `LemonCheckTestEngine` to discover `BindingsProvider` implementations. When `lemon-check/spring` is on classpath, `SpringBindingsProvider` is automatically loaded.

**Rationale**: Standard Java SPI mechanism, no additional dependencies required.

---

### 5. Test Class Instance Management

**Question**: How to handle test class instance lifecycle with Spring?

**Findings**:
- Spring's `TestContextManager` expects a test instance for `prepareTestInstance()`
- LemonCheck bindings are separate from test class
- Test class instance only needed for annotation scanning and context initialization

**Decision**: Create test class instance, use it for Spring context initialization, then retrieve bindings bean separately from the ApplicationContext.

**Rationale**: Separates concerns - test class drives Spring context, bindings class provides scenario configuration.

---

## Technology Choices

| Choice | Selected | Rationale |
|--------|----------|-----------|
| Spring Integration API | TestContextManager | Standard Spring Test approach; context caching works |
| SPI Mechanism | Java ServiceLoader | Standard; automatic discovery; no config needed |
| Module Structure | Separate lemon-check/spring | Keeps Spring optional; clear dependency direction |
| Annotation Design | @LemonCheckContextConfiguration | Explicit entry point; mirrors @SpringBootTest pattern |

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| Context caching conflicts | Follow Spring's context caching rules; test with multiple test classes |
| ServiceLoader ordering | Define priority/precedence if multiple providers found |
| Test class must be instantiable | Document requirement; validate at discovery time |
