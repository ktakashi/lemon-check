package io.github.ktakashi.lemoncheck.junit.discovery

import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.jar.JarFile

/**
 * Discovers .scenario files from classpath locations.
 *
 * Supports glob patterns for flexible file matching (e.g., scenarios/`*`.scenario).
 */
object ScenarioDiscovery {
    /**
     * Discovers scenario files matching the given location patterns.
     *
     * @param classLoader The class loader to use for resource discovery
     * @param patterns Location patterns (glob syntax supported)
     * @return List of URLs pointing to discovered scenario files
     */
    fun discoverScenarios(
        classLoader: ClassLoader,
        patterns: Array<out String>,
    ): List<DiscoveredScenario> {
        val scenarios = mutableListOf<DiscoveredScenario>()

        for (pattern in patterns) {
            scenarios.addAll(discoverForPattern(classLoader, pattern))
        }

        return scenarios.distinctBy { it.path }
    }

    private fun discoverForPattern(
        classLoader: ClassLoader,
        pattern: String,
    ): List<DiscoveredScenario> {
        val scenarios = mutableListOf<DiscoveredScenario>()

        // Extract base directory from pattern (before any wildcards)
        val baseDir = extractBaseDirectory(pattern)
        val globPattern = if (pattern.contains("*")) pattern else "$pattern/**/*.scenario"

        // Get resources from the base directory
        val resources = classLoader.getResources(baseDir)

        while (resources.hasMoreElements()) {
            val resource = resources.nextElement()
            when (resource.protocol) {
                "file" -> scenarios.addAll(discoverFromFileSystem(resource, baseDir, globPattern))
                "jar" -> scenarios.addAll(discoverFromJar(resource, baseDir, globPattern))
            }
        }

        // Also try direct resource lookup for exact paths
        if (!pattern.contains("*")) {
            classLoader.getResource(pattern)?.let { url ->
                if (pattern.endsWith(".scenario")) {
                    val name = pattern.substringAfterLast("/")
                    scenarios.add(DiscoveredScenario(pattern, name, url))
                }
            }
        }

        return scenarios
    }

    private fun extractBaseDirectory(pattern: String): String {
        val parts = pattern.split("/", "\\")
        val baseParts = mutableListOf<String>()

        for (part in parts) {
            if (part.contains("*") || part.contains("?")) {
                break
            }
            baseParts.add(part)
        }

        return baseParts.joinToString("/").ifEmpty { "" }
    }

    private fun discoverFromFileSystem(
        resource: URL,
        baseDir: String,
        globPattern: String,
    ): List<DiscoveredScenario> {
        val scenarios = mutableListOf<DiscoveredScenario>()
        val basePath = File(resource.toURI())

        if (!basePath.exists() || !basePath.isDirectory) {
            return scenarios
        }

        // Create a PathMatcher for the glob pattern
        val matcher = createPathMatcher(globPattern)

        basePath
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(".scenario") }
            .forEach { file ->
                val relativePath = "$baseDir/${file.relativeTo(basePath).path}".replace("\\", "/")
                if (matcher.matches(Path.of(relativePath))) {
                    scenarios.add(
                        DiscoveredScenario(
                            path = relativePath,
                            name = file.name,
                            url = file.toURI().toURL(),
                        ),
                    )
                }
            }

        return scenarios
    }

    private fun discoverFromJar(
        resource: URL,
        baseDir: String,
        globPattern: String,
    ): List<DiscoveredScenario> {
        val scenarios = mutableListOf<DiscoveredScenario>()

        // Extract JAR path from URL (format: jar:file:/path/to/jar.jar!/path/in/jar)
        val jarPath = resource.path.substringAfter("file:").substringBefore("!")
        val jarFile = JarFile(jarPath)
        val matcher = createPathMatcher(globPattern)

        jarFile.use { jar ->
            jar
                .entries()
                .asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".scenario") }
                .filter { it.name.startsWith(baseDir) }
                .forEach { entry ->
                    if (matcher.matches(Path.of(entry.name))) {
                        val url = URI.create("jar:file:$jarPath!/${entry.name}").toURL()
                        scenarios.add(
                            DiscoveredScenario(
                                path = entry.name,
                                name = entry.name.substringAfterLast("/"),
                                url = url,
                            ),
                        )
                    }
                }
        }

        return scenarios
    }

    private fun createPathMatcher(pattern: String): PathMatcher {
        val globPattern = "glob:$pattern"
        return FileSystems.getDefault().getPathMatcher(globPattern)
    }
}

/**
 * Represents a discovered scenario file.
 *
 * @property path The classpath path to the scenario file
 * @property name The filename (without path)
 * @property url The URL to access the scenario file
 */
data class DiscoveredScenario(
    val path: String,
    val name: String,
    val url: URL,
)
