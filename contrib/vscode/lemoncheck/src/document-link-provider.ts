import * as vscode from 'vscode';
import { OpenApiProvider } from './openapi-provider';
import { FragmentProvider } from './fragment-provider';

/**
 * DocumentLinkProvider creates clickable links for operations and fragments
 * that show as underlined text in the editor.
 */
export class ScenarioDocumentLinkProvider implements vscode.DocumentLinkProvider {
    constructor(
        private openApiProvider: OpenApiProvider,
        private fragmentProvider: FragmentProvider
    ) {}

    provideDocumentLinks(
        document: vscode.TextDocument,
        token: vscode.CancellationToken
    ): vscode.ProviderResult<vscode.DocumentLink[]> {
        const links: vscode.DocumentLink[] = [];
        const text = document.getText();
        const lines = text.split('\n');

        for (let lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            const line = lines[lineIndex];

            // Find operation IDs (call ^operationId)
            const callMatches = line.matchAll(/call\s+(?:using\s+\w+\s+)?\^(\w+)/g);
            for (const match of callMatches) {
                const operationId = match[1];
                const operation = this.openApiProvider.getOperation(operationId);
                
                if (operation?.location) {
                    const startChar = match.index! + match[0].indexOf('^');
                    const endChar = startChar + operationId.length + 1; // +1 for ^
                    
                    const range = new vscode.Range(
                        new vscode.Position(lineIndex, startChar),
                        new vscode.Position(lineIndex, endChar)
                    );
                    
                    const link = new vscode.DocumentLink(range, operation.location.uri);
                    link.tooltip = `Go to ${operationId} (${operation.method} ${operation.path})`;
                    links.push(link);
                }
            }

            // Find fragment includes (include fragmentName)
            const includeMatches = line.matchAll(/\binclude\s+(\w[\w-]*)/g);
            for (const match of includeMatches) {
                const fragmentName = match[1];
                const fragment = this.fragmentProvider.getFragment(fragmentName);
                
                if (fragment) {
                    const startChar = match.index! + match[0].indexOf(fragmentName);
                    const endChar = startChar + fragmentName.length;
                    
                    const range = new vscode.Range(
                        new vscode.Position(lineIndex, startChar),
                        new vscode.Position(lineIndex, endChar)
                    );
                    
                    const link = new vscode.DocumentLink(range, fragment.location.uri);
                    link.tooltip = `Go to fragment: ${fragmentName}`;
                    links.push(link);
                }
            }
        }

        return links;
    }
}
