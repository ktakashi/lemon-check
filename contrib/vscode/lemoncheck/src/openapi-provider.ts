import * as vscode from 'vscode';
import * as fs from 'fs';
import * as path from 'path';
import * as yaml from 'yaml';

export interface OpenApiOperation {
    operationId: string;
    method: string;
    path: string;
    summary?: string;
    description?: string;
    parameters?: OpenApiParameter[];
    requestBody?: OpenApiRequestBody;
    responses?: Record<string, OpenApiResponse>;
    tags?: string[];
    specName?: string;
    specFile?: string;
    location?: vscode.Location;
}

export interface OpenApiParameter {
    name: string;
    in: 'path' | 'query' | 'header' | 'cookie';
    required?: boolean;
    description?: string;
    schema?: OpenApiSchema;
}

export interface OpenApiRequestBody {
    required?: boolean;
    description?: string;
    content?: Record<string, { schema?: OpenApiSchema }>;
}

export interface OpenApiResponse {
    description?: string;
    content?: Record<string, { schema?: OpenApiSchema }>;
}

export interface OpenApiSchema {
    type?: string;
    properties?: Record<string, OpenApiSchema>;
    items?: OpenApiSchema;
    $ref?: string;
    example?: unknown;
}

export interface OpenApiSpec {
    name: string;
    filePath: string;
    operations: Map<string, OpenApiOperation>;
    schemas: Map<string, OpenApiSchema>;
}

export class OpenApiProvider {
    private specs: Map<string, OpenApiSpec> = new Map();
    private allOperations: Map<string, OpenApiOperation> = new Map();

    constructor() {}

    async refresh(): Promise<void> {
        this.specs.clear();
        this.allOperations.clear();

        const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
        if (!workspaceFolder) {
            return;
        }

        const config = vscode.workspace.getConfiguration('lemoncheck');
        const singlePath = config.get<string>('openapi.path');
        const multiPaths = config.get<string[]>('openapi.paths') || [];

        // Collect all paths to check
        const pathsToCheck: string[] = [];
        
        if (singlePath) {
            pathsToCheck.push(singlePath);
        }
        
        pathsToCheck.push(...multiPaths);

        // If no paths configured, try to find OpenAPI files automatically
        if (pathsToCheck.length === 0) {
            const foundFiles = await this.findOpenApiFiles(workspaceFolder.uri.fsPath);
            pathsToCheck.push(...foundFiles);
        }

        for (const specPath of pathsToCheck) {
            await this.loadSpec(workspaceFolder.uri.fsPath, specPath);
        }
    }

    private async findOpenApiFiles(workspaceRoot: string): Promise<string[]> {
        const found: string[] = [];
        
        // First, try to find any .yaml/.yml/.json files and check if they're OpenAPI
        // This is the most comprehensive approach
        const allYamlFiles = await vscode.workspace.findFiles(
            '**/*.{yaml,yml,json}',
            '{**/node_modules/**,**/build/**,**/out/**,**/.gradle/**,**/target/**,**/dist/**}',
            100 // Limit to prevent performance issues
        );

        console.log(`LemonCheck: Scanning ${allYamlFiles.length} YAML/JSON files for OpenAPI specs...`);

        for (const file of allYamlFiles) {
            try {
                const isOpenApi = await this.isOpenApiSpec(file.fsPath);
                if (isOpenApi) {
                    const relativePath = path.relative(workspaceRoot, file.fsPath);
                    if (!found.includes(relativePath)) {
                        found.push(relativePath);
                        console.log(`LemonCheck: Found OpenAPI spec: ${relativePath}`);
                    }
                }
            } catch (error) {
                // Skip files that can't be read
            }
        }

        if (found.length === 0) {
            console.log('LemonCheck: No OpenAPI specs found. Configure lemoncheck.openapi.path in settings.');
            vscode.window.showWarningMessage(
                'LemonCheck: No OpenAPI specs found. Configure "lemoncheck.openapi.path" in settings.',
                'Open Settings'
            ).then(selection => {
                if (selection === 'Open Settings') {
                    vscode.commands.executeCommand('workbench.action.openSettings', 'lemoncheck.openapi');
                }
            });
        } else {
            console.log(`LemonCheck: Found ${found.length} OpenAPI spec(s)`);
        }

        return found;
    }

    private async isOpenApiSpec(filePath: string): Promise<boolean> {
        try {
            const content = fs.readFileSync(filePath, 'utf-8');
            const first500Chars = content.substring(0, 500).toLowerCase();
            
            // Check for OpenAPI 3.x markers
            if (first500Chars.includes('openapi:') || first500Chars.includes('"openapi"')) {
                return true;
            }
            
            // Check for Swagger 2.x markers  
            if (first500Chars.includes('swagger:') || first500Chars.includes('"swagger"')) {
                return true;
            }
            
            // Additional check for paths section (common in OpenAPI specs)
            if ((first500Chars.includes('openapi') || first500Chars.includes('swagger')) && 
                content.includes('paths:')) {
                return true;
            }
            
            return false;
        } catch {
            return false;
        }
    }

    private async loadSpec(workspaceRoot: string, specPath: string): Promise<void> {
        const fullPath = path.isAbsolute(specPath) ? specPath : path.join(workspaceRoot, specPath);
        
        if (!fs.existsSync(fullPath)) {
            console.warn(`OpenAPI spec not found: ${fullPath}`);
            return;
        }

        try {
            const content = fs.readFileSync(fullPath, 'utf-8');
            const spec = this.parseSpec(content, fullPath);
            
            if (!spec) {
                return;
            }

            // Use filename (without extension) as spec name
            const specName = path.basename(fullPath, path.extname(fullPath));
            
            const openApiSpec: OpenApiSpec = {
                name: specName,
                filePath: fullPath,
                operations: new Map(),
                schemas: new Map()
            };

            // Extract operations from paths
            if (spec.paths) {
                for (const [pathStr, pathItem] of Object.entries(spec.paths)) {
                    const methods = ['get', 'post', 'put', 'delete', 'patch', 'options', 'head'];
                    
                    for (const method of methods) {
                        const operation = (pathItem as Record<string, unknown>)[method];
                        if (operation && typeof operation === 'object') {
                            const op = operation as Record<string, unknown>;
                            const operationId = op.operationId as string;
                            
                            if (operationId) {
                                const opInfo: OpenApiOperation = {
                                    operationId,
                                    method: method.toUpperCase(),
                                    path: pathStr,
                                    summary: op.summary as string | undefined,
                                    description: op.description as string | undefined,
                                    parameters: op.parameters as OpenApiParameter[] | undefined,
                                    requestBody: op.requestBody as OpenApiRequestBody | undefined,
                                    responses: op.responses as Record<string, OpenApiResponse> | undefined,
                                    tags: op.tags as string[] | undefined,
                                    specName,
                                    specFile: fullPath,
                                    location: this.findOperationLocation(fullPath, operationId)
                                };

                                openApiSpec.operations.set(operationId, opInfo);
                                this.allOperations.set(operationId, opInfo);
                            }
                        }
                    }
                }
            }

            // Extract schemas
            const components = spec.components as Record<string, unknown> | undefined;
            const schemas = components?.schemas || spec.definitions;
            if (schemas) {
                for (const [name, schema] of Object.entries(schemas as Record<string, OpenApiSchema>)) {
                    openApiSpec.schemas.set(name, schema);
                }
            }

            this.specs.set(specName, openApiSpec);
            console.log(`Loaded OpenAPI spec: ${specName} with ${openApiSpec.operations.size} operations`);
        } catch (error) {
            console.error(`Failed to load OpenAPI spec ${fullPath}:`, error);
        }
    }

    private parseSpec(content: string, filePath: string): Record<string, unknown> | null {
        try {
            if (filePath.endsWith('.json')) {
                return JSON.parse(content);
            } else {
                return yaml.parse(content);
            }
        } catch (error) {
            console.error(`Failed to parse spec ${filePath}:`, error);
            return null;
        }
    }

    private findOperationLocation(filePath: string, operationId: string): vscode.Location | undefined {
        try {
            const content = fs.readFileSync(filePath, 'utf-8');
            const lines = content.split('\n');
            
            for (let i = 0; i < lines.length; i++) {
                const line = lines[i];
                // Match operationId: value or "operationId": "value"
                const match = line.match(/operationId['":\s]+['"]?(\w+)['"]?/);
                if (match && match[1] === operationId) {
                    const startPos = new vscode.Position(i, line.indexOf(operationId));
                    const endPos = new vscode.Position(i, line.indexOf(operationId) + operationId.length);
                    return new vscode.Location(vscode.Uri.file(filePath), new vscode.Range(startPos, endPos));
                }
            }
        } catch (error) {
            console.error(`Failed to find operation location for ${operationId}:`, error);
        }
        return undefined;
    }

    getOperation(operationId: string): OpenApiOperation | undefined {
        return this.allOperations.get(operationId);
    }

    getAllOperations(): OpenApiOperation[] {
        return Array.from(this.allOperations.values());
    }

    getAllOperationIds(): string[] {
        return Array.from(this.allOperations.keys());
    }

    getOperationsForSpec(specName: string): OpenApiOperation[] {
        const spec = this.specs.get(specName);
        return spec ? Array.from(spec.operations.values()) : [];
    }

    getSpecNames(): string[] {
        return Array.from(this.specs.keys());
    }

    hasMultipleSpecs(): boolean {
        return this.specs.size > 1;
    }
}
