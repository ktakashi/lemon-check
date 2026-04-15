package org.berrycrush.scenario

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
 * Represents a body property value, which can be either a simple value or a nested object.
 */
sealed class BodyPropertyValue {
    /** A simple value (string, number, boolean, etc.) */
    data class Simple(
        val value: ValueNode,
    ) : BodyPropertyValue()

    /** A nested object with properties */
    data class Nested(
        val properties: Map<String, BodyPropertyValue>,
    ) : BodyPropertyValue()
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
    /** Raw JSON body (used with body: followed by inline JSON) */
    val body: ValueNode? = null,
    /** Structured body properties (used with body: followed by indented properties) */
    val bodyProperties: Map<String, BodyPropertyValue>? = null,
    val bodyFile: String? = null,
    /** Auto-test configuration for generating invalid/security tests */
    val autoTestConfig: AutoTestConfig? = null,
    override val location: SourceLocation,
) : ActionNode()

/**
 * Configuration for auto-generating tests.
 *
 * @property types The types of tests to generate (invalid, security)
 * @property excludes Test categories to exclude (e.g., "SQLInjection", "maxLength")
 */
data class AutoTestConfig(
    val types: Set<AutoTestType>,
    val excludes: Set<String> = emptySet(),
    val location: SourceLocation,
)

/**
 * Types of auto-generated tests.
 */
enum class AutoTestType {
    /** Invalid request tests - violate schema constraints */
    INVALID,

    /** Security tests - injection and attack patterns */
    SECURITY,
}

/**
 * Variable extraction action.
 */
data class ExtractNode(
    val variableName: String,
    val jsonPath: String,
    override val location: SourceLocation,
) : ActionNode()

/**
 * Assertion action - wraps a condition that must be true for the assertion to pass.
 *
 * By using ConditionNode, assertions share the same structure as conditional checks,
 * enabling code reuse and consistent behavior between `assert` and `if` statements.
 *
 * Examples:
 * - `assert status 200` -> StatusCondition
 * - `assert $.name equals "Fluffy"` -> JsonPathCondition
 * - `assert header Content-Type = "application/json"` -> HeaderCondition
 * - `assert not contains "error"` -> NegatedCondition(BodyContainsCondition)
 * - `assert schema` -> SchemaCondition
 * - `assert responseTime 1000` -> ResponseTimeCondition
 */
data class AssertNode(
    val condition: ConditionNode,
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
 * Conditional action - evaluates conditions and executes matching actions.
 *
 * Example:
 * ```
 * if status 201
 *   assert $.status equals "available"
 * else if status 200
 *   assert $.status equals "in-progress"
 * else
 *   fail "status must be 200 or 201"
 * ```
 */
data class ConditionalNode(
    /** The primary if condition and its actions */
    val ifBranch: ConditionBranch,
    /** Optional else-if branches */
    val elseIfBranches: List<ConditionBranch> = emptyList(),
    /** Optional else branch actions (no condition) */
    val elseActions: List<ActionNode>? = null,
    override val location: SourceLocation,
) : ActionNode()

/**
 * A condition branch with its associated actions.
 *
 * @property condition The condition to evaluate (status code, json path, etc.)
 * @property actions Actions to execute if condition is true
 */
data class ConditionBranch(
    val condition: ConditionNode,
    val actions: List<ActionNode>,
    val location: SourceLocation,
)

/**
 * Represents a condition to evaluate.
 * Conditions are similar to assertions but return true/false instead of failing.
 */
sealed class ConditionNode {
    abstract val location: SourceLocation

    /**
     * Status code condition.
     * Example: `if status 201` or `if status 2xx`
     */
    data class StatusCondition(
        val expected: ValueNode,
        override val location: SourceLocation,
    ) : ConditionNode()

    /**
     * JSON path condition.
     * Example: `if $.status equals "active"`
     */
    data class JsonPathCondition(
        val path: String,
        val operator: ConditionOperator,
        val expected: ValueNode?,
        override val location: SourceLocation,
    ) : ConditionNode()

    /**
     * Header condition.
     * Example: `if header Content-Type equals "application/json"`
     */
    data class HeaderCondition(
        val headerName: String,
        val operator: ConditionOperator?,
        val expected: ValueNode?,
        override val location: SourceLocation,
    ) : ConditionNode()

    /**
     * Variable condition.
     * Example: `if test.type equals "invalid"`
     */
    data class VariableCondition(
        val variableName: String,
        val operator: ConditionOperator,
        val expected: ValueNode?,
        override val location: SourceLocation,
    ) : ConditionNode()

    /**
     * Negated condition.
     * Example: `if not status 200`
     */
    data class NegatedCondition(
        val condition: ConditionNode,
        override val location: SourceLocation,
    ) : ConditionNode()

    /**
     * Compound condition with logical operators.
     * Example: `if status 4xx and test.type equals "invalid"`
     */
    data class CompoundCondition(
        val left: ConditionNode,
        val operator: LogicalOperator,
        val right: ConditionNode,
        override val location: SourceLocation,
    ) : ConditionNode()

    /**
     * Body contains condition (assertion-specific).
     * Example: `assert contains "expected text"`
     */
    data class BodyContainsCondition(
        val text: ValueNode,
        override val location: SourceLocation,
    ) : ConditionNode()

    /**
     * Schema validation condition (assertion-specific).
     * Example: `assert schema`
     */
    data class SchemaCondition(
        override val location: SourceLocation,
    ) : ConditionNode()

    /**
     * Response time condition (assertion-specific).
     * Example: `assert responseTime 1000`
     */
    data class ResponseTimeCondition(
        val maxMs: ValueNode,
        override val location: SourceLocation,
    ) : ConditionNode()

    /**
     * Custom assertion condition (matched against AssertionRegistry).
     * Example: `assert the order should have status "completed"`
     *
     * This condition type is used when the assertion text doesn't match
     * any built-in condition types (status, header, jsonpath, etc.).
     */
    data class CustomAssertionCondition(
        val pattern: String,
        override val location: SourceLocation,
    ) : ConditionNode()
}

/**
 * Logical operators for compound conditions.
 */
enum class LogicalOperator {
    AND,
    OR,
}

/**
 * Operators for conditions.
 */
enum class ConditionOperator {
    EQUALS,
    NOT_EQUALS,
    CONTAINS,
    NOT_CONTAINS,
    MATCHES,
    EXISTS,
    NOT_EXISTS,
    GREATER_THAN,
    LESS_THAN,

    /** Array/string size check: hasSize */
    HAS_SIZE,

    /** Array/string not empty check: notEmpty */
    NOT_EMPTY,
}

/**
 * Fail action - fails the scenario with a custom message.
 *
 * Example:
 * ```
 * fail "Expected status 200 or 201"
 * ```
 */
data class FailNode(
    val message: String,
    override val location: SourceLocation,
) : ActionNode()

/**
 * Types of assertions.
 *
 * @deprecated Use ConditionNode subtypes instead. AssertNode now wraps ConditionNode.
 * This enum is kept for backward compatibility during migration.
 */
@Deprecated("Use ConditionNode subtypes instead")
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
 * Status code range value (e.g., 1xx, 2xx, 3xx, 4xx, 5xx).
 * Represents a range of 100 status codes.
 */
data class StatusRangeNode(
    val base: Int,
    override val location: SourceLocation,
) : ValueNode() {
    /** Returns the IntRange for this status pattern (e.g., 4 -> 400..499) */
    fun toRange(): IntRange = (base * 100)..(base * 100 + 99)
}

/**
 * Represents a row in scenario outline examples.
 */
data class ExampleRowNode(
    val values: Map<String, ValueNode>,
    override val location: SourceLocation,
) : AstNode()
