package org.berrycrush.openapi

import kotlin.test.Test
import kotlin.test.assertEquals

class OpenApiVersionTest {
    @Test
    fun `should detect Swagger 2_x version`() {
        assertEquals(OpenApiVersion.V2_X, OpenApiVersion.detect("2.0"))
        assertEquals(OpenApiVersion.V2_X, OpenApiVersion.detect("2.0.0"))
    }

    @Test
    fun `should detect OpenAPI 3_0_x version`() {
        assertEquals(OpenApiVersion.V3_0_X, OpenApiVersion.detect("3.0.0"))
        assertEquals(OpenApiVersion.V3_0_X, OpenApiVersion.detect("3.0.1"))
        assertEquals(OpenApiVersion.V3_0_X, OpenApiVersion.detect("3.0.3"))
    }

    @Test
    fun `should detect OpenAPI 3_1_x version`() {
        assertEquals(OpenApiVersion.V3_1_X, OpenApiVersion.detect("3.1.0"))
        assertEquals(OpenApiVersion.V3_1_X, OpenApiVersion.detect("3.1.1"))
    }

    @Test
    fun `should detect OpenAPI 3_2_x version`() {
        assertEquals(OpenApiVersion.V3_2_X, OpenApiVersion.detect("3.2.0"))
    }

    @Test
    fun `should return UNKNOWN for null input`() {
        assertEquals(OpenApiVersion.UNKNOWN, OpenApiVersion.detect(null))
    }

    @Test
    fun `should return UNKNOWN for blank input`() {
        assertEquals(OpenApiVersion.UNKNOWN, OpenApiVersion.detect(""))
        assertEquals(OpenApiVersion.UNKNOWN, OpenApiVersion.detect("  "))
    }

    @Test
    fun `should return UNKNOWN for unrecognized version`() {
        assertEquals(OpenApiVersion.UNKNOWN, OpenApiVersion.detect("4.0.0"))
        assertEquals(OpenApiVersion.UNKNOWN, OpenApiVersion.detect("1.0"))
    }

    @Test
    fun `version enum should have correct display names`() {
        assertEquals("Swagger 2.x", OpenApiVersion.V2_X.displayName)
        assertEquals("OpenAPI 3.0.x", OpenApiVersion.V3_0_X.displayName)
        assertEquals("OpenAPI 3.1.x", OpenApiVersion.V3_1_X.displayName)
        assertEquals("OpenAPI 3.2.x", OpenApiVersion.V3_2_X.displayName)
        assertEquals("Unknown", OpenApiVersion.UNKNOWN.displayName)
    }

    @Test
    fun `version enum should have correct prefixes`() {
        assertEquals("2.", OpenApiVersion.V2_X.prefix)
        assertEquals("3.0", OpenApiVersion.V3_0_X.prefix)
        assertEquals("3.1", OpenApiVersion.V3_1_X.prefix)
        assertEquals("3.2", OpenApiVersion.V3_2_X.prefix)
        assertEquals("", OpenApiVersion.UNKNOWN.prefix)
    }
}
