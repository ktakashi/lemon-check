package io.github.ktakashi.lemoncheck.scenario

import io.github.ktakashi.lemoncheck.dsl.LemonCheckSuite
import io.github.ktakashi.lemoncheck.exception.ScenarioParseException
import io.github.ktakashi.lemoncheck.model.Assertion
import io.github.ktakashi.lemoncheck.model.AssertionType
import io.github.ktakashi.lemoncheck.model.ExampleRow
import io.github.ktakashi.lemoncheck.model.Extraction
import io.github.ktakashi.lemoncheck.model.Fragment
import io.github.ktakashi.lemoncheck.model.Scenario
import io.github.ktakashi.lemoncheck.model.Step
import io.github.ktakashi.lemoncheck.model.StepType
import java.nio.file.Files
import java.nio.file.Path

/**
 * Represents a group of scenarios within a feature block.
 *
 * @property name Feature name
 * @property scenarios List of scenarios belonging to this feature
 * @property tags Tags applied to the feature
 */
data class FeatureGroup(
    val name: String,
    val scenarios: List<Scenario>,
    val tags: Set<String> = emptySet(),
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
        val result = Parser.parse(source, fileName)

        if (!result.isSuccess) {
            val errorMessages = result.errors.joinToString("\n") { it.toString() }
            throw ScenarioParseException("Failed to parse scenario file:\n$errorMessages")
        }

        // Transform standalone scenarios (not in any feature)
        val standaloneScenarios = result.ast!!.scenarios.map { transformScenario(it) }

        // Transform features with their scenarios (with optional background steps prepended)
        val featureGroups =
            result.ast.features.map { feature ->
                FeatureGroup(
                    name = feature.name,
                    scenarios = transformFeature(feature),
                    tags = feature.tags,
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
        val result = Parser.parse(source, fileName)

        if (!result.isSuccess) {
            val errorMessages = result.errors.joinToString("\n") { it.toString() }
            throw ScenarioParseException("Failed to parse scenario file:\n$errorMessages")
        }

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
                    extractions = currentExtractions.toList(),
                    assertions = currentAssertions.toList(),
                    sourceLocation = call.location,
                ),
            )
            currentExtractions = mutableListOf()
            currentAssertions = mutableListOf()
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
            }
        }

        // Finalize any remaining pending call with extractions/assertions
        pendingCall?.let { finalizeCall(it) }

        // If there are orphan extractions/assertions without a call, add them as a separate step
        if (pendingCall == null && (currentExtractions.isNotEmpty() || currentAssertions.isNotEmpty())) {
            steps.add(
                Step(
                    type = stepType,
                    description = node.description,
                    extractions = currentExtractions.toList(),
                    assertions = currentAssertions.toList(),
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

    private fun transformAssertion(node: AssertNode): Assertion {
        val type =
            when (node.assertionType) {
                AssertionKind.STATUS_CODE -> AssertionType.STATUS_CODE
                AssertionKind.BODY_CONTAINS -> AssertionType.BODY_CONTAINS
                AssertionKind.BODY_EQUALS -> AssertionType.BODY_EQUALS
                AssertionKind.BODY_MATCHES -> AssertionType.BODY_MATCHES
                AssertionKind.BODY_ARRAY_SIZE -> AssertionType.BODY_ARRAY_SIZE
                AssertionKind.BODY_ARRAY_NOT_EMPTY -> AssertionType.BODY_ARRAY_NOT_EMPTY
                AssertionKind.HEADER_EXISTS -> AssertionType.HEADER_EXISTS
                AssertionKind.HEADER_EQUALS -> AssertionType.HEADER_EQUALS
                AssertionKind.MATCHES_SCHEMA -> AssertionType.MATCHES_SCHEMA
                AssertionKind.RESPONSE_TIME -> AssertionType.RESPONSE_TIME
            }

        return Assertion(
            type = type,
            jsonPath = node.path,
            expected = node.expected?.let { extractValue(it) },
            headerName = node.headerName,
            pattern = if (type == AssertionType.BODY_MATCHES) node.expected?.let { extractStringValue(it) } else null,
        )
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
        }

    private fun extractStringValue(node: ValueNode): String =
        when (node) {
            is StringValueNode -> node.value
            is NumberValueNode -> node.value.toString()
            is VariableValueNode -> $$"${$${node.name}}"
            is JsonValueNode -> node.json
        }
}

/**
 * Extension function to load scenarios from a path in LemonCheckSuite.
 */
fun LemonCheckSuite.loadScenariosFrom(path: String) {
    val loader = ScenarioLoader()
    val scenarios = loader.loadScenariosFromFile(Path.of(path))
    scenarios.forEach { scenario ->
        this.scenarios.add(scenario)
    }
}

/**
 * Extension function to load fragments from a path in LemonCheckSuite.
 */
fun LemonCheckSuite.loadFragmentsFrom(path: String) {
    val loader = ScenarioLoader()
    val fragments = loader.loadFragmentsFromFile(Path.of(path))
    fragments.forEach { (name, fragment) ->
        this.fragments[name] = fragment
    }
}
