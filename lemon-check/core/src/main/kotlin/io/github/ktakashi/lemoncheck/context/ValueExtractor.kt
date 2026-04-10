package io.github.ktakashi.lemoncheck.context

import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import io.github.ktakashi.lemoncheck.exception.ExtractionException
import io.github.ktakashi.lemoncheck.model.Extraction
import io.github.ktakashi.lemoncheck.model.ExtractionSource

/**
 * Extracts values from API responses using JSONPath.
 */
class ValueExtractor {
    /**
     * Extract a value from a response body using JSONPath.
     *
     * @param body Response body (JSON)
     * @param jsonPath JSONPath expression
     * @return Extracted value or null if not found
     * @throws ExtractionException if extraction fails
     */
    fun extract(
        body: String,
        jsonPath: String,
    ): Any? =
        runCatching { JsonPath.read<Any>(body, jsonPath) }
            .fold(
                onSuccess = { it },
                onFailure = { e ->
                    when (e) {
                        is PathNotFoundException -> null
                        else ->
                            throw ExtractionException(
                                variableName = "unknown",
                                jsonPath = jsonPath,
                                responseBody = body,
                                cause = e,
                            )
                    }
                },
            )

    /**
     * Extract a value and store in context.
     *
     * @param body Response body (JSON)
     * @param extraction Extraction specification
     * @param context Execution context to store the result
     * @return The extracted value
     */
    fun extractTo(
        body: String,
        extraction: Extraction,
        context: ExecutionContext,
    ): Any? {
        val value =
            when (extraction.source) {
                ExtractionSource.BODY -> extract(body, extraction.jsonPath)
                ExtractionSource.HEADER -> throw UnsupportedOperationException("Header extraction requires response object")
                ExtractionSource.STATUS -> throw UnsupportedOperationException("Status extraction requires response object")
            }
        if (value != null) {
            context[extraction.variableName] = value
        }
        return value
    }

    /**
     * Extract a value with a default if not found.
     *
     * @param body Response body (JSON)
     * @param jsonPath JSONPath expression
     * @param defaultValue Value to return if path not found
     * @return Extracted value or default
     */
    fun extractOrDefault(
        body: String,
        jsonPath: String,
        defaultValue: Any,
    ): Any = extract(body, jsonPath) ?: defaultValue

    /**
     * Extract multiple values from a response.
     *
     * @param body Response body (JSON)
     * @param extractions List of extraction specifications
     * @param context Execution context to store results
     * @return Map of variable names to extracted values
     */
    fun extractAll(
        body: String,
        extractions: List<Extraction>,
        context: ExecutionContext,
    ): Map<String, Any?> =
        extractions.associate { extraction ->
            val value =
                runCatching { extract(body, extraction.jsonPath) }
                    .getOrNull()
            value?.let { context[extraction.variableName] = it }
            extraction.variableName to value
        }

    /**
     * Extract a typed value.
     *
     * @param body Response body (JSON)
     * @param jsonPath JSONPath expression
     * @return Extracted value cast to type T, or null
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> extractTyped(
        body: String,
        jsonPath: String,
    ): T? = extract(body, jsonPath) as? T

    /**
     * Extract a list of values.
     *
     * @param body Response body (JSON)
     * @param jsonPath JSONPath expression that returns an array
     * @return List of extracted values
     */
    fun extractList(
        body: String,
        jsonPath: String,
    ): List<Any?> =
        when (val result = extract(body, jsonPath)) {
            is List<*> -> result.toList()
            null -> emptyList()
            else -> listOf(result)
        }

    /**
     * Check if a path exists in the response.
     *
     * @param body Response body (JSON)
     * @param jsonPath JSONPath expression
     * @return True if path exists and has a value
     */
    fun pathExists(
        body: String,
        jsonPath: String,
    ): Boolean = runCatching { extract(body, jsonPath) != null }.getOrElse { false }
}
