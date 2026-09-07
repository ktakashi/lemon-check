package org.berrycrush.webhook

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.berrycrush.openapi.OpenApiSpec
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

private const val HTTP_STATUS_OK = 200
private const val HTTP_STATUS_NOT_FOUND = 404
private const val HTTP_STATUS_INTERNAL_SERVER_ERROR = 500

/**
 * Mock server for testing webhook deliveries.
 *
 * This server creates HTTP endpoints that simulate webhook receivers,
 * allowing tests to verify that webhooks are properly delivered.
 *
 * Example usage:
 * ```kotlin
 * val server = MockWebhookServer()
 * server.expect("onPetAdopted", WebhookExpectation(body = mapOf("petId" to 123)))
 * server.start()
 *
 * // ... trigger webhook in your application ...
 *
 * val calls = server.getReceived("onPetAdopted")
 * assertTrue(calls.isNotEmpty())
 * server.stop()
 * ```
 */
class MockWebhookServer(
    private val port: Int = 0,
) {
    private var server: HttpServer? = null
    private val expectations = ConcurrentHashMap<String, WebhookExpectation>()
    private val receivedCalls = ConcurrentHashMap<String, CopyOnWriteArrayList<WebhookCall>>()
    private var actualPort: Int = 0

    /**
     * Register an expectation for a webhook operation.
     *
     * @param operationId The operationId from the OpenAPI webhook definition
     * @param expectation The expected webhook call parameters
     */
    fun expect(
        operationId: String,
        expectation: WebhookExpectation = WebhookExpectation(),
    ) {
        expectations[operationId] = expectation
        receivedCalls.putIfAbsent(operationId, CopyOnWriteArrayList())
    }

    /**
     * Register webhooks from an OpenAPI specification.
     *
     * @param spec The OpenAPI specification containing webhook definitions
     */
    fun registerFromSpec(spec: OpenApiSpec) {
        spec.webhooks.forEach { (_, pathSpec) ->
            pathSpec.operations.values.forEach { operation ->
                operation.operationId?.let { operationId ->
                    expect(operationId)
                }
            }
        }
    }

    /**
     * Start the mock webhook server.
     *
     * @return The actual port the server is listening on
     */
    fun start(): Int {
        check(server == null) { "Server already started" }
        server =
            HttpServer.create(InetSocketAddress(port), 0).apply {
                executor = Executors.newVirtualThreadPerTaskExecutor()

                // Create a context for each expected webhook
                expectations.keys.forEach { operationId ->
                    createContext("/webhook/$operationId", WebhookHandler(operationId))
                }

                // Also create a catch-all handler
                createContext("/webhook") { exchange ->
                    val path = exchange.requestURI.path
                    val operationId = path.removePrefix("/webhook/")
                    if (operationId.isNotEmpty() && expectations.containsKey(operationId)) {
                        WebhookHandler(operationId).handle(exchange)
                    } else {
                        exchange.sendResponseHeaders(HTTP_STATUS_NOT_FOUND, -1)
                        exchange.close()
                    }
                }

                start()
            }

        actualPort = server!!.address.port
        return actualPort
    }

    /**
     * Stop the mock webhook server.
     */
    fun stop() {
        server?.stop(0)
        server = null
    }

    /**
     * Get the actual port the server is listening on.
     * Only valid after start() is called.
     */
    fun getPort(): Int = actualPort

    /**
     * Get the base URL for webhook endpoints.
     */
    fun getBaseUrl(): String = "http://localhost:$actualPort/webhook"

    /**
     * Get the URL for a specific webhook operation.
     */
    fun getWebhookUrl(operationId: String): String = "http://localhost:$actualPort/webhook/$operationId"

    /**
     * Get all received calls for a webhook operation.
     *
     * @param operationId The operationId of the webhook
     * @return List of received webhook calls
     */
    fun getReceived(operationId: String): List<WebhookCall> = receivedCalls[operationId]?.toList() ?: emptyList()

    /**
     * Check if a webhook was received.
     *
     * @param operationId The operationId of the webhook
     * @return true if at least one call was received
     */
    fun wasReceived(operationId: String): Boolean = receivedCalls[operationId]?.isNotEmpty() == true

    /**
     * Get the count of received calls for a webhook.
     *
     * @param operationId The operationId of the webhook
     * @return Number of times the webhook was called
     */
    fun getReceivedCount(operationId: String): Int = receivedCalls[operationId]?.size ?: 0

    /**
     * Clear all received calls (but keep expectations).
     */
    fun clearReceived() {
        receivedCalls.values.forEach { it.clear() }
    }

    /**
     * Clear all expectations and received calls.
     */
    fun reset() {
        expectations.clear()
        receivedCalls.clear()
    }

    /**
     * Verify that a webhook was received as expected.
     *
     * @param operationId The operationId of the webhook
     * @return true if the webhook was received and matches expectations
     */
    fun verify(operationId: String): Boolean {
        val expectation = expectations[operationId] ?: return false
        val calls = receivedCalls[operationId] ?: return false
        return !(expectation.expectedCount >= 0 && calls.size != expectation.expectedCount) &&
            !(calls.isEmpty() && expectation.expectedCount != 0)
    }

    /**
     * Handler for individual webhook endpoints.
     */
    private inner class WebhookHandler(
        private val operationId: String,
    ) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            exchange.use {
                runCatching {
                    val body = exchange.requestBody.bufferedReader().use { it.readText() }
                    val headers =
                        exchange.requestHeaders.mapValues { (_, values) ->
                            values.toList()
                        }
                    val contentType = exchange.requestHeaders.getFirst("Content-Type")

                    val call =
                        WebhookCall(
                            operationId = operationId,
                            body = body,
                            headers = headers,
                            contentType = contentType,
                        )

                    receivedCalls.getOrPut(operationId) { CopyOnWriteArrayList() }.add(call)

                    // Send success response
                    exchange.sendResponseHeaders(HTTP_STATUS_OK, 0)
                    exchange.responseBody.use { it.write("OK".toByteArray()) }
                }.onFailure { e ->
                    exchange.sendResponseHeaders(HTTP_STATUS_INTERNAL_SERVER_ERROR, 0)
                    exchange.responseBody.use { it.write("Error: ${e.message}".toByteArray()) }
                }
            }
        }
    }
}
