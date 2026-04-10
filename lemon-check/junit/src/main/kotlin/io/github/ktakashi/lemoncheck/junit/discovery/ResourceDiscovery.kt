package io.github.ktakashi.lemoncheck.junit.discovery

import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.jar.JarFile

/**
 * Marker interface for discovered classpath resources.
 */
interface Discovered {
    val path: String
    val name: String
    val url: URL
}

/**
 * Generic resource discovery for classpath files.
 *
 * Supports glob patterns for flexible file matching.
 * Discovers files from both file system and JAR resources.
 *
 * @param T The type of discovered resource
 * @property fileExtension The file extension to match (e.g., ".scenario", ".fragment")
 * @property resourceFactory Factory function to create discovered resource instances
 */
abstract class ResourceDiscovery<T : Discovered>(
    private val fileExtension: String,
    private val resourceFactory: (path: String, name: String, url: URL) -> T,
) {
    /**
     * Discovers files matching the given location patterns.
     *
     * @param classLoader The class loader to use for resource discovery
     * @param patterns Location patterns (glob syntax supported)
     * @return List of discovered resources
     */
    fun discover(
        classLoader: ClassLoader,
        patterns: Array<out String>,
    ): List<T> =
        patterns
            .flatMap { discoverForPattern(classLoader, it) }
            .distinctBy { it.path }

    private fun discoverForPattern(
        classLoader: ClassLoader,
        pattern: String,
    ): List<T> {
        val baseDir = extractBaseDirectory(pattern)
        val globPattern = buildGlobPattern(pattern)
        val resources = classLoader.getResources(baseDir)

        val discoveredFromResources =
            generateSequence { resources.takeIf { it.hasMoreElements() }?.nextElement() }
                .flatMap { resource ->
                    when (resource.protocol) {
                        "file" -> discoverFromFileSystem(resource, baseDir, globPattern)
                        "jar" -> discoverFromJar(resource, baseDir, globPattern)
                        else -> emptyList()
                    }
                }.toList()

        // Also try direct resource lookup for exact paths
        val directResource =
            if (pattern.contains("*")) {
                emptyList()
            } else {
                classLoader
                    .getResource(pattern)
                    ?.takeIf { pattern.endsWith(fileExtension) }
                    ?.let { url ->
                        val name = pattern.substringAfterLast("/")
                        listOf(resourceFactory(pattern, name, url))
                    }
                    ?: emptyList()
            }

        return discoveredFromResources + directResource
    }

    private fun buildGlobPattern(pattern: String): String {
        if (pattern.contains("*")) {
            return pattern
        }
        // Build pattern like: "basedir/**//*.extension"
        // Using raw strings to avoid comment lexer issues
        val starStar = "*".repeat(2)
        return "$pattern/$starStar/*$fileExtension"
    }

    private fun extractBaseDirectory(pattern: String): String =
        pattern
            .split("/", "\\")
            .takeWhile { !it.contains("*") && !it.contains("?") }
            .joinToString("/")

    private fun discoverFromFileSystem(
        resource: URL,
        baseDir: String,
        globPattern: String,
    ): List<T> {
        val basePath = File(resource.toURI())

        if (!basePath.exists() || !basePath.isDirectory) {
            return emptyList()
        }

        val matcher = createPathMatcher(globPattern)

        return basePath
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(fileExtension) }
            .mapNotNull { file ->
                val relativePath = "$baseDir/${file.relativeTo(basePath).path}".replace("\\", "/")
                if (matcher.matches(Path.of(relativePath))) {
                    resourceFactory(relativePath, file.name, file.toURI().toURL())
                } else {
                    null
                }
            }.toList()
    }

    private fun discoverFromJar(
        resource: URL,
        baseDir: String,
        globPattern: String,
    ): List<T> {
        val jarPath = resource.path.substringAfter("file:").substringBefore("!")
        val matcher = createPathMatcher(globPattern)

        return JarFile(jarPath).use { jar ->
            jar
                .entries()
                .asSequence()
                .filter { !it.isDirectory && it.name.endsWith(fileExtension) }
                .filter { it.name.startsWith(baseDir) }
                .filter { matcher.matches(Path.of(it.name)) }
                .map { entry ->
                    val url = URI.create("jar:file:$jarPath!/${entry.name}").toURL()
                    resourceFactory(
                        entry.name,
                        entry.name.substringAfterLast("/"),
                        url,
                    )
                }.toList()
        }
    }

    private fun createPathMatcher(pattern: String): PathMatcher {
        val globPattern = "glob:$pattern"
        return FileSystems.getDefault().getPathMatcher(globPattern)
    }
}
