# Tasks: Operation ID Prefix Syntax

**Input**: Design documents from `/specs/003-operation-id-prefix/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- Exact file paths included in descriptions

---

## Phase 1: Setup

**Purpose**: No setup needed - this is a modification to existing code

*(No tasks - existing project structure is sufficient)*

---

## Phase 2: Foundational

**Purpose**: No foundational work needed - modifying existing Lexer

*(No tasks - TokenType.OPERATION_ID already exists)*

---

## Phase 3: User Story 1 - Distinguish Operation IDs from Identifiers (Priority: P1) 🎯 MVP

**Goal**: Lexer correctly tokenizes `^operationId` as OPERATION_ID and `operationId` as IDENTIFIER

**Independent Test**: Run `./gradlew :lemon-check:core:test --tests "*.LexerTest"` and verify all new `^` prefix tests pass

### Implementation for User Story 1

- [x] T001 [US1] Add `scanOperationId()` method to handle `^` prefix in lemon-check/core/src/main/kotlin/io/github/ktakashi/lemoncheck/scenario/Lexer.kt
- [x] T002 [US1] Modify `scanIdentifier()` to remove OPERATION_ID heuristic - all unprefixed identifiers become IDENTIFIER in lemon-check/core/src/main/kotlin/io/github/ktakashi/lemoncheck/scenario/Lexer.kt
- [x] T003 [US1] Handle `^` character in `nextToken()` dispatch - delegate to `scanOperationId()` in lemon-check/core/src/main/kotlin/io/github/ktakashi/lemoncheck/scenario/Lexer.kt
- [x] T004 [US1] Add edge case handling: lone `^`, `^` followed by non-letter produces ERROR token in lemon-check/core/src/main/kotlin/io/github/ktakashi/lemoncheck/scenario/Lexer.kt

**Checkpoint**: Lexer correctly distinguishes `^operationId` from `operationId`

---

## Phase 4: User Story 2 - Backward Compatibility (Priority: P2)

**Goal**: Unprefixed identifiers are consistently tokenized as IDENTIFIER, enabling clear migration path

**Independent Test**: Verify `call getUser` tokenizes `getUser` as IDENTIFIER (not OPERATION_ID)

### Implementation for User Story 2

- [x] T005 [US2] Update `should tokenize operation IDs` test to use `^` prefix syntax in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/LexerTest.kt
- [x] T006 [P] [US2] Add test for unprefixed identifiers producing IDENTIFIER tokens in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/LexerTest.kt
- [x] T007 [P] [US2] Add edge case tests: lone `^`, `^123`, `^ space`, `^^double` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/LexerTest.kt

**Checkpoint**: LexerTest passes with updated assertions for `^` prefix syntax

---

## Phase 5: Test Suite Fixes

**Goal**: All existing tests pass with updated `^operationId` syntax

**Independent Test**: Run `./gradlew test` - all tests must pass

### Parser Test Updates

- [x] T008 [P] Update `should parse simple scenario` to use `^listPets` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt
- [x] T009 [P] Update `should parse scenario with call action` to use `^getPetById` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt
- [x] T010 [P] Update `should parse scenario with extraction` to use `^createPet` and `^getPetById` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt
- [x] T011 [P] Update `should parse scenario with assertions` to use `^getPetById` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt
- [x] T012 [P] Update `should parse fragment definition` to use `^login` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt
- [x] T013 [P] Update `should parse scenario with fragment include` to use `^getProtectedData` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt
- [x] T014 [P] Update `should parse scenario outline with examples` to use `^getPetById` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt
- [x] T015 [P] Update `should parse scenario with using for spec selection` to use `^listPets` and `^getItems` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt
- [x] T016 [P] Update `should handle and keyword` to use `^listPets` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt
- [x] T017 [P] Update `should parse schema validation assertion` to use `^getPetById` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt
- [x] T018 [P] Update `should parse response time assertion` to use `^listPets` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt
- [x] T019 [P] Update `should parse multiple scenarios in one file` to use `^listPets` and `^createPet` in lemon-check/core/src/test/kotlin/io/github/ktakashi/lemoncheck/scenario/ParserTest.kt

### Resource File Updates (if applicable)

- [x] T020 [P] Update scenario files in lemon-check/core/build/resources/test/scenarios/ to use `^` prefix syntax (if any exist)
- [x] T021 [P] Update scenario files in lemon-check/junit/src/test/resources/ to use `^` prefix syntax (if any exist)
- [x] T022 [P] Update scenario files in samples/petstore/src/test/resources/ to use `^` prefix syntax (if any exist)

**Checkpoint**: `./gradlew test` completes with all tests passing

---

## Phase 6: Polish & Verification

**Goal**: Clean project state with all tests passing

- [x] T023 Run `./gradlew clean test` and verify all tests pass
- [x] T024 Run `./gradlew ktlintCheck` and fix any formatting issues
- [x] T025 Verify no compilation warnings remain

**Final Checkpoint**: Project state is clean - all tests pass, no lint errors

---

## Dependencies

```
T001 → T002 → T003 → T004 (Lexer changes must be sequential)
T004 → T005, T006, T007 (LexerTest updates depend on Lexer changes)
T004 → T008-T019 (ParserTest updates depend on Lexer changes)
T004 → T020-T022 (Resource updates depend on Lexer changes)
T005-T022 → T023 (Final verification after all test updates)
T023 → T024 → T025 (Sequential verification)
```

## Parallel Execution Opportunities

**After T004 completes** (Lexer implementation done):
- T005-T007 (LexerTest updates) can run in parallel with each other
- T008-T019 (ParserTest updates) can ALL run in parallel
- T020-T022 (Resource updates) can run in parallel

## Implementation Strategy

1. **MVP**: Complete Phase 3 (T001-T004) - Lexer correctly tokenizes with `^` prefix
2. **Incremental**: Complete Phases 4-5 sequentially to update all tests
3. **Verification**: Phase 6 ensures clean project state

**Suggested batch execution**:
- Batch 1: T001-T004 (sequential Lexer changes)
- Batch 2: T005-T022 (parallel test updates)
- Batch 3: T023-T025 (sequential verification)
