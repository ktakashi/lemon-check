# OpenAPI Version Support

BerryCrush supports multiple OpenAPI specification versions through a flexible abstraction layer, enabling forward compatibility with future versions.

## Supported Versions

| Version | Status | Key Features |
|---------|--------|--------------|
| 2.x (Swagger) | Planned | Legacy API support |
| 3.0.x | ✅ Supported | Components, callbacks, links |
| 3.1.x | ✅ Supported | Webhooks, JSON Schema 2020-12 |
| 3.2.x | Future | Automatic version detection ready |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                      OpenAPI Abstraction                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐  │
│  │ OpenApiSpec  │◄─────│ OpenApiParser │◄─────│ YAML/JSON    │  │
│  │  (interface) │      │  (interface)  │      │    File      │  │
│  └──────┬───────┘      └───────────────┘      └──────────────┘  │
│         │                                                       │
│         │ Provides unified access                               │
│         ▼                                                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ SpecRegistry │  │ Operation-   │  │ HttpRequest  │          │
│  │              │  │ Resolver     │  │ Builder      │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Key Interfaces

#### OpenApiVersion

Enumeration of supported version categories:

```kotlin
enum class OpenApiVersion {
    V2_X,      // Swagger 2.0
    V3_0_X,    // OpenAPI 3.0.x
    V3_1_X,    // OpenAPI 3.1.x
    V3_2_X,    // OpenAPI 3.2.x (future)
    UNKNOWN
}
```

Version detection is automatic:

```kotlin
val version = OpenApiVersion.detect("3.1.0")  // Returns V3_1_X
```

#### OpenApiSpec

Unified interface for accessing OpenAPI specification content:

```kotlin
interface OpenApiSpec {
    val version: OpenApiVersion      // Detected version
    val specVersion: String          // Raw version string ("3.1.0")
    val info: SpecInfo               // Title, description, version
    val servers: List<ServerInfo>    // Server URLs
    val paths: Map<String, PathSpec> // API endpoints
    val components: ComponentsSpec?  // Reusable components
    val webhooks: Map<String, PathSpec>  // 3.1+ webhooks
    
    fun getOperation(operationId: String): OperationSpec?
    fun getAllOperations(): List<OperationSpec>
    
    val rawModel: Any  // Access underlying parser model
}
```

#### OpenApiParser

Parser interface for loading specifications:

```kotlin
interface OpenApiParser {
    fun parse(path: Path): OpenApiSpec
    fun parse(path: String): OpenApiSpec
    fun parseContent(content: String): OpenApiSpec
    fun supportedVersions(): Set<OpenApiVersion>
}
```

## Feature Detection

BerryCrush uses **feature detection over version checking** for maximum compatibility:

```kotlin
// ✅ Recommended: Check for feature presence
if (spec.hasWebhooks()) {
    spec.webhooks.forEach { (name, pathSpec) ->
        // Handle webhook
    }
}

// ❌ Avoid: Checking version numbers
if (spec.version >= OpenApiVersion.V3_1_X) {  // Not recommended
    // ...
}
```

This approach ensures forward compatibility with unknown future versions.

## OpenAPI 3.1.x Features

### Webhooks

Webhooks are callback URLs that an API provider calls to deliver events:

```yaml
# In OpenAPI 3.1.x spec
webhooks:
  newBooking:
    post:
      summary: New booking notification
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/Booking'
```

Access in BerryCrush:

```kotlin
spec.webhooks["newBooking"]?.operations?.get(HttpMethod.POST)
```

### JSON Schema 2020-12

OpenAPI 3.1.x uses JSON Schema Draft 2020-12 with new keywords:

| Keyword | 3.0.x | 3.1.x |
|---------|-------|-------|
| `nullable` | `nullable: true` | `type: ["string", "null"]` |
| `exclusiveMinimum` | Boolean | Number |
| `exclusiveMaximum` | Boolean | Number |
| `unevaluatedProperties` | N/A | Supported |
| `contentMediaType` | N/A | Supported |

BerryCrush's `SchemaSpec` interface handles these differences transparently.

## Implementation Details

### SwaggerParserAdapter

The default implementation uses `io.swagger.parser.v3:swagger-parser`:

```kotlin
// Internal implementation
internal class SwaggerParserAdapter : OpenApiParser {
    override fun parse(path: Path): OpenApiSpec {
        val result = OpenAPIParser().readLocation(path.toString(), null, null)
        return SwaggerOpenApiSpec(result.openAPI)
    }
}
```

### SpecRegistry Integration

`SpecRegistry` stores parsed specs and provides operation resolution:

```kotlin
data class LoadedSpec(
    val name: String,
    val spec: OpenApiSpec,  // Uses abstraction
    val baseUrl: String,
)
```

## Best Practices

1. **Use `OpenApiSpec` Interface**
   - Don't access `rawModel` unless absolutely necessary
   - The abstraction handles version differences

2. **Check Features, Not Versions**
   - Use `hasWebhooks()`, `hasComponents()` methods
   - Don't hardcode version checks

3. **Handle Missing Features Gracefully**
   - Webhooks return empty map for 3.0.x specs
   - Code should work regardless of spec version

4. **Test with Multiple Versions**
   - Test fixtures include both 3.0.x and 3.1.x specs
   - Ensure scenarios work across versions

## Test Fixtures

Real-world OpenAPI 3.1.x specs for testing:

| File | Source | Features |
|------|--------|----------|
| `petstore.yaml` | Local | OpenAPI 3.0.3 baseline |
| `train-travel.yaml` | ReadMe.io | 3.1.0, webhooks, OAuth2 |
| `tictactoe.yaml` | OAI Official | 3.1.0, webhooks, callbacks |

Located in `core/src/test/resources/`.

## See Also

- [Architecture Overview](architecture.md)
- [Module Structure](modules.md)
- [OpenAPI 3.1 Specification](https://spec.openapis.org/oas/v3.1.0)
