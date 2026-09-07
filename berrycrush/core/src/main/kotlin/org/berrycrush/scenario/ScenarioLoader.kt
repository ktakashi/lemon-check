package org.berrycrush.scenario

import org.berrycrush.exception.ScenarioParseException
import org.berrycrush.model.Assertion
import org.berrycrush.model.BodyProperty
import org.berrycrush.model.Condition
import org.berrycrush.model.ConditionalActions
import org.berrycrush.model.ConditionalAssertion
import org.berrycrush.model.Directive
import org.berrycrush.model.ExampleRow
import org.berrycrush.model.Extraction
import org.berrycrush.model.Feature
import org.berrycrush.model.Fragment
import org.berrycrush.model.FragmentEntry
import org.berrycrush.model.ParameterFragment
import org.berrycrush.model.RawRequest
import org.berrycrush.model.Scenario
import org.berrycrush.model.SourceLocation
import org.berrycrush.model.Step
import org.berrycrush.model.StepType
import org.berrycrush.model.Story
import org.berrycrush.model.WebhookConfig
import org.berrycrush.util.FileLoader
import org.berrycrush.util.toNonNullMap
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.collections.mapValues
import org.berrycrush.model.ConditionBranch as ModelConditionBranch
import org.berrycrush.model.LogicalOperator as ModelLogicalOperator

/**
 * Result of loading a scenario file.
 *
 * @property stories List of stories
 * @property parameters Optional file-level configuration parameters
 */
data class ScenarioFileContent(
    val stories: List<Story> = emptyList(),
    val parameters: Map<String, Any> = emptyMap(),
) {
    /**
     * Read-only compatibility accessor for features.
     */
    val features: List<Feature>
        get() = stories.filterIsInstance<Feature>()

    /**
     * Read-only compatibility accessor for standalone top-level scenarios.
     */
    val scenarios: List<Scenario>
        get() = stories.filterIsInstance<Scenario>()
}

/**
 * Loads and transforms scenario files into executable Scenario objects.
 */
object ScenarioLoader {
    /**
     * Parse scenario source and throw if parsing fails.
     */
    private fun parseOrThrow(
        source: String,
        fileName: String?,
    ): Parser.ParserResult {
        val result = Parser.parse(source, fileName)
        if (!result.isSuccess) {
            val errorMessages = result.errors.joinToString("\n") { it.toString() }
            throw ScenarioParseException("Failed to parse scenario file:\n$errorMessages")
        }
        return result
    }

    /**
     * Load scenarios and parameters from a single file.
     *
     * @param path Path to the .scenario file
     * @return ScenarioFileContent containing scenarios and parameters
     */
    fun loadFileContent(path: URI): ScenarioFileContent {
        val content = FileLoader.load(path)
        val fileName = path.schemeSpecificPart.substringAfterLast('/')
        return loadFileContentFromString(content, fileName)
    }

    /**
     * Load scenarios and parameters from a single file.
     *
     * @param path Path to the .scenario file
     * @return ScenarioFileContent containing scenarios and parameters
     */
    fun loadFileContent(path: Path): ScenarioFileContent {
        val content = FileLoader.load(path)
        val fileName = path.fileName.toString()
        return loadFileContentFromString(content, fileName)
    }

    /**
     * Load scenarios and parameters from a string.
     *
     * @param source The scenario file content
     * @param fileName Optional filename for error reporting
     * @return ScenarioFileContent containing scenarios and parameters
     */
    fun loadFileContentFromString(
        source: String,
        fileName: String? = null,
    ): ScenarioFileContent {
        val result = parseOrThrow(source, fileName)
        val ast = result.ast ?: error("Parser returned success without AST")
        val stories =
            ast.stories.map { node ->
                when (node) {
                    is ScenarioNode -> transformScenario(node)
                    is FeatureNode -> transformFeatureGroup(node)
                }
            }

        val parameters = result.ast.parameters?.parameters ?: emptyMap()

        return ScenarioFileContent(
            stories = stories,
            parameters = parameters,
        )
    }

    /**
     * Load fragments from a directory.
     *
     * @param directory Path to directory containing .fragment files
     * @return Map of fragment name to Fragment object
     */
    fun loadFragmentsFromDirectory(directory: Path): Map<String, Fragment> =
        Files
            .walk(directory)
            .filter { it.toString().endsWith(".fragment") }
            .map { loadFragmentsFromFile(it) }
            .reduce(emptyMap()) { acc, map -> acc + map }

    /**
     * Load fragments from a single file.
     *
     * @param path Path to the .fragment file
     * @return Map of fragment name to Fragment object
     */
    fun loadFragmentsFromFile(path: Path): Map<String, Fragment> {
        val content = Files.readString(path)
        val fileName = path.fileName.toString()
        return loadFragmentsFromString(content, fileName)
    }

    /**
     * Load fragments from a string.
     *
     * @param source The fragment file content
     * @param fileName Optional filename for error reporting
     * @return Map of fragment name to Fragment object
     */
    fun loadFragmentsFromString(
        source: String,
        fileName: String? = null,
    ): Map<String, Fragment> =
        loadFragmentEntries(source, fileName)
            .entries
            .filter { (_, value) -> value is Fragment }
            .associate { (name, value) -> name to (value as Fragment) }

    /**
     * Load fragments with resolved fragment-level default parameters.
     */
    fun loadFragmentEntries(
        source: String,
        fileName: String? = null,
    ): Map<String, FragmentEntry> {
        val result = Parser.parseFragment(source, fileName)

        if (!result.isSuccess) {
            val errorMessages = result.errors.joinToString("\n") { it.toString() }
            throw ScenarioParseException("Failed to parse fragment file:\n$errorMessages")
        }

        val ast = result.ast ?: error("Parser returned success without AST")
        val fragments = ast.fragments
        val parameters = ast.parameters
        // parser will raise error if a fragment parameter doesn't have name
        return (
            fragments.map { transformFragment(it) } +
                parameters.map { ParameterFragment(it.name!!, it.values) }
        ).associateBy { e -> e.name }
    }

    private fun transformScenario(
        node: ScenarioNode,
        backgroundSteps: List<Step> = emptyList(),
    ): Scenario {
        val steps = node.steps.flatMap { transformStep(it) }
        val examples = node.examples?.map { transformExampleRow(it) }
        val parameters = node.parameters?.parameters ?: emptyMap()

        return Scenario(
            name = node.name,
            tags = node.tags,
            steps = steps,
            background = backgroundSteps,
            examples = examples,
            parameters = parameters,
            sourceLocation = node.location,
        )
    }

    /**
     * Transform a feature node into a list of scenarios.
     *
     * Background steps from the feature are prepended to each scenario.
     * Feature tags are inherited by scenarios unless the scenario overrides them.
     * Feature parameters are inherited by scenarios, with scenario parameters taking precedence.
     */
    private fun transformFeatureGroup(node: FeatureNode): Feature =
        Feature(
            name = node.name,
            scenarios = transformFeatureScenarios(node),
            tags = node.tags,
            parameters = node.parameters?.parameters ?: emptyMap(),
            sourceLocation = node.location,
        )

    private fun transformFeatureScenarios(node: FeatureNode): List<Scenario> {
        // Transform background steps if present
        val backgroundSteps =
            node.background?.steps?.flatMap { transformStep(it) } ?: emptyList()

        // Get feature-level parameters
        val featureParams = node.parameters?.values ?: emptyMap()

        // Transform each scenario in the feature, prepending background steps
        return node.scenarios.map { scenarioNode ->
            // Merge feature tags with scenario tags (scenario tags take precedence)
            val mergedTags = node.tags + scenarioNode.tags

            // Merge feature parameters with scenario parameters (scenario params take precedence)
            val scenarioParams = scenarioNode.parameters?.values ?: emptyMap()
            val mergedParams = featureParams + scenarioParams

            val include = node.parameters?.includes ?: emptyList()
            // Create merged parameters node
            val mergedParametersNode =
                if (scenarioNode.parameters != null) {
                    val scenarioInclude = scenarioNode.parameters.includes
                    scenarioNode.parameters.copy(
                        values = mergedParams,
                        name = scenarioNode.parameters.name,
                        includes =
                            include + scenarioInclude,
                    )
                } else if (mergedParams.isNotEmpty()) {
                    ParametersNode(mergedParams, node.parameters?.location ?: scenarioNode.location, includes = include)
                } else {
                    null
                }

            transformScenario(
                scenarioNode.copy(tags = mergedTags, parameters = mergedParametersNode),
                backgroundSteps,
            )
        }
    }

    private fun transformFragment(node: FragmentNode): Fragment {
        val steps = node.steps.flatMap { transformStep(it) }

        return Fragment(
            name = node.name,
            steps = steps,
        )
    }

    /**
     * Builder for accumulating step components before finalizing.
     */
    private class StepBuilder(
        private val stepType: StepType,
        private val description: String,
        private val defaultLocation: SourceLocation?,
    ) {
        private val steps = mutableListOf<Step>()
        private var pendingCall: CallNode? = null
        private val extractions = mutableListOf<Extraction>()
        private val assertions = mutableListOf<Assertion>()
        private var failMessage: String? = null

        fun processAction(action: ActionNode) {
            when (action) {
                is CallNode -> processCallNode(action)
                is ExtractNode -> extractions.add(Extraction(action.variableName, action.jsonPath))
                is AssertNode -> assertions.add(transformAssertion(action))
                is IncludeNode -> processIncludeNode(action)
                is ConditionalNode -> assertions.add(transformConditional(action))
                is FailNode -> failMessage = action.message
                is WebhookNode -> processWebhookNode(action)
            }
        }

        private fun processWebhookNode(webhook: WebhookNode) {
            // Webhook nodes are processed during execution, not loading
            // They are stored as a special step for the executor
            finalizePendingCall()
            steps.add(
                Step(
                    type = stepType,
                    description = description,
                    directives =
                        listOf(
                            Directive.WebhookDirective(
                                config =
                                    WebhookConfig(
                                        name = webhook.name,
                                        port = webhook.port,
                                        hooks = webhook.hooks,
                                        scope = webhook.scope,
                                    ),
                                sourceLocation = webhook.location,
                            ),
                        ),
                    sourceLocation = webhook.location,
                ),
            )
        }

        private fun processCallNode(call: CallNode) {
            finalizePendingCall()
            pendingCall = call
        }

        private fun processIncludeNode(include: IncludeNode) {
            finalizePendingCall()
            pendingCall = null
            val parameters = include.parameters.mapValues { extractValue(it.value) }
            steps.add(
                Step(
                    type = stepType,
                    description = "include ${include.fragmentName}",
                    directives =
                        listOf(
                            Directive.IncludeDirective(
                                fragmentName = include.fragmentName,
                                parameters = parameters,
                                sourceLocation = include.location,
                            ),
                        ),
                    sourceLocation = include.location,
                ),
            )
        }

        private fun finalizePendingCall() {
            val call = pendingCall ?: return
            steps.add(buildStepFromCall(call))
            resetAccumulators()
        }

        private fun buildStepFromCall(call: CallNode): Step {
            val pathParams =
                call.parameters
                    .filterKeys { !it.startsWith("query_") }
                    .mapValues { extractValue(it.value) }
            val queryParams =
                call.parameters
                    .filterKeys { it.startsWith("query_") }
                    .mapKeys { it.key.removePrefix("query_") }
                    .mapValues { extractValue(it.value) }

            val directives = mutableListOf<Directive>()
            directives +=
                Directive.CallDirective(
                    operationId = call.operationId,
                    rawRequest = call.rawMethod?.let { method -> call.rawPath?.let { path -> RawRequest(method, path) } },
                    specName = call.specName,
                    pathParams = pathParams.toNonNullMap(),
                    queryParams = queryParams.toNonNullMap(),
                    headers = call.headers.mapValues { extractValue(it.value).toString() },
                    body = call.body?.let { extractStringValue(it) },
                    bodyProperties = call.bodyProperties?.let { transformBodyProperties(it) },
                    bodyFile = call.bodyFile,
                    autoTestConfig = call.autoTestConfig?.let { transformAutoTestConfig(it) },
                    sourceLocation = call.location,
                )
            extractions.forEach { extraction ->
                directives += Directive.ExtractionDirective(extraction = extraction, sourceLocation = call.location)
            }
            assertions.forEach { assertion ->
                directives +=
                    when (assertion) {
                        is Assertion.ConditionalAssertion -> {
                            Directive.ConditionalDirective(
                                assertion = assertion,
                                sourceLocation = assertion.sourceLocation ?: call.location,
                            )
                        }

                        else -> {
                            Directive.AssertionDirective(assertion = assertion, sourceLocation = assertion.sourceLocation ?: call.location)
                        }
                    }
            }
            failMessage?.let { message ->
                directives += Directive.FailDirective(message = message, sourceLocation = call.location)
            }

            return Step(
                type = stepType,
                description = description,
                directives = directives,
                sourceLocation = call.location,
            )
        }

        private fun resetAccumulators() {
            extractions.clear()
            assertions.clear()
            failMessage = null
        }

        private fun hasOrphanedComponents(): Boolean =
            pendingCall == null &&
                (
                    extractions.isNotEmpty() ||
                        assertions.isNotEmpty() ||
                        failMessage != null
                )

        fun build(): List<Step> {
            finalizePendingCall()

            if (hasOrphanedComponents()) {
                steps.add(
                    Step(
                        type = stepType,
                        description = description,
                        directives =
                            buildList {
                                extractions.forEach { extraction ->
                                    add(Directive.ExtractionDirective(extraction = extraction, sourceLocation = defaultLocation))
                                }
                                assertions.forEach { assertion ->
                                    add(
                                        when (assertion) {
                                            is Assertion.ConditionalAssertion -> {
                                                Directive.ConditionalDirective(
                                                    assertion = assertion,
                                                    sourceLocation =
                                                        assertion.sourceLocation ?: defaultLocation,
                                                )
                                            }

                                            else -> {
                                                Directive.AssertionDirective(
                                                    assertion = assertion,
                                                    sourceLocation =
                                                        assertion.sourceLocation ?: defaultLocation,
                                                )
                                            }
                                        },
                                    )
                                }
                                failMessage?.let { message ->
                                    add(Directive.FailDirective(message = message, sourceLocation = defaultLocation))
                                }
                            },
                        sourceLocation = defaultLocation,
                    ),
                )
            }

            return steps.ifEmpty {
                listOf(Step(type = stepType, description = description, sourceLocation = defaultLocation))
            }
        }
    }

    private fun transformStep(node: StepNode): List<Step> {
        val stepType =
            when (node.keyword) {
                StepKeyword.GIVEN -> StepType.GIVEN
                StepKeyword.WHEN -> StepType.WHEN
                StepKeyword.THEN -> StepType.THEN
                StepKeyword.AND -> StepType.AND
                StepKeyword.BUT -> StepType.BUT
            }

        if (node.actions.isEmpty()) {
            return listOf(Step(type = stepType, description = node.description, sourceLocation = node.location))
        }

        val builder = StepBuilder(stepType, node.description, node.location)
        node.actions.forEach { builder.processAction(it) }
        return builder.build()
    }

    /**
     * Transform AST AssertNode to model Assertion.
     *
     * AssertNode uses ConditionNode internally, which is converted to model Condition.
     * This enables shared evaluation logic between assertions and conditionals.
     */
    private fun transformAssertion(node: AssertNode): Assertion {
        val condition = transformCondition(node.condition)
        return condition.toAssertion(describeCondition(condition), node.location)
    }

    /**
     * Create a human-readable description of a condition for reporting.
     */
    private fun describeCondition(condition: Condition): String =
        when (condition) {
            is Condition.Status -> "status ${condition.expected}"

            is Condition.JsonPath -> "${condition.path} ${condition.operator.name.lowercase()} ${condition.expected ?: ""}"

            is Condition.Header -> "header ${condition.name} ${condition.operator.name.lowercase()} ${condition.expected ?: ""}"

            is Condition.Variable -> "${condition.name} ${condition.operator.name.lowercase()} ${condition.expected ?: ""}"

            is Condition.BodyContains -> "body contains \"${condition.text}\""

            is Condition.Schema -> "matches schema"

            is Condition.ResponseTime -> "responseTime < ${condition.duration} ms"

            is Condition.Negated -> "not (${describeCondition(condition.condition)})"

            is Condition.Compound -> "(${describeCondition(
                condition.left,
            )}) ${condition.operator.name} (${describeCondition(condition.right)})"

            is Condition.CustomAssertion -> condition.pattern

            is Condition.Custom -> "<custom predicate>"
        }

    private fun transformExampleRow(node: ExampleRowNode): ExampleRow {
        val values = node.values.mapValues { extractValue(it.value) }
        return ExampleRow(values)
    }

    private fun extractValue(node: ValueNode): Any? =
        when (node) {
            is NullValueNode -> null
            is BooleanValueNode -> node.value
            is StringValueNode -> node.value
            is NumberValueNode -> node.value
            is VariableValueNode -> "{{${node.name}}}"
            is JsonValueNode -> node.json
            is StatusRangeNode -> node.toRange()
        }

    private fun extractStringValue(node: ValueNode): String =
        when (node) {
            is NullValueNode -> "null"
            is BooleanValueNode -> node.value.toString()
            is StringValueNode -> node.value
            is NumberValueNode -> node.value.toString()
            is VariableValueNode -> "{{${node.name}}}"
            is JsonValueNode -> node.json
            is StatusRangeNode -> "${node.base}xx"
        }

    /**
     * Transform AST BodyPropertyValue to model BodyProperty.
     */
    private fun transformBodyProperties(props: Map<String, BodyPropertyValue>): Map<String, BodyProperty> =
        props.mapValues { (_, value) -> transformBodyPropertyValue(value) }

    private fun transformBodyPropertyValue(value: BodyPropertyValue): BodyProperty =
        when (value) {
            is BodyPropertyValue.Simple -> {
                when (val v = value.value) {
                    is JsonValueNode -> BodyProperty.Container(v.json)
                    else -> BodyProperty.Simple(extractValue(value.value))
                }
            }

            is BodyPropertyValue.Nested -> {
                BodyProperty.Nested(transformBodyProperties(value.properties))
            }
        }

    /**
     * Transform AST ConditionalNode to model ConditionalAssertion.
     */
    private fun transformConditional(node: ConditionalNode): ConditionalAssertion {
        val ifBranch = transformConditionBranch(node.ifBranch)
        val elseIfBranches = node.elseIfBranches.map { transformConditionBranch(it) }
        val elseActions = node.elseActions?.let { transformConditionalActions(it) }

        return Assertion.ConditionalAssertion(
            ifBranch = ifBranch,
            elseIfBranches = elseIfBranches,
            elseActions = elseActions,
            sourceLocation = node.location,
        )
    }

    /**
     * Transform AST ConditionBranch to model ConditionBranch.
     */
    private fun transformConditionBranch(branch: ConditionBranch): ModelConditionBranch {
        val condition = transformCondition(branch.condition)
        val actions = transformConditionalActions(branch.actions)
        return ModelConditionBranch(condition = condition, actions = actions)
    }

    /**
     * Transform AST ConditionNode to model Condition.
     */
    private fun transformCondition(node: ConditionNode): Condition =
        when (node) {
            is ConditionNode.StatusCondition -> {
                extractValue(node.expected)?.let {
                    Condition.Status(it)
                } ?: error("Invalid status condition: ${node.location}")
            }

            is ConditionNode.JsonPathCondition -> {
                Condition.JsonPath(
                    path = node.path,
                    operator = node.operator,
                    expected = node.expected?.let { extractValue(it) },
                )
            }

            is ConditionNode.HeaderCondition -> {
                Condition.Header(
                    name = node.headerName,
                    operator = node.operator ?: ConditionOperator.EXISTS,
                    expected = node.expected?.let { extractValue(it) },
                )
            }

            is ConditionNode.VariableCondition -> {
                Condition.Variable(
                    name = node.variableName,
                    operator = node.operator,
                    expected = node.expected?.let { extractValue(it) },
                )
            }

            is ConditionNode.NegatedCondition -> {
                Condition.Negated(condition = transformCondition(node.condition))
            }

            is ConditionNode.CompoundCondition -> {
                Condition.Compound(
                    left = transformCondition(node.left),
                    operator = transformLogicalOperator(node.operator),
                    right = transformCondition(node.right),
                )
            }

            is ConditionNode.BodyContainsCondition -> {
                extractValue(node.text)?.let {
                    Condition.BodyContains(text = it)
                } ?: error("Invalid body contains condition: ${node.location}")
            }

            is ConditionNode.SchemaCondition -> {
                Condition.Schema
            }

            is ConditionNode.ResponseTimeCondition -> {
                extractValue(node.maxMs)?.let {
                    Condition.ResponseTime(duration = it)
                } ?: error("Invalid response time condition: ${node.location}")
            }

            is ConditionNode.CustomAssertionCondition -> {
                Condition.CustomAssertion(pattern = node.pattern)
            }
        }

    /**
     * Transform AST LogicalOperator to model LogicalOperator.
     */
    private fun transformLogicalOperator(op: LogicalOperator): ModelLogicalOperator =
        when (op) {
            LogicalOperator.AND -> ModelLogicalOperator.AND
            LogicalOperator.OR -> ModelLogicalOperator.OR
        }

    /**
     * Transform a list of AST ActionNodes to ConditionalActions.
     */
    private fun transformConditionalActions(actions: List<ActionNode>): ConditionalActions {
        val assertions = mutableListOf<Assertion>()
        val extractions = mutableListOf<Extraction>()
        val conditionals = mutableListOf<Assertion.ConditionalAssertion>()
        var failMessage: String? = null

        for (action in actions) {
            when (action) {
                is AssertNode -> {
                    assertions.add(transformAssertion(action))
                }

                is ExtractNode -> {
                    extractions.add(
                        Extraction(
                            variableName = action.variableName,
                            jsonPath = action.jsonPath,
                        ),
                    )
                }

                is ConditionalNode -> {
                    conditionals.add(transformConditional(action))
                }

                is FailNode -> {
                    failMessage = action.message
                }

                is CallNode, is IncludeNode, is WebhookNode -> {
                    // Calls, includes, and webhooks are not allowed in conditional branches
                    // They should be ignored or logged as a warning
                }
            }
        }

        return ConditionalActions(
            assertions = assertions,
            extractions = extractions,
            failMessage = failMessage,
            nestedConditionals = conditionals,
        )
    }

    /**
     * Transform AST AutoTestConfig to model AutoTestConfig.
     */
    private fun transformAutoTestConfig(config: AutoTestConfig): org.berrycrush.model.AutoTestConfig =
        org.berrycrush.model.AutoTestConfig(
            types = config.types,
            excludes = config.excludes,
        )
}
