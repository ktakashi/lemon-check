# Quickstart: Operation ID Prefix Syntax

**Feature**: 003-operation-id-prefix  
**Date**: 2026-04-08

## What Changed

Operation IDs in scenario files now require a `^` prefix to distinguish them from regular identifiers.

## Before vs After

### Calling an API Operation

**Before** (deprecated):
```
call listPets
call getPetById
  petId: 123
```

**After** (required):
```
call ^listPets
call ^getPetById
  petId: 123
```

### Extraction

No change to extraction syntax. Variable names are identifiers, not operation IDs:

```
when I create a pet
  call ^createPet
  extract $.id => petId
then I retrieve it
  call ^getPetById
    petId: {{petId}}
```

### Using Multiple Specs

```
scenario: Multi-spec test
  when I call APIs
    call using petstore ^listPets
    call using inventory ^getItems
```

## Quick Reference

| Syntax | Token Type | Use Case |
|--------|------------|----------|
| `^operationId` | OPERATION_ID | API operation reference |
| `identifierName` | IDENTIFIER | Variable names, parameter names, spec names |

## Migration

If you have existing `.scenario` files:

1. Find all `call` statements
2. Add `^` prefix before the operation name
3. Do NOT add prefix to parameter names or extracted variable names

**Search pattern** (regex): `call\s+([a-z]\w*)`  
**Replace with**: `call ^$1`
