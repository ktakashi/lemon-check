package io.github.ktakashi.lemoncheck.step

import java.io.File
import java.net.JarURLConnection
import java.util.jar.JarFile

/**
 * Scans packages for classes containing @Step annotated methods.
 *
 * Uses classpath reflection to discover step definition classes in specified packages.
 */
class PackageStepScanner {
    private val annotationScanner = AnnotationStepScanner()

    /**
     * Scans a package for step definitions.
     *
     * @param packageName The package name to scan
     * @param classLoader The class loader to use (defaults to thread context class loader)
     * @return List of step definitions found in the package
     */
    fun scan(
        packageName: String,
        classLoader: ClassLoader? = null,
    ): List<StepDefinition> {
        val loader = classLoader ?: Thread.currentThread().contextClassLoader
        val classes = findClasses(packageName, loader)

        return classes
            .filter { clazz -> clazz.declaredMethods.any { it.isAnnotationPresent(Step::class.java) } }
            .flatMap { annotationScanner.scan(it) }
    }

    /**
     * Scans multiple packages for step definitions.
     *
     * @param packageNames The package names to scan
     * @param classLoader The class loader to use
     * @return List of all step definitions found
     */
    fun scanAll(
        vararg packageNames: String,
        classLoader: ClassLoader? = null,
    ): List<StepDefinition> = packageNames.flatMap { scan(it, classLoader) }

    private fun findClasses(
        packageName: String,
        classLoader: ClassLoader,
    ): List<Class<*>> {
        val path = packageName.replace('.', '/')
        val resources = classLoader.getResources(path)

        return generateSequence { resources.takeIf { it.hasMoreElements() }?.nextElement() }
            .flatMap { resource ->
                when (resource.protocol) {
                    "file" -> {
                        val directory = File(resource.toURI())
                        findClassesInDirectory(directory, packageName, classLoader).asSequence()
                    }
                    "jar" -> {
                        val connection = resource.openConnection() as JarURLConnection
                        findClassesInJar(connection.jarFile, packageName, classLoader).asSequence()
                    }
                    else -> emptySequence()
                }
            }.toList()
    }

    private fun findClassesInDirectory(
        directory: File,
        packageName: String,
        classLoader: ClassLoader,
    ): List<Class<*>> {
        if (!directory.exists()) return emptyList()

        val files = directory.listFiles() ?: return emptyList()

        return files.flatMap { file ->
            when {
                file.isDirectory ->
                    findClassesInDirectory(file, "$packageName.${file.name}", classLoader)
                file.name.endsWith(".class") -> {
                    val className = "$packageName.${file.name.removeSuffix(".class")}"
                    listOfNotNull(
                        runCatching { classLoader.loadClass(className) }.getOrNull(),
                    )
                }
                else -> emptyList()
            }
        }
    }

    private fun findClassesInJar(
        jarFile: JarFile,
        packageName: String,
        classLoader: ClassLoader,
    ): List<Class<*>> {
        val path = packageName.replace('.', '/')

        return jarFile
            .entries()
            .asSequence()
            .filter { it.name.startsWith(path) && it.name.endsWith(".class") }
            .mapNotNull { entry ->
                val className = entry.name.removeSuffix(".class").replace('/', '.')
                runCatching { classLoader.loadClass(className) }.getOrNull()
            }.toList()
    }
}
