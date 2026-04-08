package io.github.ktakashi.lemoncheck.scenario

import io.github.ktakashi.lemoncheck.dsl.LemonCheckSuite
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
 * Loads and transforms scenario files into executable Scenario objects.
 */
class ScenarioLoader {
    /**
     * Load scenarios from a directory.
     *
     * @param directory Path to directory containing .scenario files
     * @return List of parsed Scenario objects
     */
    fun loadScenariosFromDirectory(directory: Path): List<Scenario> {
        val scenarios = mutableListOf<Scenario>()

        Files
            .walk(directory)
            .filter { it.toString().endsWith(".scenario") }
            .forEach { path ->
                val loadedScenarios = loadScenariosFromFile(path)
                scenarios.addAll(loadedScenarios)
            }

        return scenarios
    }

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
            throw IllegalArgumentException("Failed to parse scenario file:\n$errorMessages")
        }

        return result.ast!!.scenarios.map { transformScenario(it) }
    }

    /**
     * Load fragments from a directory.
     *
     * @param directory Path to directory containing .fragment files
     * @return Map of fragment name to Fragment object
     */
    fun loadFragmentsFromDirectory(directory: Path): Map<String, Fragment> {
        val fragments = mutableMapOf<String, Fragment>()

        Files
            .walk(directory)
            .filter { it.toString().endsWith(".fragment") }
            .forEach { path ->
                val loadedFragments = loadFragmentsFromFile(path)
                fragments.putAll(loadedFragments)
            }

        return fragments
    }

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
            throw IllegalArgumentException("Failed to parse fragment file:\n$errorMessages")
        }

        return result.ast!!.fragments.associate { it.name to transformFragment(it) }
    }

    private fun transformScenario(node: ScenarioNode): Scenario {
        val steps = node.steps.flatMap { transformStep(it) }
        val examples = node.examples?.map { transformExampleRow(it) }

        return Scenario(
            name = node.name,
            steps = steps,
            examples = examples,
        )
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
            }

        // Each action becomes a separate step (or combine into one step)
        if (node.actions.isEmpty()) {
            return listOf(
                Step(
                    type = stepType,
                    description = node.description,
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
                ),
            )
        }

        return steps.ifEmpty {
            listOf(
                Step(
                    type = stepType,
                    description = node.description,
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
