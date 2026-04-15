# Parameters Scope Architecture

This document describes the parameters scope hierarchy in BerryCrush scenario files.

## Overview

BerryCrush supports the `parameters:` block to configure scenario execution behavior. Parameters can be specified at different scope levels, affecting which scenarios inherit the configuration.

## Scope Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│                    Scenario File (.scenario)                │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ parameters:                    [File-Level Scope]     │  │
│  │   shareVariablesAcrossScenarios: true                 │  │
│  │   timeout: 30                                          │  │
│  └───────────────────────────────────────────────────────┘  │
│                              │                              │
│         ┌────────────────────┼────────────────────┐        │
│         ▼                    ▼                    ▼        │
│  ┌─────────────┐    ┌─────────────────┐    ┌───────────┐  │
│  │ feature: A  │    │ feature: B      │    │ scenario: │  │
│  │ ┌─────────┐ │    │ ┌─────────────┐ │    │ standalone│  │
│  │ │params:  │ │    │ │params:      │ │    │           │  │
│  │ │ share:  │ │    │ │ share: false│ │    │ (inherits │  │
│  │ │  true   │ │    │ │ timeout: 60 │ │    │  file     │  │
│  │ └─────────┘ │    │ └─────────────┘ │    │  params)  │  │
│  │ [Feature    │    │ [Feature       │    └───────────┘  │
│  │  Scope]     │    │  Scope]        │                    │
│  │             │    │                │                    │
│  │ scenario: 1 │    │ scenario: 3    │                    │
│  │ scenario: 2 │    │ scenario: 4    │                    │
│  └─────────────┘    └────────────────┘                    │
└─────────────────────────────────────────────────────────────┘
```

## Current Implementation (File-Level Only)

Currently, BerryCrush only supports **file-level parameters**:

```scenario
# File-level parameters apply to ALL scenarios in this file
parameters:
  shareVariablesAcrossScenarios: true
  timeout: 30

scenario: first test
  # uses file-level parameters

scenario: second test  
  # also uses file-level parameters
```

### Supported Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `shareVariablesAcrossScenarios` | boolean | `false` | Share extracted variables between scenarios |
| `timeout` | integer | 30 | Request timeout in seconds |
| `baseUrl` | string | from OpenAPI | Override base URL for API calls |

## Proposed: Feature-Level Parameters

Feature-level parameters allow scoped configuration within feature blocks:

```scenario
# File-level defaults
parameters:
  timeout: 30

feature: Pet CRUD Operations
  # Feature-level overrides (only affects scenarios in this feature)
  parameters:
    shareVariablesAcrossScenarios: true
  
  scenario: create pet
    when: POST /pet
    then: status is 201
    extract: petId from $.id
  
  scenario: get created pet
    # Can access {{petId}} from previous scenario
    when: GET /pet/{{petId}}
    then: status is 200

feature: Store Operations  
  # Different feature, different parameters
  parameters:
    timeout: 60
  
  scenario: place order
    # Does NOT have access to petId from Pet CRUD feature
    when: POST /store/order
```

## Parameter Inheritance

Parameters follow a cascading inheritance model:

1. **Default values** (hardcoded in BerryCrush)
2. **File-level parameters** (override defaults)
3. **Feature-level parameters** (override file-level)

### Precedence Rules

```
Most Specific Wins:
  Feature parameters > File parameters > Defaults
```

Example:
```scenario
parameters:
  timeout: 30           # File-level
  shareVariables: false # File-level

feature: Quick Tests
  parameters:
    timeout: 10         # Feature overrides to 10
    # shareVariables inherits false from file
```

## Variable Sharing Behavior

### Without `shareVariablesAcrossScenarios`

```
scenario: A ──▶ scenario: B ──▶ scenario: C
    │              │              │
    └──variables   └──variables   └──variables
       (isolated)     (isolated)     (isolated)
```

### With File-Level `shareVariablesAcrossScenarios: true`

```
scenario: A ──▶ scenario: B ──▶ scenario: C
    │              │              │
    └──────────────┴──────────────┘
              shared variables
```

### With Feature-Level `shareVariablesAcrossScenarios: true`

```
feature: X                    feature: Y            standalone
┌──────────────────────┐    ┌───────────────┐    ┌───────────┐
│ scenario: A ─▶ B ─▶ C │    │ scenario: D   │    │ scenario: E │
│ └────shared vars────┘ │    │   (isolated)  │    │   (isolated)│
└──────────────────────┘    └───────────────┘    └───────────┘
      (shared within)           (no sharing)        (no sharing)
```

## Variable Lifecycle

### Scope Boundaries

| Source | Visible To | Lifecycle |
|--------|-----------|-----------|
| Background (file-level) | All scenarios in file | Reset per scenario |
| Background (feature) | All scenarios in feature | Reset per scenario |
| Extracted (no sharing) | Same scenario only | Scenario duration |
| Extracted (file sharing) | All subsequent scenarios | File duration |
| Extracted (feature sharing) | Subsequent scenarios in feature | Feature duration |

### Variable Reset Points

```
┌─ File Start
│
├── feature: A (shareVariables: true)
│   ├── scenario: 1    ← variables start fresh
│   │   extract: x=1
│   ├── scenario: 2    ← has x=1
│   │   extract: y=2
│   └── scenario: 3    ← has x=1, y=2
│       (feature ends, variables cleared)
│
├── feature: B (shareVariables: false)
│   ├── scenario: 4    ← NO access to x, y
│   └── scenario: 5    ← isolated
│
└── scenario: 6        ← standalone, isolated
```

## AST Structure

### Current Structure

```kotlin
data class ScenarioFileNode(
    val scenarios: List<ScenarioNode>,
    val fragments: List<FragmentNode>,
    val features: List<FeatureNode>,
    val parameters: ParametersNode?,  // File-level only
    override val location: SourceLocation,
) : AstNode()

data class FeatureNode(
    val name: String,
    val description: String?,
    val background: BackgroundNode?,
    val scenarios: List<ScenarioNode>,
    val tags: Set<String>,
    override val location: SourceLocation,
    // No parameters field
) : AstNode()
```

### Proposed Structure

```kotlin
data class FeatureNode(
    val name: String,
    val description: String?,
    val background: BackgroundNode?,
    val scenarios: List<ScenarioNode>,
    val tags: Set<String>,
    val parameters: ParametersNode?,  // NEW: Feature-level parameters
    override val location: SourceLocation,
) : AstNode()
```

## Implementation Notes

### Parser Changes

The parser needs to recognize `parameters:` after `feature:` declaration:

```ebnf
feature = "feature:" , name , NEWLINE ,
          [ parameters ] ,     (* NEW *)
          [ background ] ,
          { scenario } ;
```

### Executor Changes

The `BerryCrushScenarioExecutor` needs to:
1. Merge feature parameters with file parameters
2. Create isolated `ExecutionContext` per feature when sharing is scoped
3. Reset variables at feature boundaries

## See Also

- [Scenario Syntax](scenario-syntax.md) - Full syntax reference
- [Architecture](architecture.md) - System architecture overview
- [Feature Plan](.copilot/feature-level-parameters/plan.md) - Implementation plan
