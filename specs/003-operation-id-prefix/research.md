# Research: Operation ID Prefix Syntax

**Feature**: 003-operation-id-prefix  
**Date**: 2026-04-08

## Problem Analysis

### Current Behavior (Bug)

The Lexer currently uses a heuristic to distinguish OPERATION_ID from IDENTIFIER:

```kotlin
// Lexer.kt:302-308
val text = sb.toString()
val type = KEYWORDS[text.lowercase()] ?: TokenType.IDENTIFIER

// If it looks like an operationId (camelCase or contains an op-like name)
return if (type == TokenType.IDENTIFIER && text.first().isLowerCase()) {
    Token(TokenType.OPERATION_ID, text, loc)
} else {
    Token(type, text, loc)
}
```

**Problem**: Any identifier starting with a lowercase letter is tokenized as OPERATION_ID, including:
- Variable names in extractions: `extract $.id => petId` (petId becomes OPERATION_ID)
- Parameter names: `petId: {{petId}}` (petId becomes OPERATION_ID)
- Spec names: `call using petstore listPets` (petstore becomes OPERATION_ID)

### Test Failure Root Cause

The test `should parse scenario with extraction` fails because:

1. Source: `extract $.id => petId`
2. Lexer tokenizes `petId` as `OPERATION_ID` (lowercase start)
3. Parser's `parseExtractAction()` expects `IDENTIFIER` or `VARIABLE`:
   ```kotlin
   val variableName = when (current.type) {
       TokenType.VARIABLE, TokenType.IDENTIFIER -> current.value
       else -> {
           errors.add(ParseError("Expected variable name", currentLocation()))
           return null
       }
   }
   ```
4. `OPERATION_ID` falls into the `else` branch → ParseError

## Solution Design

### Decision: Use `^` Prefix for Operation IDs

| Syntax | Token Type | Example |
|--------|------------|---------|
| `^operationId` | OPERATION_ID | `call ^listPets` |
| `operationId` | IDENTIFIER | `extract $.id => petId` |

**Rationale**:
- `^` is visually distinctive and not commonly used in DSL syntax
- Explicit marking eliminates ambiguity
- Follows the pattern of other DSLs using prefixes for special types (e.g., `$variable`, `@annotation`)

**Alternatives Considered**:
1. **Type inference from context**: Parser knows `call` expects operation ID
   - Rejected: Requires parser to communicate back to lexer; complex and fragile
2. **Quoted operation IDs**: `call "listPets"`
   - Rejected: Loses distinction from string literals
3. **Different prefix (e.g., `@`, `#`)**: 
   - `@` conflicts with JSON path syntax
   - `#` is used for comments
   - `^` is available and uncommon

### Implementation Approach

1. **Add `scanOperationId()` method** in Lexer:
   - Triggered when `^` is encountered
   - Consumes `^` then reads identifier
   - Returns OPERATION_ID token with identifier value (excluding `^`)
   - Returns ERROR token if `^` not followed by valid identifier start

2. **Modify `scanIdentifier()` method**:
   - Remove OPERATION_ID heuristic
   - All unprefixed identifiers become IDENTIFIER tokens

3. **Add `CARET` handling** in `scanSymbol()`:
   - `^` followed by letter → delegate to `scanOperationId()`
   - `^` followed by non-letter → ERROR token

4. **Update tests**:
   - LexerTest: Add `^operationId` tests, update existing OPERATION_ID tests
   - ParserTest: Update scenario strings to use `^operationId` syntax

## Edge Cases

| Input | Expected Token(s) | Notes |
|-------|-------------------|-------|
| `^listPets` | OPERATION_ID("listPets") | Normal case |
| `listPets` | IDENTIFIER("listPets") | Unprefixed = identifier |
| `^` | ERROR("Expected identifier") | Lone caret |
| `^^listPets` | ERROR + OPERATION_ID("listPets") | Double caret |
| `^123` | ERROR("Invalid operation ID") | Numbers not allowed as start |
| `^ listPets` | ERROR + IDENTIFIER("listPets") | Space after caret |
| `^List_Pets_123` | OPERATION_ID("List_Pets_123") | Underscores and numbers allowed |

## Impact Assessment

### Files to Modify

| File | Changes |
|------|---------|
| `Lexer.kt` | Add `scanOperationId()`, modify `scanIdentifier()`, handle `^` in `scanSymbol()` |
| `LexerTest.kt` | Add tests for `^` prefix, update existing OPERATION_ID tests |
| `ParserTest.kt` | Update all scenario strings to use `^operationId` syntax |

### No Changes Needed

- `Token.kt`: OPERATION_ID already exists
- `Parser.kt`: Already accepts both IDENTIFIER and OPERATION_ID for operation ID position

## References

- Current Lexer implementation: `lemon-check/core/src/main/kotlin/.../scenario/Lexer.kt`
- Test failure trace: ParserTest.kt:60 in `should parse scenario with extraction`
