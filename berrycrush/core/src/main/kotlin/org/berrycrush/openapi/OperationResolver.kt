package org.berrycrush.openapi

import org.berrycrush.exception.OperationNotFoundException

/**
 * Resolves OpenAPI operation IDs to path and method information.
 *
 * This class provides operation resolution for an OpenApiSpec.
 */
class OperationResolver(
    private val spec: OpenApiSpec,
) {
    private val operationIndex: Map<String, ResolvedOperation> by lazy {
        buildOperationIndex()
    }

    /**
     * Resolve an operation ID to its path and HTTP method.
     *
     * @param operationId The OpenAPI operation ID
     * @return Resolved operation with path and method
     * @throws OperationNotFoundException if operation is not found
     */
    fun resolve(operationId: String): ResolvedOperation =
        operationIndex[operationId]
            ?: throw OperationNotFoundException(operationId, operationIndex.keys.toList())

    /**
     * Check if an operation ID exists.
     */
    fun hasOperation(operationId: String): Boolean = operationId in operationIndex

    /**
     * Get all available operation IDs.
     */
    fun allOperationIds(): Set<String> = operationIndex.keys

    private fun buildOperationIndex(): Map<String, ResolvedOperation> =
        spec
            .getAllOperations()
            .filter { it.operationId != null }
            .associate { op ->
                op.operationId!! to
                    ResolvedOperation(
                        operationId = op.operationId!!,
                        path = op.path,
                        method = op.method,
                        operation = op,
                    )
            }
}

/**
 * A resolved OpenAPI operation with path and method information.
 */
data class ResolvedOperation(
    val operationId: String,
    val path: String,
    val method: HttpMethod,
    val operation: OperationSpec,
)

/**
 * Find the response definition for a given status code.
 *
 * Tries exact match first, then wildcard (2XX, 4XX, etc.), then default.
 *
 * @param statusCode The HTTP status code to find the response for
 * @return The ApiResponse definition, or null if not found
 */
fun ResolvedOperation.findResponse(statusCode: Int): ResponseSpec? {
    val responses = operation.responses
    if (responses.isEmpty()) return null

    // Try exact match
    responses[statusCode.toString()]?.let { return it }

    // Try wildcard (2XX, 4XX, etc.)
    val wildcard = "${statusCode / 100}XX"
    responses[wildcard]?.let { return it }

    // Try default
    return responses["default"]
}
