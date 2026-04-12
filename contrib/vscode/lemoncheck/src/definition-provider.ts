import * as vscode from 'vscode';
import { OpenApiProvider } from './openapi-provider';
import { FragmentProvider } from './fragment-provider';

export class ScenarioDefinitionProvider implements vscode.DefinitionProvider {
    constructor(
        private openApiProvider: OpenApiProvider,
        private fragmentProvider: FragmentProvider
    ) {}

    provideDefinition(
        document: vscode.TextDocument,
        position: vscode.Position,
        token: vscode.CancellationToken
    ): vscode.ProviderResult<vscode.Definition | vscode.LocationLink[]> {
        const lineText = document.lineAt(position).text;
        const charPos = position.character;
        
        // Try to find spec name after "using" keyword
        // Pattern: call using specName ^operationId
        const usingMatch = lineText.match(/call\s+using\s+(\w+)\s+\^/);
        if (usingMatch) {
            const specName = usingMatch[1];
            const usingIndex = lineText.indexOf('using');
            const specStart = lineText.indexOf(specName, usingIndex + 5);
            const specEnd = specStart + specName.length;
            
            // Check if cursor is on the spec name
            if (charPos >= specStart && charPos <= specEnd) {
                return this.getSpecDefinition(specName);
            }
        }
        
        // Try to find operation ID at cursor position
        // Look for ^operationId pattern in the line
        const callMatch = lineText.match(/call\s+(?:using\s+\w+\s+)?\^(\w+)/);
        if (callMatch) {
            const operationId = callMatch[1];
            const caretIndex = lineText.indexOf('^' + operationId);
            const endIndex = caretIndex + operationId.length + 1;
            
            // Check if cursor is on the ^operationId part
            if (charPos >= caretIndex && charPos <= endIndex) {
                return this.getOperationDefinition(operationId);
            }
        }

        // Try standard word range detection for fragments and variables
        const wordRange = document.getWordRangeAtPosition(position, /[\w\-]+/);
        
        if (wordRange) {
            const word = document.getText(wordRange);

            // Check for fragment reference (include fragmentName)
            if (this.isFragmentContext(lineText, charPos)) {
                return this.getFragmentDefinition(word);
            }

            // Check for variable reference ({{varName}} or ${varName})
            const variableMatch = this.getVariableAtPosition(document, position);
            if (variableMatch) {
                return this.getVariableDefinition(document, variableMatch);
            }
        }

        return null;
    }

    private isFragmentContext(lineText: string, charPosition: number): boolean {
        // Check if we're after "include "
        const beforeCursor = lineText.substring(0, charPosition);
        return /\binclude\s+/.test(beforeCursor);
    }

    private getSpecDefinition(specName: string): vscode.Location | null {
        const location = this.openApiProvider.getSpecLocation(specName);
        
        if (location) {
            this.scheduleRevealAtTop(location);
            return location;
        }
        
        // Show warning if spec not found
        const allSpecs = this.openApiProvider.getSpecNames();
        if (allSpecs.length === 0) {
            vscode.window.showWarningMessage(
                `LemonCheck: No OpenAPI specs loaded. Run "LemonCheck: Refresh OpenAPI" command.`
            );
        } else {
            vscode.window.showWarningMessage(
                `LemonCheck: Spec '${specName}' not found. Available: ${allSpecs.join(', ')}`
            );
        }
        
        return null;
    }

    /**
     * Schedule a reveal-at-top after VS Code navigates to the location.
     */
    private scheduleRevealAtTop(location: vscode.Location): void {
        // Use a small delay to let VS Code navigate first
        setTimeout(async () => {
            const editor = vscode.window.activeTextEditor;
            if (editor && editor.document.uri.fsPath === location.uri.fsPath) {
                // If we're in the right file, reveal at top
                editor.revealRange(location.range, vscode.TextEditorRevealType.AtTop);
            }
        }, 100);
    }

    private getOperationDefinition(operationId: string): vscode.Location | null {
        const operation = this.openApiProvider.getOperation(operationId);
        
        if (operation?.location) {
            this.scheduleRevealAtTop(operation.location);
            return operation.location;
        }
        
        // If no exact location, at least go to the spec file
        if (operation?.specFile) {
            const location = new vscode.Location(
                vscode.Uri.file(operation.specFile),
                new vscode.Position(0, 0)
            );
            return location;
        }
        
        // If operation not found, show helpful message
        const allOps = this.openApiProvider.getAllOperationIds();
        
        if (allOps.length === 0) {
            vscode.window.showWarningMessage(
                `LemonCheck: No OpenAPI specs loaded. Run "LemonCheck: Refresh OpenAPI" command.`
            );
        } else {
            vscode.window.showWarningMessage(
                `LemonCheck: Operation '${operationId}' not found. Available: ${allOps.slice(0, 5).join(', ')}${allOps.length > 5 ? '...' : ''}`
            );
        }

        return null;
    }

    private getFragmentDefinition(fragmentName: string): vscode.Location | null {
        const fragment = this.fragmentProvider.getFragment(fragmentName);
        
        if (fragment) {
            this.scheduleRevealAtTop(fragment.location);
            return fragment.location;
        }
        
        // Show warning if fragment not found
        const allFragments = this.fragmentProvider.getFragmentNames();
        if (allFragments.length === 0) {
            vscode.window.showWarningMessage(`LemonCheck: No fragments loaded.`);
        } else {
            vscode.window.showWarningMessage(
                `LemonCheck: Fragment '${fragmentName}' not found. Available: ${allFragments.join(', ')}`
            );
        }
        return null;
    }

    private getVariableAtPosition(
        document: vscode.TextDocument,
        position: vscode.Position
    ): string | null {
        const lineText = document.lineAt(position).text;
        const char = position.character;

        // Check for {{varName}}
        const doublebraceMatch = lineText.match(/\{\{(\w+)\}\}/g);
        if (doublebraceMatch) {
            for (const match of doublebraceMatch) {
                const start = lineText.indexOf(match);
                const end = start + match.length;
                if (char >= start && char <= end) {
                    const varName = match.match(/\{\{(\w+)\}\}/);
                    return varName ? varName[1] : null;
                }
            }
        }

        // Check for ${varName}
        const dollarMatch = lineText.match(/\$\{(\w+)\}/g);
        if (dollarMatch) {
            for (const match of dollarMatch) {
                const start = lineText.indexOf(match);
                const end = start + match.length;
                if (char >= start && char <= end) {
                    const varName = match.match(/\$\{(\w+)\}/);
                    return varName ? varName[1] : null;
                }
            }
        }

        return null;
    }

    private getVariableDefinition(
        document: vscode.TextDocument,
        variableName: string
    ): vscode.Location | null {
        const text = document.getText();
        
        // Look for extract ... => variableName
        const extractRegex = new RegExp(`extract\\s+\\$[\\w\\.\\[\\]]+\\s+=>\\s+(${variableName})\\b`);
        const match = text.match(extractRegex);
        
        if (match) {
            const offset = text.indexOf(match[0]);
            const varOffset = offset + match[0].indexOf(match[1]);
            const position = document.positionAt(varOffset);
            return new vscode.Location(document.uri, position);
        }

        // Look for in examples table header
        const lines = text.split('\n');
        for (let i = 0; i < lines.length; i++) {
            if (lines[i].includes('|') && lines[i].includes(variableName)) {
                // Check if this is a header line (first table row after examples:)
                let isHeader = false;
                for (let j = i - 1; j >= 0; j--) {
                    const prevLine = lines[j].trim();
                    if (prevLine === 'examples:') {
                        isHeader = true;
                        break;
                    }
                    if (prevLine.includes('|')) {
                        break; // This is not the first row
                    }
                }
                
                if (isHeader) {
                    const charIndex = lines[i].indexOf(variableName);
                    return new vscode.Location(
                        document.uri,
                        new vscode.Position(i, charIndex)
                    );
                }
            }
        }

        return null;
    }
}
