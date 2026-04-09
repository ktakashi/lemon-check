# Feature Specification: Spring Context Integration

**Feature Branch**: `004-spring-context-integration`  
**Created**: 2026-04-08  
**Status**: Draft  
**Input**: User description: "JUnit engine sample test is not working due to the lack of Spring context integration. We want to enable it by adding Spring context integration support to `lemon-check/spring` module. `LemonCheckContextConfiguration` should act as a Spring test context initialisation entry point."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run Scenario Tests with Spring Context Injection (Priority: P1)

As a Spring Boot developer using lemon-check, I want to run my BDD scenario tests with full Spring context integration, so that my bindings classes can receive Spring-injected values like `@LocalServerPort` and `@Autowired` beans.

**Why this priority**: This is the core problem being solved. Currently, the `PetstoreScenarioTest` is disabled because the LemonCheck JUnit engine instantiates bindings classes independently, bypassing Spring's dependency injection. Without this, Spring Boot users cannot use lemon-check effectively.

**Independent Test**: Can be fully tested by enabling the currently disabled `PetstoreScenarioTest`, running it via JUnit Platform, and verifying that `@LocalServerPort` is properly injected into the bindings class and scenarios execute against the running Spring Boot application.

**Acceptance Scenarios**:

1. **Given** a test class annotated with `@SpringBootTest` and `@LemonCheckContextConfiguration`, **When** the test runs, **Then** the Spring application context is started before scenarios execute
2. **Given** a bindings class with `@LocalServerPort` field, **When** scenarios execute, **Then** the port value is injected correctly from Spring's test server
3. **Given** a bindings class with `@Autowired` dependencies, **When** scenarios execute, **Then** Spring beans are injected into the bindings instance
4. **Given** `@LemonCheckContextConfiguration` on the test class, **When** JUnit discovers tests, **Then** Spring context lifecycle is integrated with scenario execution

---

### User Story 2 - Configure Spring Integration via LemonCheckContextConfiguration (Priority: P2)

As a developer, I want to use `@LemonCheckContextConfiguration` to define my Spring-aware bindings configuration, so that I have a clear entry point for Spring test context initialization in lemon-check scenarios.

**Why this priority**: Configuration flexibility is essential for different test setups, but basic Spring injection (P1) must work first.

**Independent Test**: Can be tested by creating a test class with `@LemonCheckContextConfiguration` specifying different Spring-managed bindings, running scenarios, and verifying the Spring-injected configuration is applied correctly.

**Acceptance Scenarios**:

1. **Given** a test class with `@LemonCheckContextConfiguration(bindings = SpringBindings.class)`, **When** scenarios execute, **Then** the bindings class is obtained from Spring's application context
2. **Given** multiple test classes with different `@LemonCheckContextConfiguration`, **When** tests run, **Then** each test class uses its own Spring context and bindings configuration
3. **Given** a test class with `@LemonCheckContextConfiguration` but missing `@SpringBootTest`, **When** tests run, **Then** a clear error message indicates Spring context is required

---

### User Story 3 - Enable Disabled Petstore Scenario Test (Priority: P3)

As a lemon-check maintainer, I want the `PetstoreScenarioTest` to work with Spring integration, so that the sample demonstrates proper Spring Boot + lemon-check integration for users.

**Why this priority**: This validates the implementation and serves as documentation, but depends on P1 and P2 being complete.

**Independent Test**: Can be tested by removing the `@Disabled` annotation from `PetstoreScenarioTest`, running `./gradlew :samples:petstore:test`, and verifying all scenario tests pass.

**Acceptance Scenarios**:

1. **Given** the updated `PetstoreScenarioTest` with `@LemonCheckContextConfiguration`, **When** I run `./gradlew :samples:petstore:test`, **Then** all scenario tests pass
2. **Given** the petstore scenarios making HTTP requests, **When** scenarios execute, **Then** requests reach the Spring Boot application on the dynamically allocated port
3. **Given** the petstore bindings class, **When** `getBindings()` is called, **Then** the `baseUrl` contains the correct port from `@LocalServerPort`

---

### Edge Cases

- What happens when `@LemonCheckContextConfiguration` is used without `@SpringBootTest`? The system must report a clear error message indicating Spring context is required.
- What happens when the Spring context fails to start? The error must be reported clearly before scenario execution begins.
- What happens when a Spring bean cannot be injected into the bindings class? A clear dependency injection error must be reported.
- How does the system handle Spring context caching across multiple test classes? Standard Spring TestContext caching behavior should apply.
- What happens when scenarios are run in parallel with Spring integration? Spring context should be shared per JVM as per standard Spring Test behavior.

## Requirements *(mandatory)*

### Functional Requirements

**Spring Integration Module (lemon-check/spring)**:

- **FR-001**: System MUST provide a new Gradle module `lemon-check/spring` for Spring context integration
- **FR-002**: System MUST provide `@LemonCheckContextConfiguration` annotation as the Spring test context initialization entry point
- **FR-003**: System MUST integrate with Spring TestContext framework to manage application context lifecycle
- **FR-004**: System MUST obtain bindings instances from Spring's application context instead of direct instantiation
- **FR-005**: System MUST support `@LocalServerPort` injection in bindings classes when used with `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- **FR-006**: System MUST support `@Autowired` dependency injection in bindings classes
- **FR-007**: System MUST start Spring application context before scenario discovery/execution
- **FR-008**: System MUST clean up Spring context after test completion following standard Spring Test semantics
- **FR-009**: System MUST report clear error messages when Spring context initialization fails
- **FR-010**: System MUST work with existing `@LemonCheckScenarios` and `@LemonCheckConfiguration` annotations

**Sample Petstore Update (samples/petstore)**:

- **FR-011**: System MUST update `PetstoreScenarioTest` to use `@LemonCheckContextConfiguration`
- **FR-012**: System MUST remove `@Disabled` annotation from `PetstoreScenarioTest` once integration works
- **FR-013**: System MUST update `PetstoreBindings` to be a Spring-managed component with proper injection

### Key Entities

- **LemonCheckContextConfiguration**: Annotation that marks a test class for Spring context integration with lemon-check. Acts as the entry point for Spring test context initialization. Key attributes: `bindings` (the Spring-managed bindings class).
- **SpringContextAdapter**: Internal component that bridges between LemonCheck JUnit engine and Spring TestContext framework. Responsible for context lifecycle management and bean retrieval.
- **Spring-managed LemonCheckBindings**: A bindings implementation that is a Spring `@Component` and can receive dependency injection.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `PetstoreScenarioTest` runs successfully without the `@Disabled` annotation
- **SC-002**: All existing lemon-check tests continue to pass (no regression)
- **SC-003**: Developers can inject `@LocalServerPort` into bindings classes and receive the correct port value
- **SC-004**: Developers can use `@Autowired` beans in bindings classes
- **SC-005**: Spring context is properly cached and reused per JUnit Platform's TestContext caching rules
- **SC-006**: Clear error messages are displayed when Spring configuration is invalid or missing
- **SC-007**: Documentation in the sample test demonstrates proper usage of Spring integration

## Assumptions

- Spring Boot 3.x is the target Spring version (consistent with existing petstore sample)
- JUnit Platform 5.10.x or higher is used (compatible with existing junit module)
- The `lemon-check/junit` module will be a dependency of `lemon-check/spring` module
- Standard Spring TestContext framework APIs are sufficient for this integration
- Kotlin 2.x is used for the new module implementation (consistent with existing modules)
- No changes to the existing `@LemonCheckConfiguration` semantics are required; `@LemonCheckContextConfiguration` provides Spring-specific augmentation
