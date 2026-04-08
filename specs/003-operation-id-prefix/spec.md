# Feature Specification: Operation ID Prefix Syntax

**Feature Branch**: `003-operation-id-prefix`  
**Created**: 2026-04-08  
**Status**: Draft  
**Input**: User description: "Tokenizer returns operation_id instead of identifier. This is caused due to the lack of prefix on operation_id. Modify operation_id to have ^ as its prefix. e.g. ^operationId = OPERATION_ID, operationId = IDENTIFIER."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Distinguish Operation IDs from Identifiers (Priority: P1)

As a scenario author, I want to explicitly mark operation IDs with a `^` prefix so that the lexer correctly distinguishes between operation references and regular identifiers.

**Why this priority**: This is the core functionality that resolves the ambiguity between operation IDs and identifiers, which is currently causing incorrect tokenization.

**Independent Test**: Can be fully tested by writing scenario files with both `^operationId` (expecting OPERATION_ID token) and `operationId` (expecting IDENTIFIER token), and verifying correct token types are produced.

**Acceptance Scenarios**:

1. **Given** a scenario file contains `^getUser`, **When** the lexer tokenizes it, **Then** it produces an OPERATION_ID token with value "getUser"
2. **Given** a scenario file contains `userName`, **When** the lexer tokenizes it, **Then** it produces an IDENTIFIER token with value "userName"
3. **Given** a scenario file contains `^createOrder` followed by `orderData`, **When** the lexer tokenizes it, **Then** it produces OPERATION_ID "createOrder" followed by IDENTIFIER "orderData"

---

### User Story 2 - Backward Compatibility Warning (Priority: P2)

As a scenario author upgrading from a previous version, I want clear feedback when using unprefixed operation IDs so that I can update my scenario files to the new syntax.

**Why this priority**: Helps existing users transition smoothly without breaking their existing scenarios silently.

**Independent Test**: Can be tested by parsing a scenario that uses an unprefixed camelCase word where an operation ID is expected, and verifying the token type is IDENTIFIER (not OPERATION_ID).

**Acceptance Scenarios**:

1. **Given** a scenario file uses `call getUser` (unprefixed), **When** the parser processes it, **Then** "getUser" is tokenized as IDENTIFIER
2. **Given** a scenario file uses `call ^getUser` (prefixed), **When** the parser processes it, **Then** "getUser" is tokenized as OPERATION_ID

---

### Edge Cases

- What happens when `^` appears without an identifier following it? → Produces an ERROR token
- How does the lexer handle `^^identifier` (double caret)? → First `^` is consumed, second `^identifier` produces OPERATION_ID
- What happens with `^123invalid`? → Produces ERROR token (operation IDs must start with a letter)
- How are whitespace and `^` handled (e.g., `^ operationId`)? → `^` followed by whitespace produces ERROR token; operation ID must immediately follow

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST treat `^` followed immediately by a valid identifier as an OPERATION_ID token
- **FR-002**: System MUST treat standalone identifiers (without `^` prefix) as IDENTIFIER tokens
- **FR-003**: System MUST include only the identifier value (excluding `^`) in the OPERATION_ID token's text
- **FR-004**: System MUST produce an ERROR token when `^` is not immediately followed by a valid identifier character
- **FR-005**: System MUST recognize operation ID identifiers that start with a letter and contain letters, digits, or underscores

### Key Entities

- **Token**: Represents a lexical unit with type (OPERATION_ID, IDENTIFIER, etc.), text value, and source location
- **OPERATION_ID Token**: A token type representing an explicit reference to an OpenAPI operation, prefixed with `^` in source
- **IDENTIFIER Token**: A token type representing a general identifier without the operation ID prefix

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All scenario files using `^operationId` syntax produce OPERATION_ID tokens with 100% accuracy
- **SC-002**: All scenario files using unprefixed identifiers produce IDENTIFIER tokens (no false positives for OPERATION_ID)
- **SC-003**: Lexer correctly handles all edge cases (lone `^`, invalid characters after `^`) without crashing
- **SC-004**: Existing test suite passes after modification with updated assertions for new behavior

## Assumptions

- The `^` character is not currently used for any other purpose in the scenario file syntax
- Operation IDs follow standard identifier naming rules (start with letter, contain letters/digits/underscores)
- This change affects only the lexer; parser changes for consuming the new token type are out of scope for this feature
- Existing scenario files will need to be updated to use the `^` prefix for operation IDs
