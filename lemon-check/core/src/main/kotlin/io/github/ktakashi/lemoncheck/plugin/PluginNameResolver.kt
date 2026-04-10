package io.github.ktakashi.lemoncheck.plugin

import io.github.ktakashi.lemoncheck.exception.ConfigurationException
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * Resolves plugin names to plugin instances via ServiceLoader discovery.
 *
 * Handles name-based plugin registration format: `<plugin-id>[:<param>]`
 *
 * ## Resolution Order
 *
 * 1. **ServiceLoader discovery by ID**: Exact match against plugin's `id` property
 * 2. **ServiceLoader discovery by name**: Exact match against plugin's `name` property
 *
 * ## Plugin Name Format
 *
 * - `"<plugin-id>"` → Plugin with default configuration
 * - `"<plugin-id>:<path>"` → Plugin with Path parameter (if constructor supports it)
 *
 * ## Examples
 *
 * ```kotlin
 * // Resolve by ID
 * @LemonCheckConfiguration(plugins = ["sample:logging"])
 *
 * // Resolve by name
 * @LemonCheckConfiguration(plugins = ["Sample Logging Plugin"])
 *
 * // Resolve with Path parameter
 * @LemonCheckConfiguration(plugins = ["report:json:build/reports/output.json"])
 * ```
 *
 * ## Custom Plugin Registration
 *
 * Plugins must be registered via `META-INF/services/io.github.ktakashi.lemoncheck.plugin.LemonCheckPlugin`:
 *
 * ```text
 * com.example.myplugin.MyCustomPlugin
 * com.example.reporting.HtmlReportPlugin
 * ```
 *
 * ## Path Parameter Support
 *
 * If a path parameter is provided (e.g., `report:json:path/to/file.json`), the resolver
 * attempts to instantiate the plugin using a constructor that accepts `java.nio.file.Path`.
 * If no such constructor exists, the no-arg constructor is used.
 */
object PluginNameResolver {
    /**
     * Lazy-loaded plugins discovered via ServiceLoader.
     *
     * Plugins are cached to avoid repeated ServiceLoader lookups. Each discovered
     * plugin class is instantiated once and reused for subsequent lookups.
     */
    private val discoveredPlugins: List<LemonCheckPlugin> by lazy {
        ServiceLoader
            .load(LemonCheckPlugin::class.java)
            .toList()
    }

    /**
     * Resolve a plugin name to a plugin instance.
     *
     * @param pluginName Plugin ID or name, optionally followed by colon-separated path parameter
     * @return Plugin instance
     * @throws IllegalArgumentException if plugin is not found
     */
    fun resolve(pluginName: String): LemonCheckPlugin {
        // Parse plugin name and optional path parameter
        // Format: <plugin-id-or-name>[:<path>]
        val colonIndex = findParameterSeparator(pluginName)

        return if (colonIndex != -1) {
            val baseId = pluginName.substring(0, colonIndex)
            val pathParam = pluginName.substring(colonIndex + 1)
            resolveWithPath(baseId, Path.of(pathParam))
        } else {
            resolveByIdOrName(pluginName)
        }
    }

    /**
     * Find the colon that separates plugin ID from path parameter.
     *
     * For IDs like "report:json:path", the first two parts are the ID,
     * and the third part (if present) is the path parameter.
     *
     * @return Index of the separator colon, or -1 if no path parameter
     */
    private fun findParameterSeparator(pluginName: String): Int {
        // Count colons to determine if there's a path parameter
        val colons = pluginName.indices.filter { pluginName[it] == ':' }

        // If we have at least 2 colons, the last colon separates the path
        // e.g., "report:json:path/file" -> id="report:json", path="path/file"
        return if (colons.size >= 2) {
            colons.last()
        } else {
            // Single colon means it's part of the ID (e.g., "sample:logging")
            -1
        }
    }

    /**
     * Resolves a plugin by ID or name without additional parameters.
     */
    private fun resolveByIdOrName(pluginName: String): LemonCheckPlugin {
        // Try matching by ID first
        val byId = discoveredPlugins.find { it.id == pluginName }
        if (byId != null) {
            return newPluginInstance(byId)
        }

        // Then try matching by name
        val byName = discoveredPlugins.find { it.name == pluginName }
        if (byName != null) {
            return newPluginInstance(byName)
        }

        throwUnknownPluginException(pluginName)
    }

    /**
     * Resolves a plugin by ID or name with a Path parameter.
     */
    private fun resolveWithPath(
        baseId: String,
        path: Path,
    ): LemonCheckPlugin {
        // Try matching by ID first
        val byId = discoveredPlugins.find { it.id == baseId }
        if (byId != null) {
            return newPluginInstanceWithPath(byId, path)
        }

        // Then try matching by name
        val byName = discoveredPlugins.find { it.name == baseId }
        if (byName != null) {
            return newPluginInstanceWithPath(byName, path)
        }

        throwUnknownPluginException(baseId)
    }

    /** Creates a new instance of the plugin using its no-arg constructor. */
    private fun newPluginInstance(plugin: LemonCheckPlugin) = plugin::class.java.getDeclaredConstructor().newInstance()

    /**
     * Creates a new instance of a plugin with Path parameter.
     *
     * Looks for a constructor where the first parameter is Path.
     * Kotlin classes with default parameters generate constructors with
     * additional synthetic parameters, so we find a constructor that
     * starts with Path and provide defaults for remaining parameters.
     */
    private fun newPluginInstanceWithPath(
        plugin: LemonCheckPlugin,
        path: Path,
    ): LemonCheckPlugin {
        val pluginClass = plugin::class.java

        // Find constructor where first parameter is Path
        val pathConstructor =
            pluginClass.declaredConstructors.find { constructor ->
                constructor.parameterTypes.isNotEmpty() &&
                    constructor.parameterTypes[0] == Path::class.java
            }

        if (pathConstructor != null) {
            // Build args array with Path as first arg, defaults for rest
            val args =
                pathConstructor.parameterTypes
                    .mapIndexed { index, paramType ->
                        when {
                            index == 0 -> path
                            paramType == Boolean::class.java || paramType == java.lang.Boolean.TYPE -> true
                            paramType == Int::class.java || paramType == Integer.TYPE -> 0
                            paramType == String::class.java -> ""
                            else -> null
                        }
                    }.toTypedArray()

            return pathConstructor.newInstance(*args) as LemonCheckPlugin
        }

        // Fall back to no-arg constructor
        return pluginClass.getDeclaredConstructor().newInstance()
    }

    /**
     * Throws an exception with helpful error message listing available plugins.
     */
    private fun throwUnknownPluginException(pluginName: String): Nothing {
        val availableIds = discoveredPlugins.map { it.id }
        val availableNames =
            discoveredPlugins
                .map { it.name }
                .filter { it != it::class.simpleName } // Only show explicit names
        throw ConfigurationException(
            "Unknown plugin: '$pluginName'. " +
                "Available plugin IDs: $availableIds. " +
                if (availableNames.isNotEmpty()) "Available plugin names: $availableNames." else "",
        )
    }
}
