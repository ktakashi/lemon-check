import * as vscode from 'vscode';
import { OpenApiProvider, OpenApiOperation } from './openapi-provider';
import { FragmentProvider, Fragment } from './fragment-provider';

export class ScenarioHoverProvider implements vscode.HoverProvider {
    constructor(
        private openApiProvider: OpenApiProvider,
        private fragmentProvider: FragmentProvider
    ) {}

    provideHover(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): vscode.ProviderResult<vscode.Hover> {
        const wordRange = document.getWordRangeAtPosition(position, /[\w\-\^]+/);
        if (!wordRange) {
            return null;
        }

        const word = document.getText(wordRange);
        const lineText = document.lineAt(position).text;

        // Check for operation ID (^operationId)
        if (word.startsWith('^') || this.isOperationIdContext(lineText, position.character)) {
            const operationId = word.startsWith('^') ? word.substring(1) : word;
            return this.getOperationHover(operationId);
        }

        // Check for fragment reference (include fragmentName)
        if (this.isFragmentContext(lineText, position.character)) {
            return this.getFragmentHover(word);
        }

        // Check for keyword hover
        const keywordHover = this.getKeywordHover(word);
        if (keywordHover) {
            return keywordHover;
        }

        // Check for assertion operator hover
        const operatorHover = this.getOperatorHover(word, lineText);
        if (operatorHover) {
            return operatorHover;
        }

        return null;
    }

    private isOperationIdContext(lineText: string, charPosition: number): boolean {
        const beforeCursor = lineText.substring(0, charPosition);
        return /call\s+(?:using\s+\w+\s+)?\^/.test(beforeCursor);
    }

    private isFragmentContext(lineText: string, charPosition: number): boolean {
        const beforeCursor = lineText.substring(0, charPosition);
        return /\binclude\s+/.test(beforeCursor);
    }

    private getOperationHover(operationId: string): vscode.Hover | null {
        const operation = this.openApiProvider.getOperation(operationId);
        if (!operation) {
            return new vscode.Hover(
                new vscode.MarkdownString(`⚠️ **Unknown operation:** \`${operationId}\`\n\nNo matching operationId found in OpenAPI specs.`)
            );
        }

        return new vscode.Hover(new vscode.MarkdownString(this.formatOperationHover(operation)));
    }

    private formatOperationHover(op: OpenApiOperation): string {
        let content = `### ${op.operationId}\n\n`;
        content += `**${op.method}** \`${op.path}\`\n\n`;

        if (op.summary) {
            content += `${op.summary}\n\n`;
        }

        if (op.description) {
            content += `${op.description}\n\n`;
        }

        if (op.parameters && op.parameters.length > 0) {
            content += '**Parameters:**\n\n';
            content += '| Name | In | Required | Description |\n';
            content += '|------|-----|----------|-------------|\n';
            for (const param of op.parameters) {
                const required = param.required ? '✓' : '';
                const desc = param.description || '';
                content += `| \`${param.name}\` | ${param.in} | ${required} | ${desc} |\n`;
            }
            content += '\n';
        }

        if (op.requestBody) {
            content += '**Request Body:** ';
            content += op.requestBody.required ? '*required*' : '*optional*';
            if (op.requestBody.description) {
                content += `\n\n${op.requestBody.description}`;
            }
            content += '\n\n';
        }

        if (op.responses) {
            content += '**Responses:**\n\n';
            for (const [code, response] of Object.entries(op.responses)) {
                content += `- **${code}**: ${response.description || 'No description'}\n`;
            }
            content += '\n';
        }

        if (op.tags && op.tags.length > 0) {
            content += `**Tags:** ${op.tags.map(t => `\`${t}\``).join(', ')}\n\n`;
        }

        if (op.specName) {
            content += `*From spec: ${op.specName}*`;
        }

        return content;
    }

    private getFragmentHover(fragmentName: string): vscode.Hover | null {
        const fragment = this.fragmentProvider.getFragment(fragmentName);
        if (!fragment) {
            return new vscode.Hover(
                new vscode.MarkdownString(`⚠️ **Unknown fragment:** \`${fragmentName}\`\n\nNo matching fragment file found.`)
            );
        }

        let content = `### Fragment: ${fragment.name}\n\n`;
        content += `**File:** \`${fragment.filePath}\`\n\n`;
        content += '**Steps:**\n\n```lemoncheck\n';
        content += fragment.steps.slice(0, 10).join('\n');
        if (fragment.steps.length > 10) {
            content += `\n... and ${fragment.steps.length - 10} more steps`;
        }
        content += '\n```';

        return new vscode.Hover(new vscode.MarkdownString(content));
    }

    private getKeywordHover(word: string): vscode.Hover | null {
        const keywords: Record<string, string> = {
            'scenario': '**scenario:** Defines a test scenario.\n\n```lemoncheck\nscenario: My test\n  when: I do something\n    call ^operation\n```',
            'outline': '**outline:** Defines a parameterized scenario with examples.\n\n```lemoncheck\noutline: Test with data\n  when: I call with {{param}}\n    call ^operation\n  examples:\n    | param |\n    | value1 |\n```',
            'feature': '**feature:** Groups related scenarios with optional background.\n\n```lemoncheck\nfeature: My Feature\n  background:\n    given: setup\n      call ^setup\n  scenario: test 1\n    ...\n```',
            'fragment': '**fragment:** Defines a reusable step sequence.\n\n```lemoncheck\nfragment: authenticate\n  given: login\n    call ^login\n    extract $.token => authToken\n```',
            'background': '**background:** Shared setup steps that run before each scenario in a feature.',
            'given': '**given:** Precondition setup step.',
            'when': '**when:** Action step - the behavior being tested.',
            'then': '**then:** Outcome verification step.',
            'and': '**and:** Continuation of the previous step type.',
            'but': '**but:** Exception or negative case step.',
            'call': '**call:** Invoke an OpenAPI operation.\n\n```lemoncheck\ncall ^operationId\ncall using specName ^operationId\n```',
            'assert': '**assert:** Verify response conditions.\n\n```lemoncheck\nassert status 200\nassert $.name equals "value"\nassert contains "text"\nassert not contains "error"\n```',
            'extract': '**extract:** Capture value for later use.\n\n```lemoncheck\nextract $.id => petId\n```',
            'include': '**include:** Include a fragment.\n\n```lemoncheck\ninclude authenticate\n```',
            'examples': '**examples:** Data table for parameterized scenarios.\n\n```lemoncheck\nexamples:\n  | name | value |\n  | test | 123 |\n```',
            'parameters': '**parameters:** File-level configuration.\n\n```lemoncheck\nparameters:\n  baseUrl: "http://localhost:8080"\n  timeout: 60\n```'
        };

        const lowerWord = word.toLowerCase().replace(':', '');
        if (keywords[lowerWord]) {
            return new vscode.Hover(new vscode.MarkdownString(keywords[lowerWord]));
        }

        return null;
    }

    private getOperatorHover(word: string, lineText: string): vscode.Hover | null {
        // Only show operator hover if we're in an assert context
        if (!lineText.includes('assert')) {
            return null;
        }

        const operators: Record<string, string> = {
            'equals': '**equals:** Exact equality comparison.\n\n```lemoncheck\nassert $.name equals "Max"\nassert $.count equals 5\n```',
            'not': '**not:** Negates the following assertion.\n\n```lemoncheck\nassert $.status not equals "sold"\nassert not contains "error"\n```',
            'exists': '**exists:** Check field exists (not null).\n\n```lemoncheck\nassert $.id exists\n```',
            'notEmpty': '**notEmpty:** Check array/string is not empty.\n\n```lemoncheck\nassert $.items notEmpty\n```',
            'hasSize': '**hasSize:** Check array/string length.\n\n```lemoncheck\nassert $.items hasSize 5\n```',
            'size': '**size:** Check array/string length (alias for hasSize).\n\n```lemoncheck\nassert $.items size 5\n```',
            'greaterThan': '**greaterThan:** Numeric comparison.\n\n```lemoncheck\nassert $.price greaterThan 0\n```',
            'lessThan': '**lessThan:** Numeric comparison.\n\n```lemoncheck\nassert $.age lessThan 100\n```',
            'contains': '**contains:** Check body/array contains value.\n\n```lemoncheck\nassert contains "success"\nassert $.tags contains "urgent"\n```',
            'in': '**in:** Check value is in list.\n\n```lemoncheck\nassert $.status in ["available", "pending"]\n```',
            'matches': '**matches:** Regex pattern match.\n\n```lemoncheck\nassert $.email matches ".*@.*\\\\.com"\n```',
            'status': '**status:** Check HTTP status code.\n\n```lemoncheck\nassert status 200\nassert status 2xx\nassert status 201-204\n```',
            'schema': '**schema:** Validate response against OpenAPI schema.\n\n```lemoncheck\nassert schema\n```',
            'header': '**header:** Check HTTP response header.\n\n```lemoncheck\nassert header Content-Type\nassert header Content-Type = "application/json"\n```',
            'responseTime': '**responseTime:** Check response time in ms.\n\n```lemoncheck\nassert responseTime 5000\n```'
        };

        if (operators[word]) {
            return new vscode.Hover(new vscode.MarkdownString(operators[word]));
        }

        return null;
    }
}
