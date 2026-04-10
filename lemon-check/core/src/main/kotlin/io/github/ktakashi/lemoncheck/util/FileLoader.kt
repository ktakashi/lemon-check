package io.github.ktakashi.lemoncheck.util

import io.github.ktakashi.lemoncheck.exception.ConfigurationException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Utility for loading external files from classpath or file system.
 *
 * Commonly used for:
 * - Request body templates
 * - Expected response fixtures
 * - Configuration files
 *
 * Supported path formats:
 * - `classpath:path/to/file.json` - Load from classpath
 * - `file:./relative/path.json` - Load from file system (relative to working directory)
 * - `file:/absolute/path.json` - Load from file system (absolute path)
 * - `/absolute/path.json` - Load from file system (absolute path, shorthand)
 * - `./relative/path.json` - Load from file system (relative path, shorthand)
 */
object FileLoader {
    private const val CLASSPATH_PREFIX = "classpath:"
    private const val FILE_PREFIX = "file:"

    /**
     * Load content from the specified path.
     *
     * @param path The file path with optional prefix (classpath: or file:)
     * @param baseDirectory Optional base directory for resolving relative paths
     * @return The file content as a string
     * @throws ConfigurationException if the file cannot be loaded
     */
    fun load(
        path: String,
        baseDirectory: Path? = null,
    ): String =
        runCatching {
            when {
                path.startsWith(CLASSPATH_PREFIX) -> loadFromClasspath(path.removePrefix(CLASSPATH_PREFIX))
                path.startsWith(FILE_PREFIX) -> loadFromFileSystem(path.removePrefix(FILE_PREFIX), baseDirectory)
                path.startsWith("/") -> loadFromFileSystem(path, baseDirectory)
                path.startsWith("./") || path.startsWith("../") -> loadFromFileSystem(path, baseDirectory)
                else -> loadFromClasspath(path) // Default to classpath
            }
        }.getOrElse { e ->
            throw ConfigurationException(
                "Failed to load file '$path': ${e.message}",
            )
        }

    /**
     * Load content from classpath.
     */
    private fun loadFromClasspath(path: String): String {
        val normalizedPath = path.trimStart('/')
        val inputStream =
            Thread.currentThread().contextClassLoader?.getResourceAsStream(normalizedPath)
                ?: FileLoader::class.java.classLoader?.getResourceAsStream(normalizedPath)
                ?: throw ConfigurationException("Classpath resource not found: $normalizedPath")

        return inputStream.bufferedReader().use { it.readText() }
    }

    /**
     * Load content from file system.
     */
    private fun loadFromFileSystem(
        path: String,
        baseDirectory: Path?,
    ): String {
        val filePath =
            when {
                path.startsWith("/") -> Paths.get(path)
                baseDirectory != null -> baseDirectory.resolve(path).normalize()
                else -> Paths.get(path).toAbsolutePath().normalize()
            }

        if (!Files.exists(filePath)) {
            throw ConfigurationException("File not found: $filePath")
        }

        return Files.readString(filePath)
    }

    /**
     * Check if the path references a file that needs loading.
     */
    fun isValidPath(path: String?): Boolean =
        path?.let {
            it.startsWith(CLASSPATH_PREFIX) ||
                it.startsWith(FILE_PREFIX) ||
                it.startsWith("/") ||
                it.startsWith("./") ||
                it.startsWith("../") ||
                it.contains("/") // Any path with slashes
        } ?: false
}
