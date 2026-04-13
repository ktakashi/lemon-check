# Auto-Test Provider Extensibility

LemonCheck's auto-test feature supports custom test providers through Java's ServiceLoader pattern. This allows users to add custom invalid request tests and security test payloads without modifying the core library.

## Overview

The auto-test system uses two types of providers:

1. **InvalidTestProvider** - Generates invalid values for schema constraint testing
2. **SecurityTestProvider** - Generates security attack payloads

Both types are discovered automatically via ServiceLoader, allowing you to add custom providers by simply adding classes to your project.

## Built-in Providers

### Invalid Test Providers

| Test Type | Description |
|-----------|-------------|
| minLength | Strings shorter than minLength constraint |
| maxLength | Strings longer than maxLength constraint |
| pattern | Strings not matching the pattern |
| format | Invalid format values (email, uuid, date, etc.) |
| enum | Values not in the enum list |
| minimum | Numbers below the minimum |
| maximum | Numbers above the maximum |
| type | Wrong type values (e.g., string for number field) |
| required | Missing required fields |
| minItems | Arrays with fewer items than minItems |
| maxItems | Arrays with more items than maxItems |

### Security Test Providers

| Test Type | Display Name | Description |
|-----------|--------------|-------------|
| SQLInjection | SQL Injection | SQL injection attack payloads |
| XSS | XSS | Cross-site scripting payloads |
| PathTraversal | Path Traversal | Path traversal attack patterns |
| CommandInjection | Command Injection | Shell command injection payloads |
| LDAPInjection | LDAP Injection | LDAP query injection payloads |
| XXE | XXE | XML External Entity payloads |
| HeaderInjection | Header Injection | HTTP header injection payloads |

## Creating Custom Providers

### Custom Invalid Test Provider

Create a class implementing `InvalidTestProvider`:

```kotlin
package com.example

import io.github.ktakashi.lemoncheck.autotest.provider.InvalidTestProvider
import io.github.ktakashi.lemoncheck.autotest.provider.InvalidTestValue
import io.swagger.v3.oas.models.media.Schema

class NumericOverflowProvider : InvalidTestProvider {
    // Unique identifier for test reports and excludes
    override val testType: String = "numericOverflow"
    
    // Higher priority overrides built-in providers with same testType
    override val priority: Int = 100

    override fun canHandle(schema: Schema<*>): Boolean =
        schema.type == "integer" || schema.type == "number"

    override fun generateInvalidValues(
        fieldName: String,
        schema: Schema<*>,
    ): List<InvalidTestValue> = listOf(
        InvalidTestValue(
            value = Long.MAX_VALUE,
            description = "Numeric overflow value",
        ),
        InvalidTestValue(
            value = Double.POSITIVE_INFINITY,
            description = "Infinity value",
        ),
    )
}
```

### Custom Security Test Provider

Create a class implementing `SecurityTestProvider`:

```kotlin
package com.example

import io.github.ktakashi.lemoncheck.autotest.ParameterLocation
import io.github.ktakashi.lemoncheck.autotest.provider.SecurityTestProvider
import io.github.ktakashi.lemoncheck.autotest.provider.SecurityPayload

class NoSqlInjectionProvider : SecurityTestProvider {
    // Unique identifier for excludes
    override val testType: String = "NoSQLInjection"
    
    // Human-readable name for test reports
    override val displayName: String = "NoSQL Injection"
    
    // Higher priority overrides built-in providers with same testType
    override val priority: Int = 100

    override fun applicableLocations(): Set<ParameterLocation> =
        setOf(ParameterLocation.BODY, ParameterLocation.QUERY)

    override fun generatePayloads(): List<SecurityPayload> = listOf(
        SecurityPayload(
            name = "MongoDB $ne injection",
            payload = "{\"\$ne\": null}",
        ),
        SecurityPayload(
            name = "MongoDB $where injection",
            payload = "{\"\$where\": \"sleep(5000)\"}",
        ),
    )
}
```

### Registering via ServiceLoader

Create a service configuration file in your project:

**`src/main/resources/META-INF/services/io.github.ktakashi.lemoncheck.autotest.provider.InvalidTestProvider`**:
```
com.example.NumericOverflowProvider
```

**`src/main/resources/META-INF/services/io.github.ktakashi.lemoncheck.autotest.provider.SecurityTestProvider`**:
```
com.example.NoSqlInjectionProvider
```

The providers will be automatically discovered and registered when the auto-test system initializes.

## Provider Priority

Each provider has a `priority` property (default: 0 for built-in, 100 for user providers):

- Higher priority providers override lower priority ones with the same `testType`
- Equal priority: later registration wins
- Built-in providers have priority 0
- User providers should use priority >= 100 to override built-in

## Excluding Test Types

Use the `excludes` option in your scenario to skip certain test types:

```
auto-test:
  operations: [createPet]
  types: [invalid, security]
  excludes: [SQLInjection, maxLength, myCustomType]
```

The `excludes` option works with both built-in and custom provider test types.

## Programmatic Registration

You can also register providers programmatically:

```kotlin
val registry = AutoTestProviderRegistry.withDefaults()
registry.registerInvalid(MyCustomInvalidProvider())
registry.registerSecurity(MyCustomSecurityProvider())

val generator = AutoTestGenerator(openApi, registry)
```

Or create an empty registry for full control:

```kotlin
val registry = AutoTestProviderRegistry.empty()
// Only your custom providers will be used
registry.registerInvalid(MyOnlyProvider())
```

## Test Type Naming Conventions

- Use **camelCase** for `testType` (e.g., `numericOverflow`, `NoSQLInjection`)
- Use **human-readable names** for `displayName` (e.g., "Numeric Overflow", "NoSQL Injection")
- The `testType` is used for:
  - Test identification and deduplication
  - `excludes` configuration
  - Provider override matching
- The `displayName` is used for:
  - Test reports (IntelliJ, JUnit XML)
  - Scenario output logs
