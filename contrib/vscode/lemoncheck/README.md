# LemonCheck VS Code Extension

IDE support for LemonCheck `.scenario` and `.fragment` files.

## Features

### Syntax Highlighting

Full syntax highlighting for LemonCheck scenario DSL:
- Keywords: `scenario`, `feature`, `given`, `when`, `then`, `and`, `but`
- Directives: `call`, `assert`, `extract`, `include`, `body`, `bodyFile`
- Operation IDs: `^operationId`
- Variables: `{{variableName}}`, `${variableName}`
- Tags: `@smoke`, `@ignore`, etc.
- JSONPath expressions: `$.path.to.field`
- JSON bodies and strings

### Auto-Completion

Intelligent completions for:
- **Operation IDs** - After typing `call ^`, shows all operations from your OpenAPI spec
- **Spec names** - For multi-spec projects, after `call using `
- **Fragment names** - After `include `
- **Variables** - After `{{` or `${`, shows extracted variables
- **Keywords** - Step keywords with snippets
- **Assertion operators** - `equals`, `contains`, `notEmpty`, etc.
- **Parameters** - Call parameters based on OpenAPI operation definition

### Go to Definition (F12)

Navigate to:
- **OpenAPI operations** - Click on `^operationId` to jump to the operation in your OpenAPI spec
- **Fragments** - Click on fragment name to jump to fragment definition
- **Variables** - Click on `{{variable}}` to jump to the `extract` statement

### Find All References (Shift+F12)

Find all usages of:
- **Fragments** - Right-click on a fragment definition or usage to find all `include` statements

### Hover Information

Hover over elements to see:
- **Operation details** - Method, path, parameters, request body, responses
- **Fragment preview** - Steps defined in the fragment
- **Keyword documentation** - Usage examples for keywords and operators

## Installation

### From VSIX (Local Install)

1. Build the extension:
   ```bash
   cd contrib/vscode/lemoncheck
   npm install
   npm run compile
   npm run package
   ```

2. Install the generated `.vsix` file:
   - Open VS Code
   - Press `Ctrl+Shift+P` (or `Cmd+Shift+P` on macOS)
   - Run "Extensions: Install from VSIX..."
   - Select the `.vsix` file

### Development

1. Open the extension folder in VS Code
2. Press `F5` to launch Extension Development Host
3. Open a `.scenario` file to test

## Configuration

Configure the extension in VS Code settings:

```json
{
  // Single OpenAPI spec
  "lemoncheck.openapi.path": "src/main/resources/openapi.yaml",
  
  // Multiple OpenAPI specs
  "lemoncheck.openapi.paths": [
    "specs/petstore.yaml",
    "specs/auth.yaml"
  ],
  
  // Path to search for fragment files
  "lemoncheck.fragmentsPath": "src/test/resources"
}
```

### Auto-Discovery

If no OpenAPI paths are configured, the extension automatically searches for:
- `openapi.yaml`, `openapi.yml`, `openapi.json`
- `swagger.yaml`, `swagger.yml`, `swagger.json`
- Any `openapi*.yaml` or `swagger*.yaml` files

## Commands

- **LemonCheck: Refresh OpenAPI** - Reload OpenAPI specifications
- **LemonCheck: Refresh Fragments** - Reload fragment files

## Requirements

- VS Code 1.85.0 or higher

## Known Issues

- Large OpenAPI specs may take a moment to parse initially
- Schema validation in completions doesn't resolve `$ref` references

## Contributing

Contributions are welcome! Please see the main lemon-check repository for contribution guidelines.

## License

Apache License 2.0
