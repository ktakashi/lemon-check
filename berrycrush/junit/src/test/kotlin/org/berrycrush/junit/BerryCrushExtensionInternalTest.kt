package org.berrycrush.junit

import org.berrycrush.executor.BerryCrushScenarioExecutor
import org.berrycrush.model.Scenario
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BerryCrushExtensionInternalTest {
    @Test
    fun `collectSpecs should read repeatable and single annotations without duplicates`() {
        val extension = BerryCrushExtension()
        val specs = extension.collectSpecs(MultiSpecClass::class)

        assertEquals(2, specs.size)
        assertTrue(specs.any { it.name == "default" && "test-api.yaml" in it.paths })
        assertTrue(specs.any { it.name == "auth" && "auth-api.yaml" in it.paths })
    }

    @Test
    fun `extension should resolve supported parameters and recreate executor after beforeEach`() {
        val extension = BerryCrushExtension()
        val context = extensionContext(ExtensionTargetClass::class.java)

        extension.beforeAll(context)

        val suiteParam = parameterContext(parameterMethod("suiteParam"), 0)
        val configParam = parameterContext(parameterMethod("configParam"), 0)
        val executorParam = parameterContext(parameterMethod("executorParam"), 0)

        assertTrue(extension.supportsParameter(suiteParam, context))
        assertTrue(extension.supportsParameter(configParam, context))
        assertTrue(extension.supportsParameter(executorParam, context))

        val suite = extension.resolveParameter(suiteParam, context)
        val configuration = extension.resolveParameter(configParam, context)
        val firstExecutor = extension.resolveParameter(executorParam, context)

        assertTrue(suite.javaClass.name.contains("BerryCrushSuite"))
        assertTrue(configuration.javaClass.name.contains("BerryCrushConfiguration"))
        assertTrue(firstExecutor.javaClass.name.contains("BerryCrushScenarioExecutor"))

        extension.beforeEach(context)
        val secondExecutor = extension.resolveParameter(executorParam, context)
        assertNotEquals(firstExecutor, secondExecutor)
    }

    @Test
    fun `supportsParameter should reject unsupported types`() {
        val extension = BerryCrushExtension()
        val context = extensionContext(ExtensionTargetClass::class.java)
        val unsupported = parameterContext(parameterMethod("unsupportedParam"), 0)

        assertTrue(!extension.supportsParameter(unsupported, context))
    }

    @Test
    fun `supportsTestTemplate should honor method or class level annotation`() {
        val extension = BerryCrushExtension()

        val classAnnotatedContext =
            extensionContext(
                testClass = ClassLevelTemplateClass::class.java,
                testMethod = ClassLevelTemplateClass::class.java.getDeclaredMethod("regularTestMethod"),
            )
        val methodAnnotatedContext =
            extensionContext(
                testClass = MethodLevelTemplateClass::class.java,
                testMethod = MethodLevelTemplateClass::class.java.getDeclaredMethod("templateMethod"),
            )
        val plainContext =
            extensionContext(
                testClass = PlainTemplateClass::class.java,
                testMethod = PlainTemplateClass::class.java.getDeclaredMethod("regularMethod"),
            )

        assertTrue(extension.supportsTestTemplate(classAnnotatedContext))
        assertTrue(extension.supportsTestTemplate(methodAnnotatedContext))
        assertTrue(!extension.supportsTestTemplate(plainContext))
    }

    @Test
    fun `beforeAll should reuse parent suite for nested context`() {
        val extension = BerryCrushExtension()
        val parent = extensionContext(ExtensionTargetClass::class.java)
        val child = extensionContext(ExtensionTargetClass::class.java, parent = parent)

        extension.beforeAll(parent)
        val parentSuite = extension.resolveParameter(parameterContext(parameterMethod("suiteParam"), 0), parent)

        extension.beforeAll(child)
        val childSuite = extension.resolveParameter(parameterContext(parameterMethod("suiteParam"), 0), child)

        assertEquals(parentSuite, childSuite)
    }

    @Test
    fun `provideTestTemplateInvocationContexts should expose scenario resolver`() {
        val extension = BerryCrushExtension()
        val context =
            extensionContext(
                testClass = MethodLevelTemplateClass::class.java,
                testMethod = MethodLevelTemplateClass::class.java.getDeclaredMethod("templateMethod"),
            )
        extension.beforeAll(context)

        val suiteParam = parameterContext(parameterMethod("suiteParam"), 0)
        val suite = extension.resolveParameter(suiteParam, context) as BerryCrushSuite
        val scenario =
            Scenario(name = "invocation scenario").apply {
                suite.add(this)
            }

        val invocations = extension.provideTestTemplateInvocationContexts(context).toList()
        assertEquals(1, invocations.size)
        assertEquals("invocation scenario", invocations.single().getDisplayName(1))

        val additionalExtensions = invocations.single().additionalExtensions
        assertTrue(additionalExtensions.isNotEmpty())
        val scenarioResolver = additionalExtensions.single() as org.junit.jupiter.api.extension.ParameterResolver

        val scenarioParam = parameterContext(parameterMethod("scenarioParam"), 0)
        assertTrue(scenarioResolver.supportsParameter(scenarioParam, context))
        assertEquals(scenario, scenarioResolver.resolveParameter(scenarioParam, context))

        val unsupportedParam = parameterContext(parameterMethod("unsupportedParam"), 0)
        assertTrue(!scenarioResolver.supportsParameter(unsupportedParam, context))
        assertNotNull(scenarioResolver.resolveParameter(scenarioParam, context))
    }

    private fun parameterMethod(name: String): Method = ParameterFixtures::class.java.methods.first { it.name == name }

    private fun extensionContext(
        testClass: Class<*>,
        parent: ExtensionContext? = null,
        testMethod: Method? = null,
    ): ExtensionContext {
        val store = createStoreProxy()
        val parentOptional = Optional.ofNullable(parent)
        val testClassOptional = Optional.of(testClass)
        val methodOptional = Optional.ofNullable(testMethod)

        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ExtensionContext::class.java),
            contextInvocationHandler(store, parentOptional, testClass, testClassOptional, testMethod, methodOptional),
        ) as ExtensionContext
    }

    private fun createStoreProxy(): ExtensionContext.Store {
        val storeData = mutableMapOf<Any, Any>()
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ExtensionContext.Store::class.java),
        ) { _, method, args ->
            when (method.name) {
                "get" -> handleStoreGet(storeData, args)
                "put" -> handleStorePut(storeData, args)
                "remove" -> handleStoreRemove(storeData, args)
                "getOrComputeIfAbsent", "computeIfAbsent" -> handleStoreCompute(storeData, args)
                else -> defaultValueFor(method.returnType)
            }
        } as ExtensionContext.Store
    }

    private fun handleStoreGet(
        storeData: MutableMap<Any, Any>,
        args: Array<Any?>?,
    ): Any? {
        val key = requireNotNull(args!![0])
        val value = storeData[key]
        return if (args.size == 2 && args[1] is Class<*>) {
            (args[1] as Class<*>).cast(value)
        } else {
            value
        }
    }

    private fun handleStorePut(
        storeData: MutableMap<Any, Any>,
        args: Array<Any?>?,
    ): Any? {
        val key = requireNotNull(args!![0])
        val value = args[1]
        if (value == null) {
            storeData.remove(key)
        } else {
            storeData[key] = value
        }
        return null
    }

    private fun handleStoreRemove(
        storeData: MutableMap<Any, Any>,
        args: Array<Any?>?,
    ): Any? {
        val key = requireNotNull(args!![0])
        val removed = storeData.remove(key)
        return if (args.size == 2 && args[1] is Class<*>) {
            (args[1] as Class<*>).cast(removed)
        } else {
            removed
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleStoreCompute(
        storeData: MutableMap<Any, Any>,
        args: Array<Any?>?,
    ): Any? {
        val key = requireNotNull(args!![0])
        val creator = args[1] as java.util.function.Function<Any, Any?>
        val value = storeData.getOrPut(key) { creator.apply(key) ?: Unit }
        return if (args.size == 3 && args[2] is Class<*>) {
            (args[2] as Class<*>).cast(value)
        } else {
            value
        }
    }

    private fun contextInvocationHandler(
        store: ExtensionContext.Store,
        parentOptional: Optional<ExtensionContext>,
        testClass: Class<*>,
        testClassOptional: Optional<Class<*>>,
        testMethod: Method?,
        methodOptional: Optional<Method>,
    ): java.lang.reflect.InvocationHandler =
        java.lang.reflect.InvocationHandler { _, method, _ ->
            when (method.name) {
                "getStore" -> store
                "getParent" -> parentOptional
                "getTestClass" -> testClassOptional
                "getRequiredTestClass" -> testClass
                "getTestMethod" -> methodOptional
                "getRequiredTestMethod" -> testMethod ?: throw IllegalStateException("No test method")
                "getUniqueId" -> "test-unique-id"
                "getDisplayName" -> "test-display-name"
                "getTags" -> emptySet<String>()
                else -> defaultValueFor(method.returnType)
            }
        }

    private fun parameterContext(
        method: Method,
        index: Int,
    ): ParameterContext {
        val parameter = method.parameters[index]

        val handler =
            java.lang.reflect.InvocationHandler { _, invoked, _ ->
                when (invoked.name) {
                    "getParameter" -> parameter
                    "getIndex" -> index
                    "isAnnotated" -> false
                    "findAnnotation" -> Optional.empty<Annotation>()
                    "findRepeatableAnnotations" -> emptyList<Annotation>()
                    else -> defaultValueFor(invoked.returnType)
                }
            }

        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ParameterContext::class.java),
            handler,
        ) as ParameterContext
    }

    private fun defaultValueFor(returnType: Class<*>): Any? =
        when {
            returnType == Boolean::class.javaPrimitiveType -> false
            returnType == Int::class.javaPrimitiveType -> 0
            returnType == Long::class.javaPrimitiveType -> 0L
            returnType == Double::class.javaPrimitiveType -> 0.0
            returnType == Float::class.javaPrimitiveType -> 0f
            returnType == Short::class.javaPrimitiveType -> 0.toShort()
            returnType == Byte::class.javaPrimitiveType -> 0.toByte()
            returnType == Char::class.javaPrimitiveType -> 0.toChar()
            Optional::class.java.isAssignableFrom(returnType) -> Optional.empty<Any>()
            Set::class.java.isAssignableFrom(returnType) -> emptySet<Any>()
            List::class.java.isAssignableFrom(returnType) -> emptyList<Any>()
            else -> null
        }
}

@BerryCrushSpecs(
    BerryCrushSpec(paths = ["test-api.yaml"], name = "default"),
    BerryCrushSpec(paths = ["auth-api.yaml"], name = "auth"),
)
private class MultiSpecClass

@BerryCrushSpec(paths = ["classpath:/test-api.yaml"], baseUrl = "http://localhost:8080")
private class ExtensionTargetClass

@BerryCrushScenarios("scenarios/*.scenario")
@Disabled("fixture class for extension branch tests")
private class ClassLevelTemplateClass {
    fun regularTestMethod() = Unit
}

@Disabled("fixture class for extension branch tests")
private class MethodLevelTemplateClass {
    @BerryCrushScenarios("scenarios/*.scenario")
    fun templateMethod() = Unit
}

@Disabled("fixture class for extension branch tests")
private class PlainTemplateClass {
    fun regularMethod() = Unit
}

@Suppress("UnusedParameter")
private class ParameterFixtures {
    fun suiteParam(unused: BerryCrushSuite) = Unit

    fun configParam(unused: org.berrycrush.config.BerryCrushConfiguration) = Unit

    fun executorParam(unused: BerryCrushScenarioExecutor) = Unit

    fun unsupportedParam(unused: String) = Unit

    fun scenarioParam(unused: Scenario) = Unit
}
