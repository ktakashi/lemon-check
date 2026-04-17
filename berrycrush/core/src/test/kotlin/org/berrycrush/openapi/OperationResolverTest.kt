package org.berrycrush.openapi

import org.berrycrush.exception.OperationNotFoundException
import org.berrycrush.openapi.impl.SwaggerParserAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OperationResolverTest {
    private val parser = SwaggerParserAdapter()

    private fun loadPetstoreResolver(): OperationResolver {
        val specPath =
            javaClass.getResource("/petstore.yaml")?.path
                ?: error("petstore.yaml not found in test resources")
        val spec = parser.parse(specPath)
        return OperationResolver(spec)
    }

    @Test
    fun `should resolve listPets operation`() {
        val resolver = loadPetstoreResolver()

        val operation = resolver.resolve("listPets")

        assertEquals("listPets", operation.operationId)
        assertEquals("/pets", operation.path)
        assertEquals(HttpMethod.GET, operation.method)
    }

    @Test
    fun `should resolve getPetById operation`() {
        val resolver = loadPetstoreResolver()

        val operation = resolver.resolve("getPetById")

        assertEquals("getPetById", operation.operationId)
        assertEquals("/pets/{petId}", operation.path)
        assertEquals(HttpMethod.GET, operation.method)
    }

    @Test
    fun `should resolve createPet operation`() {
        val resolver = loadPetstoreResolver()

        val operation = resolver.resolve("createPet")

        assertEquals("createPet", operation.operationId)
        assertEquals("/pets", operation.path)
        assertEquals(HttpMethod.POST, operation.method)
    }

    @Test
    fun `should resolve updatePet operation`() {
        val resolver = loadPetstoreResolver()

        val operation = resolver.resolve("updatePet")

        assertEquals("updatePet", operation.operationId)
        assertEquals("/pets/{petId}", operation.path)
        assertEquals(HttpMethod.PUT, operation.method)
    }

    @Test
    fun `should resolve deletePet operation`() {
        val resolver = loadPetstoreResolver()

        val operation = resolver.resolve("deletePet")

        assertEquals("/pets/{petId}", operation.path)
        assertEquals(HttpMethod.DELETE, operation.method)
    }

    @Test
    fun `should throw OperationNotFoundException for unknown operation`() {
        val resolver = loadPetstoreResolver()

        val exception =
            assertFailsWith<OperationNotFoundException> {
                resolver.resolve("unknownOperation")
            }

        assertEquals("unknownOperation", exception.operationId)
        assertTrue(exception.availableOperations.isNotEmpty())
    }

    @Test
    fun `should list all operation IDs`() {
        val resolver = loadPetstoreResolver()

        val allOps = resolver.allOperationIds()

        assertTrue("listPets" in allOps)
        assertTrue("getPetById" in allOps)
        assertTrue("createPet" in allOps)
        assertTrue("updatePet" in allOps)
        assertTrue("deletePet" in allOps)
        assertTrue("login" in allOps)
    }

    @Test
    fun `should check if operation exists`() {
        val resolver = loadPetstoreResolver()

        assertTrue(resolver.hasOperation("listPets"))
        assertTrue(resolver.hasOperation("getPetById"))
        assertTrue(!resolver.hasOperation("nonExistent"))
    }

    @Test
    fun `should include operation details in resolved operation`() {
        val resolver = loadPetstoreResolver()

        val operation = resolver.resolve("listPets")

        assertNotNull(operation.operation)
        assertNotNull(operation.operation.parameters)
        assertTrue(operation.operation.parameters.any { it.name == "limit" })
        assertTrue(operation.operation.parameters.any { it.name == "status" })
    }
}
