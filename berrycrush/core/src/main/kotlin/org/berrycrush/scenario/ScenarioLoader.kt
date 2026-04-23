package org.berrycrush.scenario

import org.berrycrush.dsl.BerryCrushSuite
import org.berrycrush.exception.ScenarioParseException
import org.berrycrush.model.Assertion
import org.berrycrush.model.BodyProperty
import org.berrycrush.model.Condition
import org.berrycrush.model.ConditionalActions
import org.berrycrush.model.ConditionalAssertion
import org.berrycrush.model.ExampleRow
import org.berrycrush.model.Extraction
import org.berrycrush.model.Fragment
import org.berrycrush.model.Scenario
import org.berrycrush.model.Step
import org.berrycrush.model.StepType
import java.nio.file.Files
import java.nio.file.Path
import org.berrycrush.model.ConditionBranch as ModelConditionBranch
import org.berrycrush.model.ConditionOperator as ModelConditionOperator
import org.berrycrush.model.LogicalOperator as ModelLogicalOperator

/**
 * Represents a group of scenarios within a feature block.
 *
 * @property name Feature name
 * @property scenarios List of scenarios belonging to this feature
 * @property tags Tags applied to the feature
 * @property parameters Feature-level parameters that override file-level parameters
 */
data class FeatureGroup(
    val name: String,
    val scenarios: List<Scenario>,
    val tags: Set<String> = emptySet(),
    val parameters: Map<String, Any> = emptyMap(),
)

/**
 * Result of loading a scenario file.
 *
 * @property scenarios List of all parsed scenarios (flat, for backward compatibility)
 * @property features List of feature groups (for structured reporting)
 * @property standaloneScenarios Scenarios not in any feature block
 * @property parameters Optional file-level configuration parameters
 */
data class ScenarioFileContent(
    val scenarios: List<Scenario>,
    val features: List<FeatureGroup> = emptyList(),
    val standaloneScenarios: List<Scenario> = emptyList(),
    val parameters: Map<String, Any> = emptyMap(),
)

/**
 * Loads and transforms scenario files into executable Scenario objects.
 */
class ScenarioLoader {
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
     * Load scenarios from a directory.
     *
     * @param directory Path to directory containing .scenario files
     * @return List of parsed Scenario objects
     */
    fun loadScenariosFromDirectory(directory: Path): List<Scenario> =
        Files
            .walk(directory)
            .filter { it.toString().endsWith(".scenario") }
            .flatMap { loadScenariosFromFile(it).stream() }
            .toList()

    /**
     * Load scenarios from a single file.
     *
     * @param path Path to the .scenario file
     * @return List of parsed Scenario objects
     */
    fun loadScenariosFromFile(path: Path): List<Scenario> {
        val content = Files.readString(path)
        val fileName = path.fileName.toString()
        return loadScenariosFromString(content, fileName)
    }

    /**
     * Load scenarios and parameters from a single file.
     *
     * @param path Path to the .scenario file
     * @return ScenarioFileContent containing scenarios and parameters
     */
    fun loadFileContent(path: Path): ScenarioFileContent {
        val content = Files.readString(path)
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

        // Transform standalone scenarios (not in any feature)
        val standaloneScenarios = result.ast!!.scenarios.map { transformScenario(it) }

        // Transform features with their scenarios (with optional background steps prepended)
        val featureGroups =
            result.ast.features.map { feature ->
                FeatureGroup(
                    name = feature.name,
                    scenarios = transformFeature(feature),
                    tags = feature.tags,
                    parameters = feature.parameters?.values ?: emptyMap(),
                )
            }

        // All scenarios in flat list for backward compatibility
        val allScenarios = standaloneScenarios + featureGroups.flatMap { it.scenarios }

        val parameters = result.ast.parameters?.values ?: emptyMap()

        return ScenarioFileContent(
            scenarios = allScenarios,
            features = featureGroups,
            standaloneScenarios = standaloneScenarios,
            parameters = parameters,
        )
    }

    /**
     * Load scenarios from a string.
     *
     * @param source The scenario file content
     * @param fileName Optional filename for error reporting
     * @return List of parsed Scenario objects
     */
    fun loadScenariosFromString(
        source: String,
        fileName: String? = null,
    ): List<Scenario> {
        val result = parseOrThrow(source, fileName)

        // Transform and combine scenarios
        val standaloneScenarios = result.ast!!.scenarios.map { transformScenario(it) }
        val featureScenarios = result.ast.features.flatMap { transformFeature(it) }

        return standaloneScenarios + featureScenarios
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
    ): Map<String, Fragment> {
        val result = Parser.parse(source, fileName)

        if (!result.isSuccess) {
            val errorMessages = result.errors.joinToString("\n") { it.toString() }
            throw ScenarioParseException("Failed to parse fragment file:\n$errorMessages")
        }

        return result.ast!!.fragments.associate { it.name to transformFragment(it) }
    }

    private fun transformScenario(
        node: ScenarioNode,
        backgroundSteps: List<Step> = emptyList(),
    ): Scenario {
        val steps = node.steps.flatMap { transformStep(it) }
        val examples = node.examples?.map { transformExampleRow(it) }

        return Scenario(
            name = node.name,
            tags = node.tags,
            steps = steps,
            background = backgroundSteps,
            examples = examples,
            sourceLocation = node.location,
        )
    }

    /**
     * Transform a feature node into a list of scenarios.
     *
     * Background steps from the feature are prepended to each scenario.
     * Feature tags are inherited by scenarios unless the scenario overrides them.
     */
    private fun transformFeature(node: FeatureNode): List<Scenario> {
        // Transform background steps if present
        val backgroundSteps =
            node.background?.steps?.flatMap { transformStep(it) } ?: emptyList()

        // Transform each scenario in the feature, prepending background steps
        return node.scenarios.map { scenarioNode ->
            // Merge feature tags with scenario tags (scenario tags take precedence)
            val mergedTags = node.tags + scenarioNode.tags
            transformScenario(
                scenarioNode.copy(tags = mergedTags),
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

    private fun transformStep(node: StepNode): List<Step> {
        val stepType =
            when (node.keyword) {
                StepKeyword.GIVEN -> StepType.GIVEN
                StepKeyword.WHEN -> StepType.WHEN
                StepKeyword.THEN -> StepType.THEN
                StepKeyword.AND -> StepType.AND
                StepKeyword.BUT -> StepType.BUT
            }

        // Each action becomes a separate step (or combine into one step)
        if (node.actions.isEmpty()) {
            return listOf(
                Step(
                    type = stepType,
                    description = node.description,
                    sourceLocation = node.location,
                ),
            )
        }

        // For call actions, create steps with the call details
        // Extractions and assertions that follow a call are attached to that call
        val steps = mutableListOf<Step>()
        var pendingCall: CallNode? = null
        var currentExtractions = mutableListOf<Extraction>()
        var currentAssertions = mutableListOf<Assertion>()
        var currentConditionals = mutableListOf<ConditionalAssertion>()
        var currentFailMessage: String? = null

        fun finalizeCall(call: CallNode) {
            val pathParams =
                call.parameters
                    .filterKeys { !it.startsWith("query_") }
                    .mapValues { extractValue(it.value) }

            val queryParams =
                call.parameters
                    .filterKeys { it.startsWith("query_") }
                    .mapKeys { it.key.removePrefix("query_") }
                    .mapValues { extractValue(it.value) }

            val headers = call.headers.mapValues { extractValue(it.value).toString() }
            val body = call.body?.let { extractStringValue(it) }
            val bodyProperties = call.bodyProperties?.let { transformBodyProperties(it) }
            val autoTestConfig = call.autoTestConfig?.let { transformAutoTestConfig(it) }

            steps.add(
                Step(
                    type = stepType,
                    description = node.description,
                    operationId = call.operationId,
                    specName = call.specName,
                    pathParams = pathParams,
                    queryParams = queryParams,
                    headers = headers,
                    body = body,
                    bodyProperties = bodyProperties,
                    bodyFile = call.bodyFile,
                    extractions = currentExtractions.toList(),
                    assertions = currentAssertions.toList(),
                    conditionals = currentConditionals.toList(),
                    failMessage = currentFailMessage,
                    autoTestConfig = autoTestConfig,
                    sourceLocation = call.location,
                ),
            )
            currentExtractions = mutableListOf()
            currentAssertions = mutableListOf()
            currentConditionals = mutableListOf()
            currentFailMessage = null
        }

        for (action in node.actions) {
            when (action) {
                is CallNode -> {
                    // Finalize any pending call with accumulated extractions/assertions
                    pendingCall?.let { finalizeCall(it) }
                    pendingCall = action
                }
                is ExtractNode -> {
                    currentExtractions.add(
                        Extraction(
                            variableName = action.variableName,
                            jsonPath = action.jsonPath,
                        ),
                    )
                }
                is AssertNode -> {
                    currentAssertions.add(transformAssertion(action))
                }
                is IncludeNode -> {
                    // Finalize any pending call first
                    pendingCall?.let { finalizeCall(it) }
                    pendingCall = null

                    // Include actions are handled at runtime by the executor
                    steps.add(
                        Step(
                            type = stepType,
                            description = "include ${action.fragmentName}",
                            fragmentName = action.fragmentName,
                            sourceLocation = action.location,
                        ),
                    )
                }
                is ConditionalNode -> {
                    currentConditionals.add(transformConditional(action))
                }
                is FailNode -> {
                    currentFailMessage = action.message
                }
            }
        }

        // Finalize any remaining pending call with extractions/assertions
        pendingCall?.let { finalizeCall(it) }

        // If there are orphan extractions/assertions/conditionals without a call, add them as a separate step
        if (pendingCall == null &&
            (
                currentExtractions.isNotEmpty() ||
                    currentAssertions.isNotEmpty() ||
                    currentConditionals.isNotEmpty() ||
                    currentFailMessage != null
            )
        ) {
            steps.add(
                Step(
                    type = stepType,
                    description = node.description,
                    extractions = currentExtractions.toList(),
                    assertions = currentAssertions.toList(),
                    conditionals = currentConditionals.toList(),
                    failMessage = currentFailMessage,
                    sourceLocation = node.location,
                ),
            )
        }

        return steps.ifEmpty {
            listOf(
                Step(
                    type = stepType,
                    description = node.description,
                    sourceLocation = node.location,
                ),
            )
        }
    }

    /**
     * Transform AST AssertNode to model Assertion.
     *
     * AssertNode uses ConditionNode internally, which is converted to model Condition.
     * This enables shared evaluation logic between assertions and conditionals.
     */
    private fun transformAssertion(node: AssertNode): Assertion {
        val condition = transformCondition(node.condition)
        return Assertion(
            condition = condition,
            description = describeCondition(condition),
        )
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
            is Condition.ResponseTime -> "responseTime < ${condition.maxMs} ms"
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

    private fun extractValue(node: ValueNode): Any =
        when (node) {
            is StringValueNode -> node.value
            is NumberValueNode -> node.value
            is VariableValueNode -> $$"${$${node.name}}"
            is JsonValueNode -> node.json
            is StatusRangeNode -> node.toRange()
        }

    private fun extractStringValue(node: ValueNode): String =
        when (node) {
            is StringValueNode -> node.value
            is NumberValueNode -> node.value.toString()
            is VariableValueNode -> $$"${$${node.name}}"
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
            is BodyPropertyValue.Simple -> BodyProperty.Simple(extractValue(value.value))
            is BodyPropertyValue.Nested -> BodyProperty.Nested(transformBodyProperties(value.properties))
        }

    /**
     * Transform AST ConditionalNode to model ConditionalAssertion.
     */
    private fun transformConditional(node: ConditionalNode): ConditionalAssertion {
        val ifBranch = transformConditionBranch(node.ifBranch)
        val elseIfBranches = node.elseIfBranches.map { transformConditionBranch(it) }
        val elseActions = node.elseActions?.let { transformConditionalActions(it) }

        return ConditionalAssertion(
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
            is ConditionNode.StatusCondition -> Condition.Status(extractValue(node.expected))
            is ConditionNode.JsonPathCondition ->
                Condition.JsonPath(
                    path = node.path,
                    operator = transformConditionOperator(node.operator),
                    expected = node.expected?.let { extractValue(it) },
                )
            is ConditionNode.HeaderCondition ->
                Condition.Header(
                    name = node.headerName,
                    operator = node.operator?.let { transformConditionOperator(it) } ?: ModelConditionOperator.EXISTS,
                    expected = node.expected?.let { extractValue(it) },
                )
            is ConditionNode.VariableCondition ->
                Condition.Variable(
                    name = node.variableName,
                    operator = transformConditionOperator(node.operator),
                    expected = node.expected?.let { extractValue(it) },
                )
            is ConditionNode.NegatedCondition ->
                Condition.Negated(condition = transformCondition(node.condition))
            is ConditionNode.CompoundCondition ->
                Condition.Compound(
                    left = transformCondition(node.left),
                    operator = transformLogicalOperator(node.operator),
                    right = transformCondition(node.right),
                )
            is ConditionNode.BodyContainsCondition ->
                Condition.BodyContains(text = extractValue(node.text))
            is ConditionNode.SchemaCondition ->
                Condition.Schema
            is ConditionNode.ResponseTimeCondition ->
                Condition.ResponseTime(maxMs = extractValue(node.maxMs))
            is ConditionNode.CustomAssertionCondition ->
                Condition.CustomAssertion(pattern = node.pattern)
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
     * Transform AST ConditionOperator to model ConditionOperator.
     */
    private fun transformConditionOperator(op: ConditionOperator): ModelConditionOperator =
        when (op) {
            ConditionOperator.EQUALS -> ModelConditionOperator.EQUALS
            ConditionOperator.NOT_EQUALS -> ModelConditionOperator.NOT_EQUALS
            ConditionOperator.CONTAINS -> ModelConditionOperator.CONTAINS
            ConditionOperator.NOT_CONTAINS -> ModelConditionOperator.NOT_CONTAINS
            ConditionOperator.MATCHES -> ModelConditionOperator.MATCHES
            ConditionOperator.EXISTS -> ModelConditionOperator.EXISTS
            ConditionOperator.NOT_EXISTS -> ModelConditionOperator.NOT_EXISTS
            ConditionOperator.GREATER_THAN -> ModelConditionOperator.GREATER_THAN
            ConditionOperator.LESS_THAN -> ModelConditionOperator.LESS_THAN
            ConditionOperator.HAS_SIZE -> ModelConditionOperator.HAS_SIZE
            ConditionOperator.NOT_EMPTY -> ModelConditionOperator.NOT_EMPTY
        }

    /**
     * Transform a list of AST ActionNodes to ConditionalActions.
     */
    private fun transformConditionalActions(actions: List<ActionNode>): ConditionalActions {
        val assertions = mutableListOf<Assertion>()
        val extractions = mutableListOf<Extraction>()
        val conditionals = mutableListOf<ConditionalAssertion>()
        var failMessage: String? = null

        for (action in actions) {
            when (action) {
                is AssertNode -> assertions.add(transformAssertion(action))
                is ExtractNode ->
                    extractions.add(
                        Extraction(
                            variableName = action.variableName,
                            jsonPath = action.jsonPath,
                        ),
                    )
                is ConditionalNode -> conditionals.add(transformConditional(action))
                is FailNode -> failMessage = action.message
                is CallNode, is IncludeNode -> {
                    // Calls and includes are not allowed in conditional branches
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

/**
 * Extension function to load scenarios from a path in BerryCrushSuite.
 */
fun BerryCrushSuite.loadScenariosFrom(path: String) {
    val loader = ScenarioLoader()
    val scenarios = loader.loadScenariosFromFile(Path.of(path))
    scenarios.forEach { scenario ->
        this.scenarios.add(scenario)
    }
}

/**
 * Extension function to load fragments from a path in BerryCrushSuite.
 */
fun BerryCrushSuite.loadFragmentsFrom(path: String) {
    val loader = ScenarioLoader()
    val fragments = loader.loadFragmentsFromFile(Path.of(path))
    fragments.forEach { (name, fragment) ->
        this.fragments[name] = fragment
    }
}
