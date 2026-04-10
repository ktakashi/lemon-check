package io.github.ktakashi.lemoncheck.step

import java.lang.reflect.Modifier

/**
 * Scans classes for methods annotated with [@Step].
 *
 * Extracts step definitions from annotated methods in provided classes.
 */
class AnnotationStepScanner {
    /**
     * Scans a class for @Step annotated methods.
     *
     * @param clazz The class to scan
     * @param instance Optional instance for non-static methods (created if null)
     * @return List of step definitions found in the class
     */
    fun scan(
        clazz: Class<*>,
        instance: Any? = null,
    ): List<StepDefinition> {
        val actualInstance = instance ?: createInstance(clazz)

        return clazz.declaredMethods
            .mapNotNull { method ->
                method.getAnnotation(Step::class.java)?.let { annotation ->
                    if (!Modifier.isPublic(method.modifiers)) {
                        method.isAccessible = true
                    }
                    StepDefinition(
                        pattern = annotation.pattern,
                        method = method,
                        instance = if (Modifier.isStatic(method.modifiers)) null else actualInstance,
                        description = annotation.description,
                    )
                }
            }
    }

    /**
     * Scans multiple classes for @Step annotated methods.
     *
     * @param classes The classes to scan
     * @return List of all step definitions found
     */
    fun scanAll(vararg classes: Class<*>): List<StepDefinition> = classes.flatMap { scan(it) }

    /**
     * Scans multiple classes with their instances for @Step annotated methods.
     *
     * @param instances The class instances to scan
     * @return List of all step definitions found
     */
    fun scanInstances(vararg instances: Any): List<StepDefinition> = instances.flatMap { scan(it.javaClass, it) }

    private fun createInstance(clazz: Class<*>): Any? =
        runCatching {
            val constructor = clazz.getDeclaredConstructor()
            if (!Modifier.isPublic(constructor.modifiers)) {
                constructor.isAccessible = true
            }
            constructor.newInstance()
        }.getOrNull()
}
