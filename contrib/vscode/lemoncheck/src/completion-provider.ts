import * as vscode from 'vscode';
import { OpenApiProvider, OpenApiOperation } from './openapi-provider';
import { FragmentProvider, Fragment } from './fragment-provider';

export class ScenarioCompletionProvider implements vscode.CompletionItemProvider {
    constructor(
        private openApiProvider: OpenApiProvider,
        private fragmentProvider: FragmentProvider
    ) {}

    provideCompletionItems(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken,
        context: vscode.CompletionContext
    ): vscode.CompletionItem[] | vscode.CompletionList {
        const lineText = document.lineAt(position).text;
        const linePrefix = lineText.substring(0, position.character);
        const trimmedLine = lineText.trim();

        // Operation ID completions (after call ^)
        if (linePrefix.includes('call') && linePrefix.includes('^')) {
            return this.getOperationCompletions(linePrefix);
        }

        // Spec name completions (after call using)
        if (linePrefix.match(/call\s+using\s+$/)) {
            return this.getSpecNameCompletions();
        }

        // Include fragment completions
        if (trimmedLine.startsWith('include ') || linePrefix.match(/\binclude\s+$/)) {
            return this.getFragmentCompletions();
        }

        // Variable interpolation completions (after {{ or ${)
        if (linePrefix.match(/\{\{[^}]*$/) || linePrefix.match(/\$\{[^}]*$/)) {
            return this.getVariableCompletions(document, position);
        }

        // Tag completions (after @)
        if (linePrefix.match(/@\w*$/)) {
            return this.getTagCompletions();
        }

        // Keyword completions at line start
        if (this.isLineStart(linePrefix)) {
            return this.getKeywordCompletions(document, position);
        }

        // Directive completions (after step keywords)
        if (this.isInsideStep(document, position)) {
            return this.getDirectiveCompletions(linePrefix);
        }

        // Assert operator completions
        if (linePrefix.match(/assert\s+\$[\w\.\[\]]+\s+$/)) {
            return this.getAssertOperatorCompletions();
        }

        // Parameter completions (for call parameters)
        if (this.isInsideCall(document, position)) {
            return this.getParameterCompletions(document, position);
        }

        return [];
    }

    private getOperationCompletions(linePrefix: string): vscode.CompletionItem[] {
        const items: vscode.CompletionItem[] = [];
        
        // Check if we're in a named spec context (call using specName ^)
        const usingMatch = linePrefix.match(/call\s+using\s+(\w+)\s+\^/);
        const operations = usingMatch 
            ? this.openApiProvider.getOperationsForSpec(usingMatch[1])
            : this.openApiProvider.getAllOperations();

        for (const op of operations) {
            const item = new vscode.CompletionItem(
                op.operationId,
                vscode.CompletionItemKind.Method
            );
            
            item.detail = `${op.method} ${op.path}`;
            item.documentation = new vscode.MarkdownString(
                this.formatOperationDocs(op)
            );
            
            // Insert just the operationId (^ is already typed)
            item.insertText = op.operationId;
            
            items.push(item);
        }

        return items;
    }

    private getSpecNameCompletions(): vscode.CompletionItem[] {
        return this.openApiProvider.getSpecNames().map(name => {
            const item = new vscode.CompletionItem(name, vscode.CompletionItemKind.Module);
            item.detail = 'OpenAPI spec';
            return item;
        });
    }

    private getFragmentCompletions(): vscode.CompletionItem[] {
        return this.fragmentProvider.getAllFragments().map(fragment => {
            const item = new vscode.CompletionItem(
                fragment.name,
                vscode.CompletionItemKind.Snippet
            );
            item.detail = `Fragment from ${fragment.filePath}`;
            item.documentation = new vscode.MarkdownString(
                `**Steps:**\n\`\`\`\n${fragment.steps.slice(0, 5).join('\n')}\n\`\`\``
            );
            return item;
        });
    }

    private getVariableCompletions(
        document: vscode.TextDocument,
        position: vscode.Position
    ): vscode.CompletionItem[] {
        const items: vscode.CompletionItem[] = [];
        const text = document.getText();
        
        // Find all extracted variables in the document
        const extractMatches = text.matchAll(/extract\s+\$[\w\.\[\]]+\s+=>\s+(\w+)/g);
        const variables = new Set<string>();
        
        for (const match of extractMatches) {
            variables.add(match[1]);
        }

        // Find variables from examples table headers
        const exampleHeaders = text.matchAll(/\|\s*([^|]+)/g);
        for (const match of exampleHeaders) {
            const header = match[1].trim();
            if (header && !header.match(/^\|/) && header !== 'examples:') {
                variables.add(header);
            }
        }

        for (const varName of variables) {
            const item = new vscode.CompletionItem(varName, vscode.CompletionItemKind.Variable);
            item.detail = 'Extracted variable';
            items.push(item);
        }

        return items;
    }

    private getTagCompletions(): vscode.CompletionItem[] {
        const builtInTags = [
            { name: 'ignore', description: 'Skip this scenario during execution' },
            { name: 'wip', description: 'Work in progress' },
            { name: 'slow', description: 'Marks slow-running tests' },
            { name: 'smoke', description: 'Smoke tests' },
            { name: 'regression', description: 'Regression tests' },
            { name: 'api', description: 'API tests' },
            { name: 'critical', description: 'Critical path tests' }
        ];

        return builtInTags.map(tag => {
            const item = new vscode.CompletionItem(tag.name, vscode.CompletionItemKind.Keyword);
            item.detail = tag.description;
            item.insertText = tag.name; // @ is already typed
            return item;
        });
    }

    private getKeywordCompletions(
        document: vscode.TextDocument,
        position: vscode.Position
    ): vscode.CompletionItem[] {
        const items: vscode.CompletionItem[] = [];
        const indent = this.getIndentation(document, position);

        if (indent === 0) {
            // Top-level keywords
            items.push(this.createKeywordItem('scenario:', 'Define a test scenario', 'scenario: ${1:Scenario name}\n  '));
            items.push(this.createKeywordItem('outline:', 'Define a parameterized scenario', 'outline: ${1:Outline name}\n  '));
            items.push(this.createKeywordItem('feature:', 'Define a feature group', 'feature: ${1:Feature name}\n  '));
            items.push(this.createKeywordItem('fragment:', 'Define a reusable fragment', 'fragment: ${1:Fragment name}\n  '));
            items.push(this.createKeywordItem('parameters:', 'Define file-level parameters', 'parameters:\n  '));
        } else {
            // Step keywords
            items.push(this.createKeywordItem('given:', 'Precondition setup', 'given: ${1:description}\n    '));
            items.push(this.createKeywordItem('when:', 'Action to perform', 'when: ${1:description}\n    '));
            items.push(this.createKeywordItem('then:', 'Expected outcome', 'then: ${1:description}\n    '));
            items.push(this.createKeywordItem('and:', 'Continuation', 'and: ${1:description}\n    '));
            items.push(this.createKeywordItem('but:', 'Exception/negative case', 'but: ${1:description}\n    '));
            items.push(this.createKeywordItem('background:', 'Shared setup for feature', 'background:\n    '));
            items.push(this.createKeywordItem('examples:', 'Data table for outline', 'examples:\n    | ${1:column1} | ${2:column2} |\n    | ${3:value1}  | ${4:value2}  |'));
        }

        return items;
    }

    private getDirectiveCompletions(linePrefix: string): vscode.CompletionItem[] {
        const items: vscode.CompletionItem[] = [];

        items.push(this.createDirectiveItem('call', 'Call an OpenAPI operation', 'call ^${1:operationId}'));
        items.push(this.createDirectiveItem('assert status', 'Assert HTTP status code', 'assert status ${1:200}'));
        items.push(this.createDirectiveItem('assert contains', 'Assert body contains text', 'assert contains "${1:text}"'));
        items.push(this.createDirectiveItem('assert not contains', 'Assert body does not contain text', 'assert not contains "${1:text}"'));
        items.push(this.createDirectiveItem('assert schema', 'Validate against OpenAPI schema', 'assert schema'));
        items.push(this.createDirectiveItem('assert $', 'Assert JSONPath value', 'assert \\$.${1:path} ${2|equals,exists,notEmpty,contains,hasSize,greaterThan,lessThan,matches,in|} ${3:value}'));
        items.push(this.createDirectiveItem('extract', 'Extract value to variable', 'extract \\$.${1:path} => ${2:varName}'));
        items.push(this.createDirectiveItem('include', 'Include a fragment', 'include ${1:fragmentName}'));
        items.push(this.createDirectiveItem('body:', 'Set request body', 'body: ${1:{"key": "value"}}'));
        items.push(this.createDirectiveItem('bodyFile:', 'Load body from file', 'bodyFile: "${1:classpath:templates/body.json}"'));
        items.push(this.createDirectiveItem('header_', 'Set HTTP header', 'header_${1:Authorization}: "${2:Bearer token}"'));

        return items;
    }

    private getAssertOperatorCompletions(): vscode.CompletionItem[] {
        const operators = [
            { name: 'equals', desc: 'Exact equality', snippet: 'equals ${1:value}' },
            { name: 'not equals', desc: 'Not equal', snippet: 'not equals ${1:value}' },
            { name: 'exists', desc: 'Field exists', snippet: 'exists' },
            { name: 'not exists', desc: 'Field does not exist', snippet: 'not exists' },
            { name: 'notEmpty', desc: 'Not empty array/string', snippet: 'notEmpty' },
            { name: 'hasSize', desc: 'Array/string length', snippet: 'hasSize ${1:count}' },
            { name: 'greaterThan', desc: 'Numeric greater than', snippet: 'greaterThan ${1:value}' },
            { name: 'lessThan', desc: 'Numeric less than', snippet: 'lessThan ${1:value}' },
            { name: 'contains', desc: 'Contains value', snippet: 'contains ${1:value}' },
            { name: 'not contains', desc: 'Does not contain', snippet: 'not contains ${1:value}' },
            { name: 'in', desc: 'Value in list', snippet: 'in [${1:values}]' },
            { name: 'matches', desc: 'Regex match', snippet: 'matches "${1:pattern}"' }
        ];

        return operators.map(op => {
            const item = new vscode.CompletionItem(op.name, vscode.CompletionItemKind.Operator);
            item.detail = op.desc;
            item.insertText = new vscode.SnippetString(op.snippet);
            return item;
        });
    }

    private getParameterCompletions(
        document: vscode.TextDocument,
        position: vscode.Position
    ): vscode.CompletionItem[] {
        const items: vscode.CompletionItem[] = [];
        
        // Find the current call operation
        const operationId = this.findCurrentOperationId(document, position);
        if (!operationId) {
            return items;
        }

        const operation = this.openApiProvider.getOperation(operationId);
        if (!operation || !operation.parameters) {
            return items;
        }

        for (const param of operation.parameters) {
            const item = new vscode.CompletionItem(
                param.name,
                param.in === 'header' ? vscode.CompletionItemKind.Property : vscode.CompletionItemKind.Field
            );
            
            item.detail = `${param.in} parameter${param.required ? ' (required)' : ''}`;
            item.documentation = param.description;
            
            if (param.in === 'header') {
                item.insertText = new vscode.SnippetString(`header_${param.name}: "\${1:value}"`);
            } else {
                item.insertText = new vscode.SnippetString(`${param.name}: \${1:value}`);
            }
            
            items.push(item);
        }

        // Add body and bodyFile if operation has request body
        if (operation.requestBody) {
            items.push(this.createDirectiveItem('body:', 'Set request body', 'body: ${1:{"key": "value"}}'));
            items.push(this.createDirectiveItem('bodyFile:', 'Load body from file', 'bodyFile: "${1:classpath:templates/body.json}"'));
        }

        return items;
    }

    private formatOperationDocs(op: OpenApiOperation): string {
        let docs = `**${op.method} ${op.path}**\n\n`;
        
        if (op.summary) {
            docs += `${op.summary}\n\n`;
        }
        
        if (op.description) {
            docs += `${op.description}\n\n`;
        }

        if (op.parameters && op.parameters.length > 0) {
            docs += '**Parameters:**\n';
            for (const param of op.parameters) {
                const required = param.required ? '*(required)*' : '';
                docs += `- \`${param.name}\` (${param.in}) ${required}`;
                if (param.description) {
                    docs += `: ${param.description}`;
                }
                docs += '\n';
            }
            docs += '\n';
        }

        if (op.requestBody) {
            docs += '**Request Body:** ';
            docs += op.requestBody.required ? '*(required)*' : '*(optional)*';
            if (op.requestBody.description) {
                docs += `\n${op.requestBody.description}`;
            }
            docs += '\n\n';
        }

        if (op.tags && op.tags.length > 0) {
            docs += `**Tags:** ${op.tags.join(', ')}\n`;
        }

        return docs;
    }

    private createKeywordItem(label: string, detail: string, snippet: string): vscode.CompletionItem {
        const item = new vscode.CompletionItem(label, vscode.CompletionItemKind.Keyword);
        item.detail = detail;
        item.insertText = new vscode.SnippetString(snippet);
        return item;
    }

    private createDirectiveItem(label: string, detail: string, snippet: string): vscode.CompletionItem {
        const item = new vscode.CompletionItem(label, vscode.CompletionItemKind.Function);
        item.detail = detail;
        item.insertText = new vscode.SnippetString(snippet);
        return item;
    }

    private isLineStart(linePrefix: string): boolean {
        return linePrefix.trim().length === 0 || linePrefix.match(/^\s*$/) !== null;
    }

    private isInsideStep(document: vscode.TextDocument, position: vscode.Position): boolean {
        // Look backwards for a step keyword
        for (let i = position.line; i >= 0; i--) {
            const line = document.lineAt(i).text.trim();
            if (line.match(/^(given|when|then|and|but):/i)) {
                return true;
            }
            if (line.match(/^(scenario|outline|feature|fragment|background|parameters):/i)) {
                return false;
            }
        }
        return false;
    }

    private isInsideCall(document: vscode.TextDocument, position: vscode.Position): boolean {
        // Look backwards for a call directive
        for (let i = position.line; i >= Math.max(0, position.line - 10); i--) {
            const line = document.lineAt(i).text.trim();
            if (line.startsWith('call ')) {
                return true;
            }
            if (line.match(/^(given|when|then|and|but|assert|extract|include):/i)) {
                return false;
            }
        }
        return false;
    }

    private findCurrentOperationId(document: vscode.TextDocument, position: vscode.Position): string | null {
        for (let i = position.line; i >= Math.max(0, position.line - 10); i--) {
            const line = document.lineAt(i).text;
            const match = line.match(/call\s+(?:using\s+\w+\s+)?\^(\w+)/);
            if (match) {
                return match[1];
            }
        }
        return null;
    }

    private getIndentation(document: vscode.TextDocument, position: vscode.Position): number {
        const line = document.lineAt(position).text;
        const match = line.match(/^(\s*)/);
        return match ? match[1].length : 0;
    }
}
