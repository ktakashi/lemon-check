package io.github.ktakashi.lemoncheck.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExecutionContextTest {
    @Test
    fun `should store and retrieve variables`() {
        val context = ExecutionContext()

        context["name"] = "Rex"
        context["age"] = 5

        assertEquals("Rex", context.get<String>("name"))
        assertEquals(5, context.get<Int>("age"))
    }

    @Test
    fun `should return null for missing variables`() {
        val context = ExecutionContext()

        assertNull(context.get<String>("missing"))
    }

    @Test
    fun `should return default for missing variables`() {
        val context = ExecutionContext()

        assertEquals("default", context.getOrDefault("missing", "default"))
    }

    @Test
    fun `should check if variable exists`() {
        val context = ExecutionContext()

        context["exists"] = "value"

        assertTrue(context.contains("exists"))
        assertFalse(context.contains("missing"))
    }

    @Test
    fun `should list all variable names`() {
        val context = ExecutionContext()

        context["a"] = 1
        context["b"] = 2
        context["c"] = 3

        val names = context.variableNames()

        assertEquals(setOf("a", "b", "c"), names)
    }

    @Test
    fun `should interpolate simple variables`() {
        val context = ExecutionContext()

        context["name"] = "Rex"
        context["age"] = 5

        val result = context.interpolate($$"Pet $name is $age years old")

        assertEquals("Pet Rex is 5 years old", result)
    }

    @Test
    fun `should interpolate bracketed variables`() {
        val context = ExecutionContext()

        context["petName"] = "Rex"

        val result = context.interpolate($$"Pet ${petName} is cute")

        assertEquals("Pet Rex is cute", result)
    }

    @Test
    fun `should leave unknown variables as-is`() {
        val context = ExecutionContext()

        context["known"] = "value"

        val result = context.interpolate($$"$known and $unknown")

        assertEquals($$"value and $unknown", result)
    }

    @Test
    fun `should clear all variables`() {
        val context = ExecutionContext()

        context["a"] = 1
        context["b"] = 2

        context.clear()

        assertFalse(context.contains("a"))
        assertFalse(context.contains("b"))
        assertTrue(context.variableNames().isEmpty())
    }

    @Test
    fun `should create child context with inherited variables`() {
        val parent = ExecutionContext()
        parent["inherited"] = "value"

        val child = parent.createChild()

        assertEquals("value", child.get<String>("inherited"))

        // Modifying child should not affect parent
        child["inherited"] = "modified"
        assertEquals("value", parent.get<String>("inherited"))
    }

    @Test
    fun `should interpolate mustache-style variables`() {
        val context = ExecutionContext()
        context["petId"] = 123
        context["name"] = "Fluffy"

        val result = context.interpolate("Pet {{petId}} is named {{name}}")

        assertEquals("Pet 123 is named Fluffy", result)
    }

    @Test
    fun `should leave unknown mustache variables as-is`() {
        val context = ExecutionContext()
        context["known"] = "value"

        val result = context.interpolate("{{known}} and {{unknown}}")

        assertEquals("value and {{unknown}}", result)
    }
}
