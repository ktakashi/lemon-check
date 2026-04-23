package org.berrycrush.junit.engine

import org.berrycrush.assertion.AnnotationAssertionScanner
import org.berrycrush.assertion.AssertionRegistry
import org.berrycrush.assertion.DefaultAssertionRegistry
import org.berrycrush.junit.BerryCrushConfiguration
import org.berrycrush.step.AnnotationStepScanner
import org.berrycrush.step.DefaultStepRegistry
import org.berrycrush.step.PackageStepScanner
import org.berrycrush.step.StepRegistry
import kotlin.reflect.KClass

/**
 * Factory for creating StepRegistry and AssertionRegistry from @BerryCrushConfiguration.
 *
 * This class provides shared logic for both BerryCrushExtension and ScenarioTestExecutor
 * to avoid code duplication.
 */
object RegistryFactory {
    /**
     * Create a StepRegistry by scanning step classes and packages from configuration.
     *
     * @param stepClasses Array of classes containing @Step annotated methods
     * @param stepPackages Array of package names to scan for step classes
     * @return StepRegistry if any steps were found, null otherwise
     */
    fun createStepRegistry(
        stepClasses: Array<KClass<*>>,
        stepPackages: Array<String> = emptyArray(),
    ): StepRegistry? {
        if (stepClasses.isEmpty() && stepPackages.isEmpty()) {
            return null
        }

        val registry = DefaultStepRegistry()
        val scanner = AnnotationStepScanner()

        // Scan step classes
        stepClasses.forEach { klass ->
            runCatching {
                scanner.scan(klass.java).forEach { definition ->
                    registry.register(definition)
                }
            }.onFailure { e ->
                System.err.println(
                    "Warning: Failed to scan step class ${klass.qualifiedName}: ${e.message}",
                )
            }
        }

        // Scan step packages
        if (stepPackages.isNotEmpty()) {
            val packageScanner = PackageStepScanner()
            stepPackages.forEach { packageName ->
                runCatching {
                    packageScanner.scan(packageName).forEach { definition ->
                        registry.register(definition)
                    }
                }.onFailure { e ->
                    System.err.println(
                        "Warning: Failed to scan step package $packageName: ${e.message}",
                    )
                }
            }
        }

        return if (registry.allDefinitions().isEmpty()) null else registry
    }

    /**
     * Create a StepRegistry from a @BerryCrushConfiguration annotation.
     *
     * @param config The configuration annotation
     * @return StepRegistry if any steps were found, null otherwise
     */
    fun createStepRegistry(config: BerryCrushConfiguration?): StepRegistry? {
        config ?: return null
        return createStepRegistry(config.stepClasses, config.stepPackages)
    }

    /**
     * Create a StepRegistry by scanning the test class for @BerryCrushConfiguration.
     *
     * @param testClass The test class to check for configuration
     * @return StepRegistry if any steps were found, null otherwise
     */
    fun createStepRegistry(testClass: Class<*>): StepRegistry? {
        val config = testClass.getAnnotation(BerryCrushConfiguration::class.java)
        return createStepRegistry(config)
    }

    /**
     * Create an AssertionRegistry by scanning assertion classes from configuration.
     *
     * @param assertionClasses Array of classes containing @Assertion annotated methods
     * @param assertionPackages Array of package names (not currently implemented)
     * @return AssertionRegistry if any assertions were found, null otherwise
     */
    fun createAssertionRegistry(
        assertionClasses: Array<KClass<*>>,
        assertionPackages: Array<String> = emptyArray(),
    ): AssertionRegistry? {
        if (assertionClasses.isEmpty() && assertionPackages.isEmpty()) {
            return null
        }

        val registry = DefaultAssertionRegistry()
        val scanner = AnnotationAssertionScanner()

        // Scan assertion classes
        assertionClasses.forEach { klass ->
            runCatching {
                scanner.scan(klass.java).forEach { definition ->
                    registry.register(definition)
                }
            }.onFailure { e ->
                System.err.println(
                    "Warning: Failed to scan assertion class ${klass.qualifiedName}: ${e.message}",
                )
            }
        }

        // TODO: Implement package scanning for assertions if needed
        // For now, only class-based registration is supported

        return if (registry.allDefinitions().isEmpty()) null else registry
    }

    /**
     * Create an AssertionRegistry from a @BerryCrushConfiguration annotation.
     *
     * @param config The configuration annotation
     * @return AssertionRegistry if any assertions were found, null otherwise
     */
    fun createAssertionRegistry(config: BerryCrushConfiguration?): AssertionRegistry? {
        config ?: return null
        return createAssertionRegistry(config.assertionClasses, config.assertionPackages)
    }

    /**
     * Create an AssertionRegistry by scanning the test class for @BerryCrushConfiguration.
     *
     * @param testClass The test class to check for configuration
     * @return AssertionRegistry if any assertions were found, null otherwise
     */
    fun createAssertionRegistry(testClass: Class<*>): AssertionRegistry? {
        val config = testClass.getAnnotation(BerryCrushConfiguration::class.java)
        return createAssertionRegistry(config)
    }
}
