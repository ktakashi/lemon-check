package io.github.ktakashi.lemoncheck.openapi

import io.github.ktakashi.lemoncheck.exception.OperationNotFoundException
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem

/**
 * Resolves OpenAPI operation IDs to path and method information.
 */
class OperationResolver(
    private val openApi: OpenAPI,
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

    private fun buildOperationIndex(): Map<String, ResolvedOperation> {
        val index = mutableMapOf<String, ResolvedOperation>()

        openApi.paths?.forEach { (path, pathItem) ->
            addOperations(index, path, pathItem)
        }

        return index
    }

    private fun addOperations(
        index: MutableMap<String, ResolvedOperation>,
        path: String,
        pathItem: PathItem,
    ) {
        pathItem.get?.let { addOperation(index, path, HttpMethod.GET, it) }
        pathItem.post?.let { addOperation(index, path, HttpMethod.POST, it) }
        pathItem.put?.let { addOperation(index, path, HttpMethod.PUT, it) }
        pathItem.delete?.let { addOperation(index, path, HttpMethod.DELETE, it) }
        pathItem.patch?.let { addOperation(index, path, HttpMethod.PATCH, it) }
        pathItem.head?.let { addOperation(index, path, HttpMethod.HEAD, it) }
        pathItem.options?.let { addOperation(index, path, HttpMethod.OPTIONS, it) }
    }

    private fun addOperation(
        index: MutableMap<String, ResolvedOperation>,
        path: String,
        method: HttpMethod,
        operation: Operation,
    ) {
        operation.operationId?.let { id ->
            index[id] =
                ResolvedOperation(
                    operationId = id,
                    path = path,
                    method = method,
                    operation = operation,
                )
        }
    }
}

/**
 * A resolved OpenAPI operation with path and method information.
 */
data class ResolvedOperation(
    val operationId: String,
    val path: String,
    val method: HttpMethod,
    val operation: Operation,
)

/**
 * HTTP methods supported by OpenAPI.
 */
enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS,
}
