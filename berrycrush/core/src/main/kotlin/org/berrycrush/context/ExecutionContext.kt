package org.berrycrush.context

import com.jayway.jsonpath.JsonPath
import org.berrycrush.webhook.MockWebhookServer
import java.util.concurrent.ConcurrentHashMap

private val mustachePattern = Regex("""\{\{([-\w._\[\]0-9]+)}}""")
private val bracketPattern = Regex("""\$\{([^}]+)}""")
private val simplePattern = Regex("""\$(\w+)""")
private val segmentPattern = Regex("""(\w+)((?:\[\d+])*)""")

/**
 * Execution context that holds variables and state during scenario execution.
 *
 * ## Thread Safety
 *
 * This class is designed for thread-safe usage in parallel execution scenarios:
 * - Variables are stored in a [ConcurrentHashMap]
 * - Mutable state fields use `@Volatile` for visibility
 * - [createIsolatedCopy] creates a fully independent copy for parallel scenarios
 *
 * ## Parallel Execution
 *
 * For parallel scenario execution, use [createIsolatedCopy] to ensure each
 * scenario has its own isolated context:
 *
 * ```kotlin
 * val parallelContext = sharedContext.createIsolatedCopy()
 * executor.execute(scenario, parallelContext)
 * ```
 *
 * For sequential execution with shared variables, use [createChild]:
 *
 * ```kotlin
 * val childContext = parentContext.createChild()
 * ```
 *
 * ## Webhook Support
 *
 * Webhook servers can be registered using [registerWebhookServer] and accessed
 * via variable interpolation using `{{serverName.hookName}}` syntax:
 *
 * ```kotlin
 * context.registerWebhookServer("payments", server)
 * val url = context.interpolate("{{payments.onPaymentReceived}}")
 * // Returns: "http://localhost:8080/webhook/onPaymentReceived"
 * ```
 */
@Suppress("TooManyFunctions")
class ExecutionContext(
    val shareVariablesAcrossScenarios: Boolean = false,
    val parameters: Map<String, Any> = emptyMap(),
    val parent: ExecutionContext? = null,
    private val environmentProvider: (String) -> String? = System::getenv,
) {
    private val variables: MutableMap<String, Any> =
        ConcurrentHashMap<String, Any>().apply {
            parent?.let {
                putAll(it.variables)
            }
        }
    private val webhookServers: MutableMap<String, MockWebhookServer> =
        ConcurrentHashMap<String, MockWebhookServer>().apply {
            parent?.let {
                putAll(it.webhookServers)
            }
        }

    val mergedParameters: Map<String, Any> by lazy {
        val mutableMap = parameters.toMutableMap()
        var context: ExecutionContext? = this.parent
        while (context != null) {
            context.parameters.forEach { (k, v) ->
                if (k == "<<") {
                    mutableMap.compute(k) { _, value ->
                        if (value != null && value is List<*>) {
                            (value + v).distinct()
                        } else {
                            // this shouldn't happen but just in case
                            v
                        }
                    }
                } else {
                    mutableMap.putIfAbsent(k, v)
                }
            }
            context = context.parent
        }
        mutableMap.toMap()
    }

    /**
     * Store a variable value.
     */
    operator fun set(
        name: String,
        value: Any,
    ) {
        variables[name] = value
    }

    /**
     * Get a variable value.
     */
    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(name: String): T? = variables[name] as? T

    /**
     * Get a variable value with a default.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getOrDefault(
        name: String,
        default: T,
    ): T = (variables[name] as? T) ?: default

    /**
     * Check if a variable exists.
     */
    fun contains(name: String): Boolean = variables.containsKey(name)

    /**
     * Get all variable names.
     */
    fun variableNames(): Set<String> = variables.keys.toSet()

    /**
     * Get all variables as a map.
     */
    fun allVariables(): Map<String, Any> = variables

    /**
     * Clear all variables and state.
     * Note: Does NOT stop webhook servers - use [cleanupWebhookServers] for that.
     */
    fun clear() {
        variables.clear()
    }

    // ==================== Webhook Server Management ====================

    /**
     * Register a webhook server with a name.
     * The server can then be referenced in variable interpolation as `{{name.hookName}}`.
     *
     * @param name The name to register the server under
     * @param server The MockWebhookServer instance
     */
    fun registerWebhookServer(
        name: String,
        server: MockWebhookServer,
    ) {
        webhookServers[name] = server
    }

    /**
     * Get a registered webhook server by name.
     *
     * @param name The server name
     * @return The MockWebhookServer or null if not found
     */
    fun getWebhookServer(name: String): MockWebhookServer? = webhookServers[name]

    /**
     * Get all registered webhook server names.
     */
    fun webhookServerNames(): Set<String> = webhookServers.keys.toSet()

    /**
     * Stop and remove a specific webhook server.
     *
     * @param name The server name to remove
     */
    fun removeWebhookServer(name: String) {
        webhookServers.remove(name)?.stop()
    }

    /**
     * Stop and remove all webhook servers.
     */
    fun cleanupWebhookServers() {
        webhookServers.values.forEach { it.stop() }
        webhookServers.clear()
    }

    /**
     * Create a child context that inherits variables from this context.
     * The child shares variable references with the parent.
     * Webhook servers are shared between parent and child.
     * Use [createIsolatedCopy] for fully isolated parallel execution.
     */
    fun createChild(): ExecutionContext = ExecutionContext(shareVariablesAcrossScenarios, parameters, this)

    /**
     * Create a fully isolated copy of this context for parallel execution.
     * All state is copied, ensuring no shared mutable state between copies.
     * Note: Webhook servers are NOT copied - each parallel context should
     * create its own webhook servers to avoid port conflicts.
     *
     * This is the recommended method for parallel scenario execution.
     *
     * @param overwriting a map to overwrite the variable
     */
    fun createIsolatedCopy(overwriting: Map<String, Any>? = null): ExecutionContext {
        val copy = createChild()
        overwriting?.forEach { (key, value) -> copy.variables[key] = value }
        // Note: webhook servers are NOT copied for isolation
        return copy
    }

    /**
     * Resolve a string with variable interpolation.
     *
     * Variables can be referenced using:
     * - Mustache-style: {{variableName}} or {{serverName.hookName}}
     * - Dollar-style: ${variableName} or $variableName
     *
     * Webhook URL interpolation:
     * - {{serverName.hookName}} resolves to the webhook URL for that hook
     * - Example: {{payments.onPaymentReceived}} -> http://localhost:8080/webhook/onPaymentReceived
     *
     * Escaping:
     * - Use \\{{ to produce literal {{
     * - Use \\$ to produce literal $
     */
    fun interpolate(template: String): String {
        // First, temporarily replace escaped sequences with placeholders
        val escapePlaceholder1 = "\u0001ESCAPED_OPEN_BRACE\u0001"
        val escapePlaceholder2 = "\u0001ESCAPED_DOLLAR\u0001"

        var result =
            template
                .replace("\\{{", escapePlaceholder1)
                .replace("\\$", escapePlaceholder2)

        // Replace {{name}}, {{server.hook}}, or {{server.hook[0].body.field}} patterns
        // Matches: word chars, dots, array indices with brackets
        result =
            mustachePattern.replace(result) { match ->
                val varName = match.groupValues[1]
                resolveVariable(varName) ?: match.value
            }

        // Replace ${name} patterns
        result =
            bracketPattern.replace(result) { match ->
                val varName = match.groupValues[1]
                resolveVariable(varName) ?: match.value
            }

        // Replace $name patterns (word boundary)
        result =
            simplePattern.replace(result) { match ->
                val varName = match.groupValues[1]
                variables[varName]?.toString() ?: match.value
            }

        // Restore escaped sequences as literals
        return result
            .replace(escapePlaceholder1, "{{")
            .replace(escapePlaceholder2, "$")
    }

    /**
     * Resolve a variable name, handling simple variables, webhook URLs, and webhook data.
     *
     * Supported formats:
     * - `foo` - Simple variable
     * - `server.hook` - Webhook URL for the specified hook
     * - `server.hook.length` - Number of times the hook was called
     * - `server.hook[0]` - First webhook call (as JSON)
     * - `server.hook[0].body` - Body of the first webhook call
     * - `server.hook[0].body.field` - Field from webhook body (using JsonPath)
     *
     * @param name The variable name
     * @return The resolved value or null if not found
     */
    private fun resolveVariable(name: String): String? {
        fun fallback(): String? {
            // Parse the path into segments
            val segments = parsePathSegments(name)
            if (segments.isEmpty()) return null

            val firstSegment = segments[0]

            // Check if first segment is a webhook server name
            val server = webhookServers[firstSegment.name]
            return if (server != null && segments.size >= 2) {
                resolveWebhookPath(server, segments.drop(1))
            } else {
                null
            }
        }

        fun checkEnv(): String? =
            if (name.startsWith("env.")) {
                environmentProvider(name.removePrefix("env."))
            } else {
                null
            }
        // First, try direct variable lookup
        return variables[name]?.toString()
            ?: checkEnv()
            ?: fallback()
    }

    /**
     * Parse a path like "server.hook[0].body.field" or "array[0][1][2]" into segments.
     * Supports multiple consecutive array indices like board[0][0].
     */
    private fun parsePathSegments(path: String): List<PathSegment> {
        val segments = mutableListOf<PathSegment>()
        // Match a word followed by zero or more array indices

        var remaining = path
        while (remaining.isNotEmpty()) {
            val match = segmentPattern.matchAt(remaining, 0) ?: break

            val name = match.groupValues[1]
            val indicesStr = match.groupValues[2]

            // Parse all indices from the matched string (e.g., "[0][1]" -> [0, 1])
            val indices =
                if (indicesStr.isNotEmpty()) {
                    Regex("""\[(\d+)]""").findAll(indicesStr).map { it.groupValues[1].toInt() }.toList()
                } else {
                    emptyList()
                }

            segments.add(PathSegment(name, indices))

            remaining = remaining.substring(match.range.last + 1)
            if (remaining.startsWith('.')) {
                remaining = remaining.substring(1)
            }
        }

        return segments
    }

    /**
     * Represents a segment of a path, e.g., "hook" or "hook[0]" or "board[0][0]".
     */
    private data class PathSegment(
        val name: String,
        val indices: List<Int> = emptyList(),
    ) {
        /** Convenience property for single-index access (backwards compatibility) */
        val index: Int? get() = indices.firstOrNull()
    }

    /**
     * Resolve a webhook-related path.
     *
     * @param server The MockWebhookServer
     * @param segments Path segments after the server name (e.g., ["hook", "[0]", "body"])
     */
    private fun resolveWebhookPath(
        server: MockWebhookServer,
        segments: List<PathSegment>,
    ): String? {
        // If "server.hook[n]...", get the specific call
        fun PathSegment.webhookCall(hookName: String): String? =
            index?.let { callIndex ->
                val calls = server.getReceived(hookName)
                if (callIndex >= calls.size) {
                    null
                } else {
                    val call = calls[callIndex]
                    // If just "server.hook[0]", return the full call as JSON
                    if (segments.size == 1) {
                        call.body
                    } else {
                        // Process remaining segments
                        resolveWebhookCallPath(call.body, segments.drop(1))
                    }
                }
            }
        if (segments.isEmpty()) return null

        val hookSegment = segments[0]
        val hookName = hookSegment.name

        return when (hookSegment.index) {
            // If just "server.hook" (no index), return the webhook URL
            null if segments.size == 1 -> server.getWebhookUrl(hookName)

            // If "server.hook.length", return the call count
            null if segments.size == 2 && segments[1].name == "length" -> server.getReceivedCount(hookName).toString()

            // Get the webhook calls
            else -> hookSegment.webhookCall(hookName)
        }
    }

    /**
     * Resolve a path within a webhook call body.
     *
     * @param body The webhook call body (JSON string)
     * @param segments Remaining path segments (e.g., ["body", "field"])
     */
    private fun resolveWebhookCallPath(
        body: String,
        segments: List<PathSegment>,
    ): String? {
        if (segments.isEmpty()) return body

        // If first segment is "body", use remaining as JsonPath
        return when (segments[0].name) {
            "body" if (segments.size == 1) -> {
                body
            }

            "body" -> {
                // Build JsonPath from remaining segments
                val jsonPath = buildJsonPath(segments.drop(1))
                val result: Any = JsonPath.read(body, jsonPath)
                result.toString()
            }

            else -> {
                null
            }
        }
    }

    /**
     * Build a JsonPath expression from path segments.
     */
    private fun buildJsonPath(segments: List<PathSegment>): String {
        val builder = StringBuilder("$")
        for ((name, indices) in segments) {
            builder.append(".$name")
            indices.forEach { idx -> builder.append("[$idx]") }
        }
        return builder.toString()
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : Any> ExecutionContext.resolveParam(value: T?): T? =
    when (value) {
        is String -> this.interpolate(value) as T
        else -> value
    }

fun <T : Any> ExecutionContext.resolveParams(params: Map<String, T?>): Map<String, T?> =
    params.entries.associate { (key, value) -> resolveParam(key) as String to resolveParam(value) }

fun ExecutionContext?.propagate(other: ExecutionContext) {
    if (this != null && this.shareVariablesAcrossScenarios) {
        other.allVariables().forEach { (name, value) -> this[name] = value }
    }
}
