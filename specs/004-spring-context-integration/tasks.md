# Tasks: Spring Context Integration

**Input**: Design documents from `/specs/004-spring-context-integration/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create new lemon-check/spring module with basic structure

- [X] T001 Create module directory structure at lemon-check/spring/
- [X] T002 Create build.gradle.kts at lemon-check/spring/build.gradle.kts with Spring Boot Test dependencies
- [X] T003 Register spring module in settings.gradle.kts
- [X] T004 [P] Add spring-boot-starter-test dependency to samples/petstore/build.gradle.kts

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Add BindingsProvider SPI to lemon-check/junit module - MUST complete before user stories

**⚠️ CRITICAL**: No Spring integration can begin until SPI extension point is in place

- [X] T005 Create BindingsProvider interface at lemon-check/junit/src/main/kotlin/io/github/ktakashi/lemoncheck/junit/spi/BindingsProvider.kt
- [X] T006 Add ServiceLoader discovery for BindingsProvider in LemonCheckTestEngine at lemon-check/junit/src/main/kotlin/io/github/ktakashi/lemoncheck/junit/engine/LemonCheckTestEngine.kt
- [X] T007 Extract binding creation to use BindingsProvider SPI with fallback to reflection in lemon-check/junit/src/main/kotlin/io/github/ktakashi/lemoncheck/junit/engine/LemonCheckTestEngine.kt
- [X] T008 Add lifecycle hooks (initialize/cleanup) calls in LemonCheckTestEngine execute method
- [X] T009 [P] Add unit test for BindingsProvider SPI fallback behavior in lemon-check/junit/src/test/kotlin/io/github/ktakashi/lemoncheck/junit/spi/BindingsProviderTest.kt

**Checkpoint**: SPI extension point ready - Spring integration can now begin

---

## Phase 3: User Story 1 - Spring Context Injection (Priority: P1) 🎯 MVP

**Goal**: Enable bindings classes to receive `@LocalServerPort` and `@Autowired` injection

**Independent Test**: PetstoreBindings receives dynamically allocated port value from Spring Boot test server

### Implementation for User Story 1

- [X] T010 [P] [US1] Create @LemonCheckContextConfiguration annotation at lemon-check/spring/src/main/kotlin/io/github/ktakashi/lemoncheck/spring/LemonCheckContextConfiguration.kt
- [X] T011 [P] [US1] Create SpringContextAdapter class at lemon-check/spring/src/main/kotlin/io/github/ktakashi/lemoncheck/spring/SpringContextAdapter.kt
- [X] T012 [US1] Implement TestContextManager lifecycle in SpringContextAdapter (initializeContext, getBean, getApplicationContext, cleanup)
- [X] T013 [US1] Create SpringBindingsProvider implementing BindingsProvider SPI at lemon-check/spring/src/main/kotlin/io/github/ktakashi/lemoncheck/spring/SpringBindingsProvider.kt
- [X] T014 [US1] Implement supports() to detect @LemonCheckContextConfiguration annotation in SpringBindingsProvider
- [X] T015 [US1] Implement initialize() to start Spring context via SpringContextAdapter
- [X] T016 [US1] Implement createBindings() to retrieve bean from ApplicationContext
- [X] T017 [US1] Implement cleanup() to release Spring context following Spring Test semantics
- [X] T018 [US1] Create ServiceLoader registration file at lemon-check/spring/src/main/resources/META-INF/services/io.github.ktakashi.lemoncheck.junit.spi.BindingsProvider
- [X] T019 [US1] Add test for SpringBindingsProvider lifecycle at lemon-check/spring/src/test/kotlin/io/github/ktakashi/lemoncheck/spring/SpringBindingsProviderTest.kt

**Checkpoint**: Spring context injection working - @LocalServerPort can be injected into bindings classes

---

## Phase 4: User Story 2 - Configuration Error Handling (Priority: P2)

**Goal**: Clear error messages when Spring configuration is invalid or missing

**Independent Test**: Test class with @LemonCheckContextConfiguration but missing @SpringBootTest shows clear error

### Implementation for User Story 2

- [X] T020 [US2] Add validation in SpringBindingsProvider.initialize() to check for @SpringBootTest annotation
- [X] T021 [US2] Implement error handling for missing @SpringBootTest with descriptive message
- [X] T022 [US2] Implement error handling for bindings class not found as Spring bean
- [X] T023 [US2] Implement error handling for Spring context initialization failures
- [X] T024 [P] [US2] Add test for missing @SpringBootTest error message at lemon-check/spring/src/test/kotlin/io/github/ktakashi/lemoncheck/spring/SpringBindingsProviderErrorTest.kt
- [X] T025 [P] [US2] Add test for bean not found error message at lemon-check/spring/src/test/kotlin/io/github/ktakashi/lemoncheck/spring/SpringBindingsProviderErrorTest.kt

**Checkpoint**: Error handling complete - invalid configurations show clear messages

---

## Phase 5: User Story 3 - Enable Petstore Scenario Test (Priority: P3)

**Goal**: Remove @Disabled from PetstoreScenarioTest and demonstrate Spring integration

**Independent Test**: `./gradlew :samples:petstore:test` passes with scenario tests executing

### Implementation for User Story 3

- [X] T026 [US3] Add lemon-check/spring dependency to samples/petstore/build.gradle.kts
- [X] T027 [US3] Update PetstoreBindings to use @LocalServerPort injection at samples/petstore/src/test/java/io/github/ktakashi/samples/petstore/PetstoreBindings.java
- [X] T028 [US3] Add @LemonCheckContextConfiguration to PetstoreScenarioTest at samples/petstore/src/test/java/io/github/ktakashi/samples/petstore/PetstoreScenarioTest.java
- [X] T029 [US3] Remove @Disabled annotation from PetstoreScenarioTest
- [X] T030 [US3] Run samples:petstore:test to verify scenarios pass with Spring integration

**Checkpoint**: Petstore sample working - validates entire Spring integration implementation

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and cleanup

- [X] T031 [P] Add KDoc documentation to public API classes in lemon-check/spring module
- [X] T032 [P] Update PetstoreScenarioTest comments to document Spring integration usage
- [X] T033 Run quickstart.md validation - verify documentation matches implementation
- [X] T034 Run full build verification: `./gradlew build` to ensure no regressions

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup - BLOCKS all user stories
- **User Stories (Phase 3-5)**: All depend on Foundational completion
  - US1 (Phase 3): Core Spring injection - can start after Phase 2
  - US2 (Phase 4): Error handling - can start after Phase 2 (or after US1 for integration)
  - US3 (Phase 5): Petstore integration - MUST wait for US1 completion
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - Can run in parallel with US1
- **User Story 3 (P3)**: Depends on US1 completion - integrates the Spring module into petstore

### Within Each User Story

- Models/Annotations before services/providers
- Core implementation before integration
- Story tests before moving to next priority

### Parallel Opportunities

**Phase 1 (Setup)**:
```
T001 → T002 → T003 (sequential - module creation)
T004 (parallel with above - different file)
```

**Phase 2 (Foundational)**:
```
T005 (interface first)
T006 → T007 → T008 (modify LemonCheckTestEngine - sequential)
T009 (parallel - different module test file)
```

**Phase 3 (User Story 1)**:
```
T010 + T011 (parallel - different files)
T012 (depends on T011)
T013 → T014 → T015 → T016 → T017 (sequential - same class)
T018 (parallel - different file)
T019 (after T013-T017)
```

**Phase 4 (User Story 2)** - Can run in parallel with Phase 3:
```
T020 → T021 → T022 → T023 (sequential - same class)
T024 + T025 (parallel - same test file but independent tests)
```

**Phase 5 (User Story 3)** - Must wait for US1:
```
T026 → T027 → T028 → T029 → T030 (sequential - integration)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (SPI extension point)
3. Complete Phase 3: User Story 1 (Spring context injection)
4. **STOP and VALIDATE**: Test Spring injection works with a simple test case
5. Continue with US2 + US3 to enable petstore sample

### Incremental Delivery

1. Complete Setup + Foundational → SPI ready
2. Add User Story 1 → Spring injection works → Core MVP!
3. Add User Story 2 → Error handling complete → Better DX
4. Add User Story 3 → Petstore enabled → Full validation
5. Each story adds value without breaking previous stories

---

## Notes

- [P] tasks = different files, no dependencies
- [US1/US2/US3] label maps task to user story from spec.md
- Foundational phase (SPI) MUST complete before any Spring integration
- US3 validates the entire implementation by enabling real-world sample
- Commit after each task or logical group
- Run `./gradlew build` after each phase to verify no regressions
