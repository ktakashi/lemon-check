package org.berrycrush.executor.resolvers

import org.berrycrush.model.Directive
import org.berrycrush.model.HttpMethod
import org.berrycrush.model.RawRequest
import org.berrycrush.model.Step
import org.berrycrush.model.callDirective
import org.berrycrush.plugin.StepContext

@Suppress("ThrowsCount")
internal fun StepContext.resolveCall(step: Step): Step {
    val call = step.callDirective
    val resolvedSpecName = resolveCallValue(call?.specName, "spec name", required = false)
    val rawRequest = call?.rawRequest
    val isRawTarget = rawRequest != null

    if (isRawTarget) {
        val resolvedMethod =
            resolveCallValue(rawRequest.method, "raw HTTP method")
                ?: throw IllegalArgumentException("Missing raw HTTP method in step '$stepDescription'")
        val resolvedPath =
            resolveCallValue(rawRequest.path, "raw HTTP path")
                ?: throw IllegalArgumentException("Missing raw HTTP path in step '$stepDescription'")

        require(HttpMethod.fromName(resolvedMethod) != null) {
            "Invalid raw HTTP method '$resolvedMethod' in step '$stepDescription'. " +
                "Expected one of ${HttpMethod.entries.joinToString { it.name }}"
        }
        require(resolvedPath.startsWith('/')) {
            "Invalid raw HTTP path '$resolvedPath' in step '$stepDescription'. Expected path starting with '/'"
        }

        return if (
            resolvedMethod == rawRequest.method &&
            resolvedPath == rawRequest.path &&
            resolvedSpecName == call.specName
        ) {
            step
        } else {
            step.copy(
                directives =
                    step.directives.replaceFirstCallDirective { existing ->
                        existing.copy(
                            rawRequest = RawRequest(resolvedMethod, resolvedPath),
                            specName = resolvedSpecName,
                        )
                    },
            )
        }
    }

    val operationCall = call ?: throw IllegalArgumentException("Missing operation ID in step '$stepDescription'")
    val resolvedOperationId =
        resolveCallValue(operationCall.operationId, "operation ID")
            ?: throw IllegalArgumentException("Missing operation ID in step '$stepDescription'")

    return if (resolvedOperationId == operationCall.operationId && resolvedSpecName == operationCall.specName) {
        step
    } else {
        step.copy(
            directives =
                step.directives.replaceFirstCallDirective { existing ->
                    existing.copy(operationId = resolvedOperationId, specName = resolvedSpecName)
                },
        )
    }
}

private fun List<Directive>.replaceFirstCallDirective(replace: (Directive.CallDirective) -> Directive): List<Directive> {
    var replaced = false
    return map { directive ->
        if (!replaced && directive is Directive.CallDirective) {
            replaced = true
            replace(directive)
        } else {
            directive
        }
    }
}

private fun StepContext.resolveCallValue(
    raw: String?,
    label: String,
    required: Boolean = true,
): String? {
    if (raw == null) {
        require(!required) { "Missing $label in step '$stepDescription'" }
        return null
    }

    val resolved = interpolate(raw).trim()
    require(resolved.isNotEmpty()) {
        "Resolved $label is empty from '$raw' in step '$stepDescription'"
    }

    val unresolvedTemplate =
        resolved.contains("{{") || resolved.contains("}}")
    require(!unresolvedTemplate) {
        "Unable to resolve $label from '$raw' in step '$stepDescription'"
    }

    return resolved
}
