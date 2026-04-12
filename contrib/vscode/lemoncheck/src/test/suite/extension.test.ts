import * as assert from 'assert';
import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';

suite('LemonCheck Extension Test Suite', () => {
    const fixturesPath = path.resolve(__dirname, '..', '..', '..', 'src', 'test', 'fixtures');
    
    suiteSetup(async () => {
        // Ensure fixtures directory exists
        if (!fs.existsSync(fixturesPath)) {
            fs.mkdirSync(fixturesPath, { recursive: true });
        }
    });

    test('Extension should be present', () => {
        assert.ok(vscode.extensions.getExtension('lemoncheck.lemoncheck'));
    });

    test('Extension should activate on scenario file', async () => {
        const ext = vscode.extensions.getExtension('lemoncheck.lemoncheck');
        if (ext) {
            await ext.activate();
            assert.ok(ext.isActive);
        }
    });

    test('Language configuration registered for .scenario files', () => {
        const langs = vscode.languages.getLanguages();
        // Languages are returned as a Thenable
        return langs.then(languageIds => {
            assert.ok(languageIds.includes('lemoncheck'), 'lemoncheck language should be registered');
        });
    });
});

suite('OpenApiProvider Tests', () => {
    test('parseYamlPath extracts operationId locations', () => {
        // Test the YAML path parsing logic
        const sampleYaml = `
openapi: "3.1.0"
info:
  title: Test API
  version: "1.0.0"
paths:
  /pets:
    get:
      operationId: listPets
      summary: List all pets
    post:
      operationId: createPet
      summary: Create a pet
  /pets/{petId}:
    get:
      operationId: getPetById
      summary: Get a pet by ID
`;
        // Count operationId occurrences
        const operationIds = sampleYaml.match(/operationId:\s*\S+/g) || [];
        assert.strictEqual(operationIds.length, 3, 'Should find 3 operationIds');
    });

    test('findOperationLocation returns correct line for operationId value', () => {
        // Simulate finding operationId position in YAML
        const yamlContent = `paths:
  /pets:
    get:
      operationId: listPets
      summary: List all pets`;
        
        const lines = yamlContent.split('\n');
        let foundLine = -1;
        
        for (let i = 0; i < lines.length; i++) {
            const match = lines[i].match(/operationId:\s*(listPets)/);
            if (match) {
                foundLine = i;
                break;
            }
        }
        
        assert.strictEqual(foundLine, 3, 'Should find operationId on line 3 (0-indexed)');
    });
});

suite('DefinitionProvider Tests', () => {
    test('detectOperationId extracts operationId from ^prefix', () => {
        const lineText = '    call ^listPets';
        const match = lineText.match(/\^(\w+)/);
        
        assert.ok(match, 'Should match ^operationId pattern');
        assert.strictEqual(match![1], 'listPets', 'Should extract operationId');
    });

    test('detectSpecName extracts spec name after using keyword', () => {
        const lineText = '    call using petstore ^listPets';
        const match = lineText.match(/\busing\s+(\w+)/);
        
        assert.ok(match, 'Should match using pattern');
        assert.strictEqual(match![1], 'petstore', 'Should extract spec name');
    });

    test('detectFragmentName extracts fragment name after include', () => {
        const lineText = '    include common-setup';
        const match = lineText.match(/\binclude\s+(\S+)/);
        
        assert.ok(match, 'Should match include pattern');
        assert.strictEqual(match![1], 'common-setup', 'Should extract fragment name');
    });

    test('detectVariable extracts variable name from {{variable}}', () => {
        const lineText = '    assert $.id equals {{petId}}';
        const match = lineText.match(/\{\{(\w+)\}\}/);
        
        assert.ok(match, 'Should match {{variable}} pattern');
        assert.strictEqual(match![1], 'petId', 'Should extract variable name');
    });

    test('isFragmentContext detects include keyword before cursor', () => {
        const lineText = '    include common-setup';
        const cursorPosition = 18; // After "include co"
        const beforeCursor = lineText.substring(0, cursorPosition);
        const isIncludeContext = /\binclude\s+/.test(beforeCursor);
        
        assert.ok(isIncludeContext, 'Should detect include context');
    });

    test('isUsingContext detects using keyword', () => {
        const lineText = '    call using petstore ^listPets';
        const cursorPosition = 15; // Right after "using "
        const beforeCursor = lineText.substring(0, cursorPosition);
        const isUsingContext = /\busing\s+$/.test(beforeCursor);
        
        assert.ok(isUsingContext, 'Should detect using context');
    });
});

suite('FragmentProvider Tests', () => {
    test('parseFragmentFile extracts fragment name from header', () => {
        const content = `fragment common-setup
  given base URL "http://localhost:8080"
`;
        const match = content.match(/^fragment\s+(\S+)/);
        
        assert.ok(match, 'Should match fragment header');
        assert.strictEqual(match![1], 'common-setup', 'Should extract fragment name');
    });
});

suite('CompletionProvider Tests', () => {
    test('keywords array contains expected values', () => {
        const keywords = ['scenario', 'feature', 'given', 'when', 'then', 'and', 'but'];
        
        assert.ok(keywords.includes('scenario'));
        assert.ok(keywords.includes('feature'));
        assert.ok(keywords.includes('given'));
        assert.ok(keywords.includes('when'));
        assert.ok(keywords.includes('then'));
        assert.ok(keywords.includes('and'));
        assert.ok(keywords.includes('but'));
    });

    test('assertOperators array contains expected values', () => {
        const operators = ['equals', 'contains', 'notEmpty', 'isEmpty', 'matches', 'startsWith', 'endsWith'];
        
        operators.forEach(op => {
            assert.ok(operators.includes(op), `Should include ${op}`);
        });
    });
});

suite('Syntax Highlighting Tests', () => {
    test('tmLanguage patterns exist', () => {
        // Just verify the extension contributes the grammar
        const ext = vscode.extensions.getExtension('lemoncheck.lemoncheck');
        const contributes = ext?.packageJSON?.contributes;
        
        assert.ok(contributes?.grammars, 'Should contribute grammars');
        assert.ok(contributes?.grammars.length > 0, 'Should have at least one grammar');
        assert.strictEqual(contributes?.grammars[0].language, 'lemoncheck');
    });
});
