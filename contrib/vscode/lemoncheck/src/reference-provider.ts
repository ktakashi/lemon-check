import * as vscode from 'vscode';
import { FragmentProvider } from './fragment-provider';

/**
 * Provides "Find All References" functionality for fragments.
 * When invoked on a fragment definition or usage, shows all locations where the fragment is included.
 */
export class ScenarioReferenceProvider implements vscode.ReferenceProvider {
    constructor(private fragmentProvider: FragmentProvider) {}

    async provideReferences(
        document: vscode.TextDocument,
        position: vscode.Position,
        context: vscode.ReferenceContext,
        token: vscode.CancellationToken
    ): Promise<vscode.Location[] | null> {
        const lineText = document.lineAt(position).text;
        const wordRange = document.getWordRangeAtPosition(position, /[\w\-]+/);
        
        if (!wordRange) {
            return null;
        }
        
        const word = document.getText(wordRange);
        
        // Check if we're on a fragment definition (fragment: name)
        const fragmentDefMatch = lineText.match(/^fragment:\s*(\w+)/);
        if (fragmentDefMatch && fragmentDefMatch[1] === word) {
            return this.findFragmentUsages(word, context.includeDeclaration);
        }
        
        // Check if we're on a fragment reference (include fragmentName)
        const includeMatch = lineText.match(/\binclude\s+(\w+)/);
        if (includeMatch && includeMatch[1] === word) {
            return this.findFragmentUsages(word, context.includeDeclaration);
        }
        
        // Also check if word matches any known fragment
        const fragment = this.fragmentProvider.getFragment(word);
        if (fragment) {
            return this.findFragmentUsages(word, context.includeDeclaration);
        }
        
        return null;
    }

    private async findFragmentUsages(fragmentName: string, includeDeclaration: boolean): Promise<vscode.Location[]> {
        const locations: vscode.Location[] = [];
        
        // Include the fragment definition if requested
        if (includeDeclaration) {
            const fragment = this.fragmentProvider.getFragment(fragmentName);
            if (fragment) {
                locations.push(fragment.location);
            }
        }
        
        // Search all .scenario files for usages (excluding build directories)
        const scenarioFiles = await vscode.workspace.findFiles(
            '**/*.scenario',
            '{**/node_modules/**,**/build/**,**/target/**,**/out/**,**/.gradle/**}'
        );
        
        const includePattern = new RegExp(`\\binclude\\s+${fragmentName}\\b`);
        
        for (const file of scenarioFiles) {
            try {
                const doc = await vscode.workspace.openTextDocument(file);
                const text = doc.getText();
                const lines = text.split('\n');
                
                for (let i = 0; i < lines.length; i++) {
                    const line = lines[i];
                    const match = line.match(includePattern);
                    if (match) {
                        const startCol = line.indexOf('include') + 'include '.length;
                        const endCol = startCol + fragmentName.length;
                        locations.push(new vscode.Location(
                            file,
                            new vscode.Range(
                                new vscode.Position(i, startCol),
                                new vscode.Position(i, endCol)
                            )
                        ));
                    }
                }
            } catch (error) {
                // Skip files that can't be read
            }
        }
        
        return locations;
    }
}
