package org.berrycrush.openapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenApiEdgeCaseTest {
    private val loader = OpenApiLoader

    @Test
    fun `load from Path should parse spec`() {
        val path =
            requireNotNull(javaClass.getResource("/petstore.yaml")) {
                "petstore.yaml not found"
            }.toURI()

        val openApi =
            loader.load(
                java.nio.file.Path
                    .of(path),
            )

        assertEquals("Petstore API", openApi.info.title)
        assertTrue(openApi.paths.isNotEmpty())
    }

    @Test
    fun `loadFromString should parse minimal valid spec`() {
        val content =
            """
            openapi: 3.0.3
            info:
              title: Minimal API
              version: 1.0.0
            paths:
              /ping:
                get:
                  operationId: ping
                  responses:
                    '200':
                      description: OK
            """.trimIndent()

        val openApi = loader.loadFromString(content)

        assertEquals("Minimal API", openApi.info.title)
        assertNotNull(openApi.paths["/ping"])
    }

    @Test
    fun `loadFromString should throw parse exception for invalid content`() {
        val error =
            assertFailsWith<OpenApiParseException> {
                loader.loadFromString("not valid openapi")
            }

        assertEquals(true, error.message?.startsWith("Failed to parse OpenAPI spec"))
    }

    @Test
    fun `open api parse exception should preserve cause`() {
        val cause = IllegalStateException("boom")
        val exception = OpenApiParseException("parse failed", cause)

        assertEquals("parse failed", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `open api spec helper methods should reflect data`() {
        val operation =
            object : OperationSpec {
                override val operationId: String = "ping"
                override val path: String = "/ping"
                override val method: HttpMethod = HttpMethod.GET
                override val summary: String = "ping endpoint"
                override val description: String? = null
                override val tags: List<String> = emptyList()
                override val parameters: List<ParameterSpec> = emptyList()
                override val requestBody: RequestBodySpec? = null
                override val responses: Map<String, ResponseSpec> = emptyMap()
                override val security: List<Map<String, List<String>>>? = null
                override val deprecated: Boolean = false
                override val callbacks: Map<String, Map<String, PathSpec>> = emptyMap()
            }
        val pathSpec =
            object : PathSpec {
                override val path: String = "/ping"
                override val operations: Map<HttpMethod, OperationSpec> = mapOf(HttpMethod.GET to operation)
                override val summary: String? = null
                override val description: String? = null
                override val parameters: List<ParameterSpec> = emptyList()
            }
        val spec =
            object : OpenApiSpec {
                override val version: OpenApiVersion = OpenApiVersion.V3_0_X
                override val specVersion: String = "3.0.3"
                override val info: SpecInfo = SpecInfo("Test API", null, "1.0.0", null, null)
                override val servers: List<ServerInfo> = emptyList()
                override val paths: Map<String, PathSpec> = mapOf("/ping" to pathSpec)
                override val components: ComponentsSpec? = null
                override val webhooks: Map<String, PathSpec> = emptyMap()
                override val rawModel: Any = "raw"

                override fun getOperation(operationId: String): OperationSpec? =
                    getAllOperations().firstOrNull { it.operationId == operationId }

                override fun getAllOperations(): List<OperationSpec> = paths.values.flatMap { it.operations.values }
            }

        assertTrue(spec.hasComponents().not())
        assertTrue(spec.hasWebhooks().not())
        assertEquals("ping", spec.getOperation("ping")?.operationId)
        assertNull(spec.getOperation("missing"))
        assertEquals(operation, pathSpec.getOperation(HttpMethod.GET))
        assertNull(pathSpec.getOperation(HttpMethod.POST))
    }

    @Test
    fun `openapi enums and schema value objects should expose expected values`() {
        assertEquals(listOf("QUERY", "HEADER", "COOKIE"), ApiKeyLocation.entries.map { it.name })
        assertEquals(listOf("PATH", "QUERY", "HEADER", "COOKIE"), ParameterLocation.entries.map { it.name })

        val discriminator = DiscriminatorSpec(propertyName = "kind", mapping = mapOf("cat" to "#/components/schemas/Cat"))
        val externalDocs = ExternalDocsSpec(url = "https://example.test/docs", description = "schema docs")

        assertEquals("kind", discriminator.propertyName)
        assertEquals("#/components/schemas/Cat", discriminator.mapping["cat"])
        assertEquals("https://example.test/docs", externalDocs.url)
        assertEquals("schema docs", externalDocs.description)
    }
}
