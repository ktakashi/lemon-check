package org.berrycrush.openapi.impl

import org.berrycrush.openapi.HttpMethod
import org.berrycrush.openapi.OpenApiVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SwaggerParserAdapterTest {
    private val parser = SwaggerParserAdapter()

    // ========================================
    // Version Detection Tests
    // ========================================

    @Test
    fun `should detect version 3_0_3 from petstore spec`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertEquals(OpenApiVersion.V3_0_X, spec.version)
        assertEquals("3.0.3", spec.specVersion)
    }

    @Test
    fun `should detect version 3_1_0 from train-travel spec`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertEquals(OpenApiVersion.V3_1_X, spec.version)
        assertEquals("3.1.0", spec.specVersion)
    }

    @Test
    fun `should detect version 3_1_0 from tictactoe spec`() {
        val specPath =
            javaClass.getResource("/tictactoe.yaml")?.path
                ?: error("tictactoe.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertEquals(OpenApiVersion.V3_1_X, spec.version)
        assertEquals("3.1.0", spec.specVersion)
    }

    // ========================================
    // Info Tests
    // ========================================

    @Test
    fun `should parse info from petstore spec`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertEquals("Petstore API", spec.info.title)
        assertEquals("1.0.0", spec.info.version)
        assertNotNull(spec.info.description)
    }

    @Test
    fun `should parse info from train-travel spec`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertEquals("Train Travel API", spec.info.title)
        assertEquals("1.0.0", spec.info.version)
        assertNotNull(spec.info.contact)
        assertEquals("Train Support", spec.info.contact?.name)
        assertNotNull(spec.info.license)
        assertEquals("Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International", spec.info.license?.name)
    }

    // ========================================
    // Servers Tests
    // ========================================

    @Test
    fun `should parse servers from petstore spec`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertTrue(spec.servers.isNotEmpty())
        assertEquals("https://petstore.example.com/api/v1", spec.servers[0].url)
    }

    @Test
    fun `should parse servers from train-travel spec`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertTrue(spec.servers.isNotEmpty())
        assertEquals("https://api.example.com", spec.servers[0].url)
        assertEquals("Production", spec.servers[0].description)
    }

    // ========================================
    // Paths Tests
    // ========================================

    @Test
    fun `should parse paths from petstore spec`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertNotNull(spec.paths)
        assertTrue(spec.paths.isNotEmpty())
        assertNotNull(spec.paths["/pets"])
        assertNotNull(spec.paths["/pets/{petId}"])
    }

    @Test
    fun `should parse paths from train-travel spec`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertNotNull(spec.paths)
        assertTrue(spec.paths.isNotEmpty())
        assertNotNull(spec.paths["/stations"])
        assertNotNull(spec.paths["/trips"])
        assertNotNull(spec.paths["/bookings"])
    }

    // ========================================
    // Operations Tests
    // ========================================

    @Test
    fun `should parse operations from paths`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        val stationsPath = spec.paths["/stations"]
        assertNotNull(stationsPath)
        assertNotNull(stationsPath.getOperation(HttpMethod.GET))
        assertEquals("get-stations", stationsPath.getOperation(HttpMethod.GET)?.operationId)
    }

    @Test
    fun `should get operation by operationId`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        val getStations = spec.getOperation("get-stations")
        assertNotNull(getStations)
        assertEquals("/stations", getStations.path)
        assertEquals(HttpMethod.GET, getStations.method)
        assertEquals("Get a list of train stations", getStations.summary)
    }

    @Test
    fun `should return null for unknown operationId`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertNull(spec.getOperation("non-existent"))
    }

    @Test
    fun `should get all operations`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        val allOps = spec.getAllOperations()
        assertTrue(allOps.isNotEmpty())
        val operationIds = allOps.mapNotNull { it.operationId }
        assertTrue(operationIds.contains("get-stations"))
        assertTrue(operationIds.contains("get-trips"))
        assertTrue(operationIds.contains("get-bookings"))
        assertTrue(operationIds.contains("create-booking"))
    }

    // ========================================
    // Webhooks Tests (3.1+ feature)
    // ========================================

    @Test
    fun `should not have webhooks in 3_0_x spec`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertFalse(spec.hasWebhooks())
        assertTrue(spec.webhooks.isEmpty())
    }

    @Test
    fun `should parse webhooks from train-travel spec`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertTrue(spec.hasWebhooks())
        assertNotNull(spec.webhooks["newBooking"])

        val webhook = spec.webhooks["newBooking"]
        assertNotNull(webhook)
        val postOp = webhook.getOperation(HttpMethod.POST)
        assertNotNull(postOp)
        assertEquals("new-booking", postOp.operationId)
    }

    @Test
    fun `should parse webhooks from tictactoe spec`() {
        val specPath =
            javaClass.getResource("/tictactoe.yaml")?.path
                ?: error("tictactoe.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertTrue(spec.hasWebhooks())
        assertNotNull(spec.webhooks["markStatus"])

        val webhook = spec.webhooks["markStatus"]
        assertNotNull(webhook)
        val postOp = webhook.getOperation(HttpMethod.POST)
        assertNotNull(postOp)
        assertEquals("markOperationWebhook", postOp.operationId)
    }

    // ========================================
    // Components Tests
    // ========================================

    @Test
    fun `should parse components from petstore spec`() {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertTrue(spec.hasComponents())
        assertNotNull(spec.components)
        assertTrue(spec.components!!.schemas.containsKey("Pet"))
    }

    @Test
    fun `should parse components from train-travel spec`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertTrue(spec.hasComponents())
        assertNotNull(spec.components)
        assertTrue(spec.components!!.schemas.containsKey("Station"))
        assertTrue(spec.components!!.schemas.containsKey("Trip"))
        assertTrue(spec.components!!.schemas.containsKey("Booking"))
    }

    @Test
    fun `should parse security schemes from train-travel spec`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        assertNotNull(spec.components?.securitySchemes)
        assertTrue(spec.components!!.securitySchemes.containsKey("OAuth2"))
    }

    // ========================================
    // Schema Tests
    // ========================================

    @Test
    fun `should parse schema properties`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        val stationSchema = spec.components?.schemas?.get("Station")
        assertNotNull(stationSchema)
        assertNotNull(stationSchema.properties)
        assertTrue(stationSchema.properties!!.containsKey("id"))
        assertTrue(stationSchema.properties!!.containsKey("name"))
    }

    @Test
    fun `should handle exclusiveMinimum as number in 3_1_x`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        // BookingPayment.amount has exclusiveMinimum: 0
        val bookingPaymentSchema = spec.components?.schemas?.get("BookingPayment")
        assertNotNull(bookingPaymentSchema)
        val amountSchema = bookingPaymentSchema.properties?.get("amount")
        assertNotNull(amountSchema)
        assertNotNull(amountSchema.exclusiveMinimum)
        assertEquals(0, amountSchema.exclusiveMinimum?.toInt())
    }

    // ========================================
    // Parameter Tests
    // ========================================

    @Test
    fun `should parse parameters from operation`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        val getTrips = spec.getOperation("get-trips")
        assertNotNull(getTrips)
        assertTrue(getTrips.parameters.isNotEmpty())

        val originParam = getTrips.parameters.find { it.name == "origin" }
        assertNotNull(originParam)
        assertTrue(originParam.required)
    }

    // ========================================
    // Response Tests
    // ========================================

    @Test
    fun `should parse responses from operation`() {
        val specPath =
            javaClass.getResource("/train-travel.yaml")?.path
                ?: error("train-travel.yaml not found in test resources")

        val spec = parser.parse(specPath)

        val getStations = spec.getOperation("get-stations")
        assertNotNull(getStations)
        assertTrue(getStations.responses.isNotEmpty())
        assertNotNull(getStations.responses["200"])
    }

    // ========================================
    // Callbacks Tests
    // ========================================

    @Test
    fun `should parse callbacks from tictactoe spec`() {
        val specPath =
            javaClass.getResource("/tictactoe.yaml")?.path
                ?: error("tictactoe.yaml not found in test resources")

        val spec = parser.parse(specPath)

        val putSquare = spec.getOperation("put-square")
        assertNotNull(putSquare)
        assertTrue(putSquare.callbacks.isNotEmpty())
        assertNotNull(putSquare.callbacks["statusCallback"])
    }

    // ========================================
    // Supported Versions Test
    // ========================================

    @Test
    fun `parser should support expected versions`() {
        val supported = parser.supportedVersions()

        assertTrue(supported.contains(OpenApiVersion.V2_X))
        assertTrue(supported.contains(OpenApiVersion.V3_0_X))
        assertTrue(supported.contains(OpenApiVersion.V3_1_X))
    }
}
