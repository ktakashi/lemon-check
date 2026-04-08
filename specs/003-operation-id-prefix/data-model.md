# Data Model: Operation ID Prefix Syntax

**Feature**: 003-operation-id-prefix  
**Date**: 2026-04-08

## Overview

This feature modifies token classification in the Lexer. No new data structures are introduced; existing entities are clarified.

## Entities

### Token (existing)

```
Token
├── type: TokenType      # Enum value identifying token kind
├── value: String        # Text content (excludes prefix for OPERATION_ID)
└── location: SourceLocation
    ├── line: Int
    ├── column: Int
    └── file: String?
```

### TokenType.OPERATION_ID (behavioral change)

**Before**: Assigned to any identifier starting with lowercase letter  
**After**: Assigned only to identifiers prefixed with `^` in source

| Property | Before | After |
|----------|--------|-------|
| Trigger | Lowercase first char | `^` prefix in source |
| Value | Full identifier text | Identifier without `^` |
| Example Source | `listPets` | `^listPets` |
| Example Value | `"listPets"` | `"listPets"` |

### TokenType.IDENTIFIER (behavioral change)

**Before**: Assigned to identifiers starting with uppercase letter  
**After**: Assigned to all unprefixed identifiers (regardless of case)

| Property | Before | After |
|----------|--------|-------|
| Trigger | Uppercase first char | No `^` prefix |
| Value | Full identifier text | Full identifier text |
| Example Source | `PetId` | `petId` |
| Example Value | `"PetId"` | `"petId"` |

## Token Stream Examples

### Example 1: Call with Operation ID

**Source**: `call ^listPets`

| Position | Type | Value |
|----------|------|-------|
| 1 | CALL | "call" |
| 2 | OPERATION_ID | "listPets" |

### Example 2: Extraction

**Source**: `extract $.id => petId`

| Position | Type | Value |
|----------|------|-------|
| 1 | EXTRACT | "extract" |
| 2 | JSON_PATH | "$.id" |
| 3 | ARROW | "=>" |
| 4 | IDENTIFIER | "petId" |

### Example 3: Mixed Usage

**Source**: `call ^getPetById` followed by `petId: 123`

| Position | Type | Value |
|----------|------|-------|
| 1 | CALL | "call" |
| 2 | OPERATION_ID | "getPetById" |
| 3 | NEWLINE | "\n" |
| 4 | INDENT | "  " |
| 5 | IDENTIFIER | "petId" |
| 6 | COLON | ":" |
| 7 | NUMBER | "123" |

## State Transitions

### Lexer State: Initial/Default

```
Input char  | Action
------------|---------------------------
'^'         | → scanOperationId()
[a-zA-Z_]   | → scanIdentifier()
[other]     | → [existing logic]
```

### Lexer State: scanOperationId()

```
Step | Condition | Action
-----|-----------|--------
1    | peek() == '^' | advance(), consume '^'
2    | peek().isLetter() | → read identifier body
2'   | else | → return ERROR("Expected identifier after ^")
3    | while isLetterOrDigit or '_' | append to buffer
4    | return | OPERATION_ID with buffer value
```

## Validation Rules

1. **OPERATION_ID identifier format**: Must start with letter, may contain letters, digits, underscores
2. **`^` must be immediately followed by identifier**: No whitespace allowed between `^` and identifier
3. **OPERATION_ID value excludes prefix**: Token value is the identifier without `^`
