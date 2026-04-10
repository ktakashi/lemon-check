package io.github.ktakashi.lemoncheck.logging

import io.github.ktakashi.lemoncheck.openapi.HttpMethod
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Optional
import javax.net.ssl.SSLSession

class HttpLogFormatterTest {
    private val formatter = DefaultHttpLogFormatter()

    @Test
    fun `formatRequest includes method and URL`() {
        val result =
            formatter.formatRequest(
                method = HttpMethod.POST,
                url = "http://localhost:8080/api/users",
                headers = mapOf("Content-Type" to "application/json"),
                body = """{"name": "John"}""",
            )

        assertTrue(result.contains("HTTP Request"))
        assertTrue(result.contains("POST http://localhost:8080/api/users"))
        assertTrue(result.contains("""{"name": "John"}"""))
    }

    @Test
    fun `formatRequest masks sensitive headers`() {
        val result =
            formatter.formatRequest(
                method = HttpMethod.GET,
                url = "http://localhost:8080/api",
                headers = mapOf("Authorization" to "Bearer secret-token", "X-Custom" to "value"),
                body = null,
            )

        assertTrue(result.contains("Authorization=***"))
        assertTrue(result.contains("X-Custom=value"))
        assertFalse(result.contains("secret-token"))
    }

    @Test
    fun `formatRequest truncates long body`() {
        val longBody = "a".repeat(2000)
        val formatter =
            DefaultHttpLogFormatter(
                includeBody = true,
                maxBodyLength = 100,
            )

        val result =
            formatter.formatRequest(
                method = HttpMethod.POST,
                url = "http://localhost/test",
                headers = emptyMap(),
                body = longBody,
            )

        assertTrue(result.contains("[truncated"))
        assertTrue(result.contains("2000 total chars"))
    }

    @Test
    fun `formatResponse includes status code and duration`() {
        val mockResponse = MockHttpResponse(200, """{"id": 1}""")

        val result =
            formatter.formatResponse(
                method = HttpMethod.GET,
                url = "http://localhost/api/users/1",
                response = mockResponse,
                durationMs = 125,
            )

        assertTrue(result.contains("HTTP Response"))
        assertTrue(result.contains("200 OK"))
        assertTrue(result.contains("125ms"))
    }

    @Test
    fun `compact formatter produces single-line output`() {
        val compactFormatter = CompactHttpLogFormatter()

        val request =
            compactFormatter.formatRequest(
                method = HttpMethod.POST,
                url = "http://localhost/api",
                headers = emptyMap(),
                body = """{"key": "value"}""",
            )

        // Single line, no newlines except possibly at start/end
        assertTrue(request.lines().size <= 1)
        assertTrue(request.contains("16 chars"))
    }

    /**
     * Mock HttpResponse for testing purposes.
     */
    class MockHttpResponse(
        private val statusCodeVal: Int,
        private val bodyVal: String,
    ) : HttpResponse<String> {
        override fun statusCode(): Int = statusCodeVal

        override fun request(): HttpRequest = throw NotImplementedError()

        override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()

        override fun headers(): HttpHeaders =
            HttpHeaders.of(
                mapOf("content-type" to listOf("application/json")),
            ) { _, _ -> true }

        override fun body(): String = bodyVal

        override fun sslSession(): Optional<SSLSession> = Optional.empty()

        override fun uri(): URI = URI.create("http://localhost")

        override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
    }
}

class HttpLoggerFactoryTest {
    @BeforeEach
    fun resetFactory() {
        HttpLoggerFactory.resetToDefault()
    }

    @Test
    fun `default factory creates ConsoleHttpLogger`() {
        val logger = HttpLoggerFactory.create()
        assertTrue(logger is ConsoleHttpLogger)
    }

    @Test
    fun `useJulLogger switches to JUL implementation`() {
        HttpLoggerFactory.useJulLogger()
        val logger = HttpLoggerFactory.create()
        assertTrue(logger is JulHttpLogger)
    }

    @Test
    fun `setFactory accepts custom factory`() {
        var called = false
        HttpLoggerFactory.setFactory {
            called = true
            object : HttpLogger {
                override fun logRequest(
                    method: HttpMethod,
                    url: String,
                    headers: Map<String, String>,
                    body: String?,
                ) = Unit

                override fun logResponse(
                    method: HttpMethod,
                    url: String,
                    response: HttpResponse<String>,
                    durationMs: Long,
                ) = Unit
            }
        }

        HttpLoggerFactory.create()
        assertTrue(called)
    }
}
