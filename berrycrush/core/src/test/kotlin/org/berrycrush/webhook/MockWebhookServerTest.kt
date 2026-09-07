package org.berrycrush.webhook

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MockWebhookServerTest {
    private lateinit var server: MockWebhookServer
    private val httpClient = HttpClient.newHttpClient()

    @BeforeTest
    fun setup() {
        server = MockWebhookServer()
    }

    @AfterTest
    fun teardown() {
        server.stop()
    }

    @Test
    fun `should start server on dynamic port`() {
        server.expect("onPetAdopted")
        val port = server.start()

        assertTrue(port > 0)
        assertNotEquals(0, port)
    }

    @Test
    fun `should receive webhook call`() {
        server.expect("onPetAdopted")
        server.start()

        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(server.getWebhookUrl("onPetAdopted")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"petId": 123, "adopterId": 456}"""))
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
        assertTrue(server.wasReceived("onPetAdopted"))
        assertEquals(1, server.getReceivedCount("onPetAdopted"))
    }

    @Test
    fun `should capture webhook body and headers`() {
        server.expect("onPetAdopted")
        server.start()

        val body = """{"petId": 123, "adopterId": 456}"""
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(server.getWebhookUrl("onPetAdopted")))
                .header("Content-Type", "application/json")
                .header("X-Custom-Header", "custom-value")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

        httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        val calls = server.getReceived("onPetAdopted")
        assertEquals(1, calls.size)

        val call = calls.first()
        assertEquals(body, call.body)
        assertEquals("application/json", call.contentType)
        assertEquals(true, call.headers["X-custom-header"]?.contains("custom-value"))
    }

    @Test
    fun `should handle multiple webhook calls`() {
        server.expect("onPetAdopted")
        server.start()

        repeat(3) { i ->
            val request =
                HttpRequest
                    .newBuilder()
                    .uri(URI.create(server.getWebhookUrl("onPetAdopted")))
                    .POST(HttpRequest.BodyPublishers.ofString("""{"call": $i}"""))
                    .build()
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        assertEquals(3, server.getReceivedCount("onPetAdopted"))

        val calls = server.getReceived("onPetAdopted")
        assertEquals(3, calls.size)
    }

    @Test
    fun `should handle multiple webhook endpoints`() {
        server.expect("onPetAdopted")
        server.expect("onPetCreated")
        server.start()

        val adoptRequest =
            HttpRequest
                .newBuilder()
                .uri(URI.create(server.getWebhookUrl("onPetAdopted")))
                .POST(HttpRequest.BodyPublishers.ofString("""{"type": "adopt"}"""))
                .build()

        val createRequest =
            HttpRequest
                .newBuilder()
                .uri(URI.create(server.getWebhookUrl("onPetCreated")))
                .POST(HttpRequest.BodyPublishers.ofString("""{"type": "create"}"""))
                .build()

        httpClient.send(adoptRequest, HttpResponse.BodyHandlers.ofString())
        httpClient.send(createRequest, HttpResponse.BodyHandlers.ofString())

        assertTrue(server.wasReceived("onPetAdopted"))
        assertTrue(server.wasReceived("onPetCreated"))
        assertEquals(1, server.getReceivedCount("onPetAdopted"))
        assertEquals(1, server.getReceivedCount("onPetCreated"))
    }

    @Test
    fun `should return 404 for unknown webhook`() {
        server.expect("onPetAdopted")
        server.start()

        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(server.getWebhookUrl("unknownWebhook")))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(404, response.statusCode())
    }

    @Test
    fun `should clear received calls`() {
        server.expect("onPetAdopted")
        server.start()

        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(server.getWebhookUrl("onPetAdopted")))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build()

        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(1, server.getReceivedCount("onPetAdopted"))

        server.clearReceived()
        assertEquals(0, server.getReceivedCount("onPetAdopted"))
        assertFalse(server.wasReceived("onPetAdopted"))
    }

    @Test
    fun `should verify webhook expectations`() {
        server.expect("onPetAdopted", WebhookExpectation(expectedCount = 2))
        server.start()

        // First call - not enough
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create(server.getWebhookUrl("onPetAdopted")))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build()

        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertFalse(server.verify("onPetAdopted"))

        // Second call - now it's enough
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertTrue(server.verify("onPetAdopted"))
    }

    @Test
    fun `should provide correct webhook URL`() {
        server.expect("onPetAdopted")
        val port = server.start()

        assertEquals("http://localhost:$port/webhook", server.getBaseUrl())
        assertEquals("http://localhost:$port/webhook/onPetAdopted", server.getWebhookUrl("onPetAdopted"))
    }
}
