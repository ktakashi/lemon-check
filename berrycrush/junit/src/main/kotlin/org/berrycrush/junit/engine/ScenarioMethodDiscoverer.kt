package org.berrycrush.junit.engine

import org.berrycrush.junit.ScenarioTest
import org.berrycrush.model.Scenario
import org.junit.jupiter.api.Disabled
import org.junit.platform.engine.support.descriptor.EngineDescriptor

/**
 * Discovers @ScenarioTest methods in test classes.
 *
 * This discoverer finds methods annotated with [ScenarioTest] and creates
 * [ScenarioMethodDescriptor] test descriptors for them.
 */
object ScenarioMethodDiscoverer {
    /**
     * Discovers @ScenarioTest methods for a test class and adds them to the engine descriptor.
     */
    fun discoverScenariosForClass(
        engineDescriptor: EngineDescriptor,
        testClass: Class<*>,
    ) {
        if (testClass.isAnnotationPresent(Disabled::class.java)) return

        // Check if class is already discovered (might have been added by ScenarioTestDiscoverer)
        val existingClassDescriptor =
            engineDescriptor.children
                .filterIsInstance<ClassTestDescriptor>()
                .find { it.testClass == testClass }

        val classDescriptor =
            existingClassDescriptor ?: run {
                val newDescriptor =
                    ClassTestDescriptor(
                        uniqueId = engineDescriptor.uniqueId.append("class", testClass.name),
                        testClass = testClass,
                    )
                engineDescriptor.addChild(newDescriptor)
                newDescriptor
            }

        // Find all @Scenario methods
        testClass.declaredMethods
            .filter { it.isAnnotationPresent(ScenarioTest::class.java) }
            .filter { !it.isAnnotationPresent(Disabled::class.java) }
            .filter { Scenario::class.java.isAssignableFrom(it.returnType) }
            .forEach { method ->
                val methodId = classDescriptor.uniqueId.append("scenario", method.name)
                val displayName = formatDisplayName(method.name)
                val descriptor =
                    ScenarioMethodDescriptor(
                        uniqueId = methodId,
                        displayName = displayName,
                        method = method,
                        testClass = testClass,
                    )
                classDescriptor.addChild(descriptor)
            }
    }

    /**
     * Format a method name as a human-readable display name.
     *
     * Converts camelCase or snake_case to "Title Case With Spaces".
     */
    private fun formatDisplayName(methodName: String): String {
        // Handle names with spaces (from backtick-wrapped Kotlin methods)
        // Kotlin compiles `list all pets` to "list all pets" (with spaces, no backticks)
        if (methodName.contains(" ")) {
            return methodName.replaceFirstChar { it.uppercase() }
        }

        // Convert camelCase to words
        return methodName
            .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
            .replace("_", " ")
            .trim()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}
