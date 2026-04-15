package org.berrycrush.assertion

import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.context.ExecutionContext
import java.net.http.HttpResponse

/**
 * Default implementation of [AssertionContext] that wraps an [ExecutionContext].
 *
 * Provides read-only access to test variables, last HTTP response, and configuration
 * for custom assertion implementations.
 *
 * @property executionContext The underlying execution context
 * @property configuration The BerryCrush configuration
 * @property sharedVariables Optional shared variables map
 * @property sharingEnabled Whether variable sharing is enabled
 */
class AssertionContextImpl(
    private val executionContext: ExecutionContext,
    override val configuration: BerryCrushConfiguration,
    private val sharedVariables: Map<String, Any?>? = null,
    private val sharingEnabled: Boolean = false,
) : AssertionContext {
    override fun variable(name: String): Any? {
        // First check scenario-scoped variables
        val scenarioValue = executionContext.get<Any?>(name)
        if (scenarioValue != null) {
            return scenarioValue
        }

        // Then check shared variables if sharing is enabled
        return if (sharingEnabled && sharedVariables != null) {
            sharedVariables[name]
        } else {
            null
        }
    }

    override fun allVariables(): Map<String, Any?> {
        val allVars = executionContext.allVariables().toMutableMap()
        if (sharingEnabled && sharedVariables != null) {
            // Shared variables are included but can be overridden by scenario variables
            sharedVariables.forEach { (key, value) ->
                if (!allVars.containsKey(key)) {
                    allVars[key] = value
                }
            }
        }
        return allVars
    }

    override val lastResponse: HttpResponse<String>?
        get() = executionContext.lastResponse
}
