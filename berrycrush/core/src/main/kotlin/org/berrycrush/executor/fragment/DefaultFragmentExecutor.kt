package org.berrycrush.executor.fragment

import org.berrycrush.exception.ConfigurationException
import org.berrycrush.model.Fragment
import org.berrycrush.model.FragmentRegistry
import org.berrycrush.model.ParameterFragment
import org.berrycrush.model.Step
import org.berrycrush.model.includeDirective
import org.berrycrush.plugin.ScenarioContext

/**
 * Default implementation of [FragmentExecutor] for expanding fragment references.
 *
 * This implementation handles:
 * - Looking up fragments in the registry
 * - Injecting include parameters into execution context
 * - Variable interpolation in parameter values
 *
 * @property fragmentRegistry Registry for looking up fragments by name
 */
class DefaultFragmentExecutor(
    private val fragmentRegistry: FragmentRegistry?,
) : FragmentExecutor {
    /**
     * Expand a step by resolving any fragment references.
     *
     * If the step references a fragment (via fragmentName), returns the steps
     * from that fragment. Otherwise, returns a list containing just the original step.
     *
     * @param step The step to expand
     * @return List of steps to execute (fragment steps or original step)
     */
    override fun expand(
        step: Step,
        context: ScenarioContext?,
    ): List<Step> {
        val fragment = resolveFragment(step, context) ?: return listOf(step)
        return fragment.steps
    }

    override fun includeParameters(
        step: Step,
        context: ScenarioContext?,
    ): Map<String, Any?> {
        val includeParameters = step.includeDirective?.parameters ?: emptyMap()
        return fragmentRegistry?.resolveParameters(includeParameters) ?: includeParameters
    }

    private fun resolveFragment(
        step: Step,
        context: ScenarioContext?,
    ): Fragment? {
        val fragmentName = step.includeDirective?.fragmentName?.let { context.interpolate(it) } ?: return null
        return fragmentRegistry?.get(fragmentName)
            ?: throw ConfigurationException(
                "Fragment '$fragmentName' not found. " +
                    "Register it with fragmentRegistry.register() or load from a .fragment file.",
            )
    }

    private fun ScenarioContext?.interpolate(value: String): String = this?.executionContext?.interpolate(value) ?: value
}
