package org.berrycrush.executor.fragment

import org.berrycrush.model.Step
import org.berrycrush.model.includeDirective
import org.berrycrush.plugin.ScenarioContext

/**
 * Executor for fragment expansion during scenario execution.
 *
 * Fragments are reusable step groups that can be included in scenarios
 * using the `include` directive.
 */
fun interface FragmentExecutor {
    /**
     * Expand a step that may include a fragment reference.
     *
     * If the step contains a `fragmentName` reference, this method resolves
     * the fragment and returns its expanded steps.
     * Otherwise, returns the original step unchanged.
     *
     * @param step The step to potentially expand
     * @return A list of steps after expansion (single item if no fragment)
     */
    fun expand(step: Step) = expand(step, null)

    /**
     * Expand a step that may include a fragment reference.
     *
     * If the step contains a `fragmentName` reference, this method resolves
     * the fragment and returns its expanded steps.
     * Otherwise, returns the original step unchanged.
     *
     * @param step The step to potentially expand
     * @param context Scenario context to resolve fragment name
     * @return A list of steps after expansion (single item if no fragment)
     */
    fun expand(
        step: Step,
        context: ScenarioContext?,
    ): List<Step>

    /**
     * Resolve effective include parameters for a step.
     *
     * Implementations may combine fragment-level defaults with include-level
     * overrides, where include-level values should take precedence.
     */
    fun includeParameters(
        step: Step,
        context: ScenarioContext?,
    ): Map<String, Any?> = step.includeDirective?.parameters ?: emptyMap()
}
