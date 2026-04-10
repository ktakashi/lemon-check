package io.github.ktakashi.lemoncheck.util

import io.github.ktakashi.lemoncheck.exception.ConfigurationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileLoaderTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should load file from classpath`() {
        val content = FileLoader.load("classpath:petstore.yaml")
        assertTrue(content.contains("openapi"))
        assertTrue(content.contains("Petstore"))
    }

    @Test
    fun `should load file without classpath prefix (defaults to classpath)`() {
        val content = FileLoader.load("petstore.yaml")
        assertTrue(content.contains("openapi"))
    }

    @Test
    fun `should load file from filesystem`() {
        // Create a test file
        val testFile = tempDir.resolve("test-body.json")
        Files.writeString(testFile, """{"name": "Test", "value": 123}""")

        val content = FileLoader.load("file:${testFile.toAbsolutePath()}")
        assertEquals("""{"name": "Test", "value": 123}""", content)
    }

    @Test
    fun `should load file with absolute path shorthand`() {
        val testFile = tempDir.resolve("test-body.json")
        Files.writeString(testFile, """{"id": 1}""")

        val content = FileLoader.load(testFile.toAbsolutePath().toString())
        assertEquals("""{"id": 1}""", content)
    }

    @Test
    fun `should load file with relative path shorthand`() {
        val testFile = tempDir.resolve("relative-test.json")
        Files.writeString(testFile, """{"relative": true}""")

        val content = FileLoader.load("./relative-test.json", tempDir)
        assertEquals("""{"relative": true}""", content)
    }

    @Test
    fun `should throw exception for non-existent classpath resource`() {
        assertFailsWith<ConfigurationException> {
            FileLoader.load("classpath:nonexistent.json")
        }
    }

    @Test
    fun `should throw exception for non-existent file`() {
        assertFailsWith<ConfigurationException> {
            FileLoader.load("file:/nonexistent/path/file.json")
        }
    }

    @Test
    fun `should validate path correctly`() {
        assertTrue(FileLoader.isValidPath("classpath:path/to/file.json"))
        assertTrue(FileLoader.isValidPath("file:./relative.json"))
        assertTrue(FileLoader.isValidPath("/absolute/path.json"))
        assertTrue(FileLoader.isValidPath("./relative/path.json"))
        assertTrue(FileLoader.isValidPath("../parent/path.json"))
        assertTrue(FileLoader.isValidPath("some/path/file.json"))
        assertFalse(FileLoader.isValidPath(null))
    }

    @Test
    fun `should load file with nested directory structure`() {
        val nestedDir = tempDir.resolve("nested/dir")
        Files.createDirectories(nestedDir)
        val testFile = nestedDir.resolve("nested-body.json")
        Files.writeString(testFile, """{"nested": "content"}""")

        val content = FileLoader.load("./nested/dir/nested-body.json", tempDir)
        assertEquals("""{"nested": "content"}""", content)
    }
}
