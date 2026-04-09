# Implementation Plan: Spring Context Integration

**Branch**: `004-spring-context-integration` | **Date**: 2026-04-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/004-spring-context-integration/spec.md`

## Summary

Add Spring TestContext integration to the lemon-check JUnit engine, enabling bindings classes to receive Spring dependency injection (`@LocalServerPort`, `@Autowired`). The core problem is that `LemonCheckTestEngine.createBindings()` directly instantiates bindings classes via reflection, bypassing Spring. The solution introduces a `lemon-check/spring` module with `@LemonCheckContextConfiguration` annotation and a `SpringContextAdapter` that intercepts binding creation and retrieves beans from Spring's ApplicationContext.

## Technical Context

**Language/Version**: Kotlin 2.3.20, Java 21  
**Primary Dependencies**: JUnit Platform 1.11.4, Spring Boot 3.4.1, spring-boot-starter-test  
**Storage**: N/A (testing infrastructure)  
**Testing**: JUnit 5, Spring Boot Test  
**Target Platform**: JVM (library)
**Project Type**: library  
**Performance Goals**: N/A (test execution time not critical)  
**Constraints**: Must not break existing non-Spring tests; must integrate with Spring TestContext caching  
**Scale/Scope**: Integration module for Spring Boot + lemon-check  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | ✅ PASS | Clean, documented Kotlin code following existing patterns |
| II. User Experience | ✅ PASS | Single annotation entry point (`@LemonCheckContextConfiguration`) |
| III. Maintainability | ✅ PASS | Separate module with clear boundaries, depends only on junit module |
| IV. Testing Standards | ✅ PASS | Validated via petstore sample test enablement |
| V. Flexibility | ✅ PASS | Optional module; existing tests unaffected |

**Gate Result**: PASS - All principles satisfied.

## Project Structure

### Documentation (this feature)

```text
specs/004-spring-context-integration/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
lemon-check/
├── core/                       # Existing - no changes
├── junit/                      # Existing - add extension point
│   └── src/main/kotlin/io/github/ktakashi/lemoncheck/junit/
│       ├── engine/
│       │   └── LemonCheckTestEngine.kt     # Add BindingsProvider SPI
│       └── spi/
│           └── BindingsProvider.kt         # NEW - SPI for custom bindings creation
└── spring/                     # NEW MODULE
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/io/github/ktakashi/lemoncheck/spring/
        │   ├── LemonCheckContextConfiguration.kt   # Entry point annotation
        │   ├── SpringBindingsProvider.kt           # SPI implementation
        │   └── SpringContextAdapter.kt             # TestContext bridge
        └── test/kotlin/
            └── io/github/ktakashi/lemoncheck/spring/
                └── SpringContextAdapterTest.kt

samples/petstore/
└── src/test/java/io/github/ktakashi/samples/petstore/
    ├── PetstoreScenarioTest.java   # UPDATE: Remove @Disabled, add @LemonCheckContextConfiguration
    └── PetstoreBindings.java       # UPDATE: Use @LocalServerPort injection
```

**Structure Decision**: Add new `lemon-check/spring` submodule alongside existing `core` and `junit` modules. This keeps Spring-specific code isolated and optional.

## Complexity Tracking

No constitution violations to justify.
