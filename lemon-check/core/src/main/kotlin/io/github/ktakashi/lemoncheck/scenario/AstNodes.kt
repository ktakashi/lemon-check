package io.github.ktakashi.lemoncheck.scenario

/**
 * Abstract Syntax Tree nodes for parsed scenario files.
 */
sealed class AstNode {
    abstract val location: SourceLocation
}

/**
 * Root node representing a scenario file.
 */
data class ScenarioFileNode(
    val scenarios: List<ScenarioNode>,
    val fragments: List<FragmentNode>,
    val features: List<FeatureNode> = emptyList(),
    val parameters: ParametersNode? = null,
    override val location: SourceLocation,
) : AstNode()

/**
 * Represents file-level configuration parameters.
 *
 * Parameters can override configuration settings for all scenarios in the file.
 * Example:
 * ```
 * parameters:
 *   shareVariablesAcrossScenarios: true
 *   timeout: 60
 * ```
 */
data class ParametersNode(
    val values: Map<String, Any>,
    override val location: SourceLocation,
) : AstNode()

/**
 * Represents a feature block that groups related scenarios.
 *
 * Features provide a logical grouping mechanism for scenarios
 * and can share common setup via background steps.
 *
 * Example:
 * ```
 * feature: Pet Store Operations
 *   background:
 *     given: existing pet
 *       call ^createPet
 *         body: {"name": "Fluffy"}
 *
 *   scenario: list pets
 *     when: ...
 * ```
 */
data class FeatureNode(
    val name: String,
    val description: String? = null,
    val background: BackgroundNode? = null,
    val scenarios: List<ScenarioNode>,
    val tags: Set<String> = emptySet(),
    override val location: SourceLocation,
) : AstNode()

/**
 * Represents background steps shared across scenarios in a feature.
 *
 * Background steps are prepended to every scenario in the feature,
 * acting as a "given" precondition for all scenarios.
 */
data class BackgroundNode(
    val steps: List<StepNode>,
    override val location: SourceLocation,
) : AstNode()

/**
 * Represents a scenario definition.
 *
 * Scenarios can be tagged for filtering and categorization.
 * Example: @slow @wip for a slow work-in-progress scenario.
 */
data class ScenarioNode(
    val name: String,
    val steps: List<StepNode>,
    val isOutline: Boolean = false,
    val examples: List<ExampleRowNode>? = null,
    val tags: Set<String> = emptySet(),
    override val location: SourceLocation,
) : AstNode()

/**
 * Represents a fragment (reusable step sequence).
 */
data class FragmentNode(
    val name: String,
    val steps: List<StepNode>,
    override val location: SourceLocation,
) : AstNode()

/**
 * Represents a single step in a scenario.
 */
data class StepNode(
    val keyword: StepKeyword,
    val description: String,
    val actions: List<ActionNode>,
    override val location: SourceLocation,
) : AstNode()

/**
 * Step keywords.
 */
enum class StepKeyword {
    GIVEN,
    WHEN,
    THEN,
    AND,
    BUT,
}

/**
 * Base class for step actions.
 */
sealed class ActionNode : AstNode()

/**
 * API call action.
 */
data class CallNode(
    val operationId: String,
    val specName: String? = null,
    val parameters: Map<String, ValueNode> = emptyMap(),
    val headers: Map<String, ValueNode> = emptyMap(),
    val body: ValueNode? = null,
    val bodyFile: String? = null,
    override val location: SourceLocation,
) : ActionNode()

/**
 * Variable extraction action.
 */
data class ExtractNode(
    val variableName: String,
    val jsonPath: String,
    override val location: SourceLocation,
) : ActionNode()

/**
 * Assertion action.
 */
data class AssertNode(
    val assertionType: AssertionKind,
    val path: String? = null,
    val expected: ValueNode? = null,
    val headerName: String? = null,
    val negate: Boolean = false,
    override val location: SourceLocation,
) : ActionNode()

/**
 * Fragment include action.
 */
data class IncludeNode(
    val fragmentName: String,
    override val location: SourceLocation,
) : ActionNode()

/**
 * Types of assertions.
 */
enum class AssertionKind {
    STATUS_CODE,
    BODY_CONTAINS,
    BODY_EQUALS,
    BODY_MATCHES,
    BODY_ARRAY_SIZE,
    BODY_ARRAY_NOT_EMPTY,
    HEADER_EXISTS,
    HEADER_EQUALS,
    MATCHES_SCHEMA,
    RESPONSE_TIME,
}

/**
 * Value node for literals, variables, or expressions.
 */
sealed class ValueNode : AstNode()

/**
 * String literal value.
 */
data class StringValueNode(
    val value: String,
    override val location: SourceLocation,
) : ValueNode()

/**
 * Number literal value.
 */
data class NumberValueNode(
    val value: Number,
    override val location: SourceLocation,
) : ValueNode()

/**
 * Variable reference value.
 */
data class VariableValueNode(
    val name: String,
    override val location: SourceLocation,
) : ValueNode()

/**
 * JSON object/array value.
 */
data class JsonValueNode(
    val json: String,
    override val location: SourceLocation,
) : ValueNode()

/**
 * Represents a row in scenario outline examples.
 */
data class ExampleRowNode(
    val values: Map<String, ValueNode>,
    override val location: SourceLocation,
) : AstNode()
