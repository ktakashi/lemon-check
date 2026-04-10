import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';

export interface Fragment {
    name: string;
    filePath: string;
    steps: string[];
    location: vscode.Location;
}

export class FragmentProvider {
    private fragments: Map<string, Fragment> = new Map();

    constructor() {}

    async refresh(): Promise<void> {
        this.fragments.clear();

        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (!workspaceFolder) {
            return;
        }

        const config = vscode.workspace.getConfiguration('lemoncheck');
        const fragmentsPath = config.get<string>('fragmentsPath') || 'src/test/resources';

        await this.searchForFragments(workspaceFolder.uri.fsPath, fragmentsPath);
    }

    private async searchForFragments(workspaceRoot: string, searchPath: string): Promise<void> {
        // Search for .fragment files (excluding build directories)
        const files = await vscode.workspace.findFiles(
            '**/*.fragment',
            '{**/node_modules/**,**/build/**,**/target/**,**/out/**,**/.gradle/**}'
        );

        for (const file of files) {
            await this.loadFragmentFile(file.fsPath);
        }
    }

    private async loadFragmentFile(filePath: string): Promise<void> {
        try {
            const content = fs.readFileSync(filePath, 'utf-8');
            const lines = content.split('\n');

            let currentFragment: Fragment | null = null;
            let fragmentStartLine = 0;

            for (let i = 0; i < lines.length; i++) {
                const line = lines[i];
                const trimmed = line.trim();

                // Check for fragment definition
                const fragmentMatch = trimmed.match(/^fragment:\s*(.+)$/);
                if (fragmentMatch) {
                    // Save previous fragment if exists
                    if (currentFragment) {
                        this.fragments.set(currentFragment.name, currentFragment);
                    }

                    const fragmentName = fragmentMatch[1].trim();
                    fragmentStartLine = i;
                    currentFragment = {
                        name: fragmentName,
                        filePath,
                        steps: [],
                        location: new vscode.Location(
                            vscode.Uri.file(filePath),
                            new vscode.Position(i, 0)
                        )
                    };
                } else if (currentFragment && trimmed.length > 0 && !trimmed.startsWith('#')) {
                    // Add step to current fragment
                    currentFragment.steps.push(trimmed);
                }
            }

            // Save last fragment
            if (currentFragment) {
                this.fragments.set(currentFragment.name, currentFragment);
            }

            console.log(`Loaded ${this.fragments.size} fragments from ${filePath}`);
        } catch (error) {
            console.error(`Failed to load fragment file ${filePath}:`, error);
        }
    }

    getFragment(name: string): Fragment | undefined {
        return this.fragments.get(name);
    }

    getAllFragments(): Fragment[] {
        return Array.from(this.fragments.values());
    }

    getFragmentNames(): string[] {
        return Array.from(this.fragments.keys());
    }
}
