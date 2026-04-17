package org.berrycrush.step

import org.berrycrush.config.BerryCrushConfiguration
import org.berrycrush.context.ExecutionContext
import java.net.http.HttpResponse

/**
 * Default implementation of [StepContext] that wraps an [ExecutionContext].
 *
 * Provides access to test variables, last HTTP response, and configuration
 * for custom step implementations.
 *
 * @property executionContext The underlying execution context
 * @property configuration The BerryCrush configuration
 * @property sharedVariables Optional shared variables map for suite-scoped variables
 * @property sharingEnabled Whether variable sharing is enabled
 */
class StepContextImpl(
    private val executionContext: ExecutionContext,
    override val configuration: BerryCrushConfiguration,
    private val sharedVariables: MutableMap<String, Any?>? = null,
    private val sharingEnabled: Boolean = false,
) : StepContext {
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

    override fun setVariable(
        name: String,
        value: Any?,
    ) {
        if (value != null) {
            executionContext[name] = value
        }
        // Note: ExecutionContext doesn't support null values, so we just don't set
    }

    override fun setSharedVariable(
        name: String,
        value: Any?,
    ) {
        if (sharingEnabled && sharedVariables != null) {
            sharedVariables[name] = value
        } else {
            // Fall back to scenario-scoped if sharing not enabled
            setVariable(name, value)
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
