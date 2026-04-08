package io.github.ktakashi.lemoncheck.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ValueExtractorTest {
    private val extractor = ValueExtractor()

    private val sampleJson =
        """
        {
            "pets": [
                {"id": 1, "name": "Rex", "status": "available"},
                {"id": 2, "name": "Fluffy", "status": "pending"},
                {"id": 3, "name": "Spike", "status": "sold"}
            ],
            "total": 3,
            "store": {
                "name": "Pet Paradise",
                "address": {
                    "city": "New York",
                    "zip": "10001"
                }
            }
        }
        """.trimIndent()

    @Test
    fun `should extract simple value`() {
        val total = extractor.extract(sampleJson, "$.total")

        assertEquals(3, total)
    }

    @Test
    fun `should extract nested value`() {
        val city = extractor.extract(sampleJson, "$.store.address.city")

        assertEquals("New York", city)
    }

    @Test
    fun `should extract array element`() {
        val firstPetName = extractor.extract(sampleJson, "$.pets[0].name")

        assertEquals("Rex", firstPetName)
    }

    @Test
    fun `should extract using filter expression`() {
        val availablePet = extractor.extract(sampleJson, "$.pets[?(@.status=='available')].name")

        assertTrue(availablePet is List<*>)
        assertEquals("Rex", availablePet[0])
    }

    @Test
    fun `should return null for non-existent path`() {
        val result = extractor.extract(sampleJson, "$.nonExistent")

        assertNull(result)
    }

    @Test
    fun `should extract with default value`() {
        val missing = extractor.extractOrDefault(sampleJson, "$.missing", "default")
        val existing = extractor.extractOrDefault(sampleJson, "$.total", 0)

        assertEquals("default", missing)
        assertEquals(3, existing)
    }

    @Test
    fun `should extract to context`() {
        val context = ExecutionContext()
        val extraction =
            io.github.ktakashi.lemoncheck.model.Extraction(
                variableName = "petId",
                jsonPath = "$.pets[0].id",
            )

        extractor.extractTo(sampleJson, extraction, context)

        assertEquals(1, context.get<Int>("petId"))
    }

    @Test
    fun `should extract multiple values`() {
        val context = ExecutionContext()
        val extractions =
            listOf(
                io.github.ktakashi.lemoncheck.model
                    .Extraction("total", "$.total"),
                io.github.ktakashi.lemoncheck.model
                    .Extraction("storeName", "$.store.name"),
                io.github.ktakashi.lemoncheck.model
                    .Extraction("firstPet", "$.pets[0].name"),
            )

        val results = extractor.extractAll(sampleJson, extractions, context)

        assertEquals(3, results.size)
        assertEquals(3, results["total"])
        assertEquals("Pet Paradise", results["storeName"])
        assertEquals("Rex", results["firstPet"])

        // Verify context was also populated
        assertEquals(3, context.get<Int>("total"))
        assertEquals("Pet Paradise", context.get<String>("storeName"))
    }

    @Test
    fun `should extract list`() {
        val ids = extractor.extractList(sampleJson, "$.pets[*].id")

        assertEquals(3, ids.size)
        assertEquals(listOf(1, 2, 3), ids)
    }

    @Test
    fun `should check path exists`() {
        assertTrue(extractor.pathExists(sampleJson, "$.total"))
        assertTrue(extractor.pathExists(sampleJson, "$.store.name"))
        assertFalse(extractor.pathExists(sampleJson, "$.missing"))
        assertFalse(extractor.pathExists(sampleJson, "$.pets[100]"))
    }

    @Test
    fun `should extract typed value`() {
        val total: Int? = extractor.extractTyped(sampleJson, "$.total")
        val name: String? = extractor.extractTyped(sampleJson, "$.store.name")

        assertEquals(3, total)
        assertEquals("Pet Paradise", name)
    }

    @Test
    fun `should handle empty JSON`() {
        val result = extractor.extract("{}", "$.any")

        assertNull(result)
    }

    @Test
    fun `should handle JSON array at root`() {
        val arrayJson = """[{"id": 1}, {"id": 2}]"""

        val ids = extractor.extractList(arrayJson, "$[*].id")

        assertEquals(listOf(1, 2), ids)
    }
}
