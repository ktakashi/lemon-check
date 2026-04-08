# Implementation Plan: Operation ID Prefix Syntax

**Branch**: `003-operation-id-prefix` | **Date**: 2026-04-08 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/003-operation-id-prefix/spec.md`

## Summary

Modify the Lexer to require `^` prefix for OPERATION_ID tokens, resolving the ambiguity between operation IDs and identifiers. Currently, the Lexer uses a heuristic (lowercase first letter = OPERATION_ID) which incorrectly tokenizes variable names like `petId` as OPERATION_ID, causing parse failures in extraction statements. The fix adds `^` as an explicit marker: `^operationId` → OPERATION_ID, `operationId` → IDENTIFIER.

## Technical Context

**Language/Version**: Kotlin 2.3.20, Java 21  
**Primary Dependencies**: JUnit 5.11.4, JUnit Platform 1.11.4, Swagger Parser 2.1.22, Jackson 2.17.0, JSONPath 2.9.0  
**Storage**: N/A  
**Testing**: JUnit 5 via Gradle (./gradlew test)  
**Target Platform**: JVM 21
**Project Type**: Library (OpenAPI BDD testing framework)  
**Performance Goals**: N/A (Lexer performance not a concern for typical scenario file sizes)  
**Constraints**: Must pass all existing tests after updating expected behavior  
**Scale/Scope**: Single file modification (Lexer.kt) + test updates

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | ✅ PASS | Clean code with clear naming, single-purpose function for `^` handling |
| II. User Experience | ✅ PASS | `^` prefix is explicit and unambiguous, follows common DSL conventions |
| III. Maintainability | ✅ PASS | Simple, localized change in Lexer.kt only |
| IV. Testing Standards | ✅ PASS | Will update LexerTest with new test cases for `^` prefix |
| V. Flexibility | ✅ PASS | Change is isolated; no architectural impact |

**Gate passed**: All constitution principles satisfied.

## Project Structure

### Documentation (this feature)

```text
specs/003-operation-id-prefix/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output  
├── contracts/           # Phase 1 output (N/A for this feature)
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
lemon-check/
├── core/
│   ├── src/main/kotlin/io/github/ktakashi/lemoncheck/scenario/
│   │   ├── Lexer.kt      # PRIMARY: Add scanOperationId(), modify scanIdentifier()
│   │   ├── Token.kt      # No changes needed (TokenType.OPERATION_ID already exists)
│   │   └── Parser.kt     # OPTIONAL: Update to require OPERATION_ID for call target
│   └── src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/
│       ├── LexerTest.kt  # Update: Add ^prefix tests, modify existing OPERATION_ID tests
│       └── ParserTest.kt # Fix: Update scenario inputs to use ^operationId syntax
```

**Structure Decision**: Multi-module Gradle project with core (DSL/lexer/parser) and junit (test engine) modules. This feature affects only the `lemon-check/core` module.

## Constitution Check (Post-Design)

*Re-evaluation after Phase 1 design completion.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Code Quality | ✅ PASS | `scanOperationId()` is a small, focused function; clear separation of concerns |
| II. User Experience | ✅ PASS | `^` prefix provides clear visual distinction; migration path documented in quickstart.md |
| III. Maintainability | ✅ PASS | Change is isolated to Lexer.kt with no impact on Parser logic |
| IV. Testing Standards | ✅ PASS | Test updates planned for LexerTest (edge cases) and ParserTest (syntax migration) |
| V. Flexibility | ✅ PASS | OPERATION_ID token type remains unchanged; only tokenization trigger changes |

**Post-design gate passed**: No constitution violations identified.
