package io.github.ktakashi.lemoncheck.autotest.provider

import io.github.ktakashi.lemoncheck.autotest.ParameterLocation

/**
 * Collection of default security test providers.
 */
object DefaultSecurityTestProviders {
    /**
     * All built-in security test providers.
     */
    val all: List<SecurityTestProvider> =
        listOf(
            SqlInjectionProvider(),
            XssProvider(),
            PathTraversalProvider(),
            CommandInjectionProvider(),
            LdapInjectionProvider(),
            XxeProvider(),
            HeaderInjectionProvider(),
        )
}

/**
 * SQL injection attack payloads.
 */
class SqlInjectionProvider : SecurityTestProvider {
    override val testType: String = "SQLInjection"
    override val displayName: String = "SQL Injection"

    override fun applicableLocations(): Set<ParameterLocation> = setOf(ParameterLocation.BODY, ParameterLocation.QUERY)

    override fun generatePayloads(): List<SecurityPayload> =
        listOf(
            SecurityPayload("Single quote", "' OR '1'='1"),
            SecurityPayload("Union select", "' UNION SELECT * FROM users--"),
            SecurityPayload("Comment bypass", "admin'--"),
            SecurityPayload("Boolean-based", "1' AND '1'='1"),
            SecurityPayload("Stacked queries", "'; DROP TABLE users;--"),
        )
}

/**
 * Cross-site scripting (XSS) attack payloads.
 */
class XssProvider : SecurityTestProvider {
    override val testType: String = "XSS"
    override val displayName: String = "XSS"

    override fun applicableLocations(): Set<ParameterLocation> =
        setOf(ParameterLocation.BODY, ParameterLocation.QUERY, ParameterLocation.HEADER)

    override fun generatePayloads(): List<SecurityPayload> =
        listOf(
            SecurityPayload("Script tag", "<script>alert('XSS')</script>"),
            SecurityPayload("Event handler", "<img src=x onerror=alert('XSS')>"),
            SecurityPayload("SVG onload", "<svg onload=alert('XSS')>"),
            SecurityPayload("JavaScript URL", "javascript:alert('XSS')"),
            SecurityPayload("HTML injection", "<h1>Injected</h1>"),
        )
}

/**
 * Path traversal attack payloads.
 */
class PathTraversalProvider : SecurityTestProvider {
    override val testType: String = "PathTraversal"
    override val displayName: String = "Path Traversal"

    override fun applicableLocations(): Set<ParameterLocation> =
        setOf(ParameterLocation.PATH, ParameterLocation.QUERY, ParameterLocation.BODY)

    override fun generatePayloads(): List<SecurityPayload> =
        listOf(
            SecurityPayload("Unix relative", "../../../etc/passwd"),
            SecurityPayload("Windows relative", "..\\..\\..\\windows\\system32\\config\\sam"),
            SecurityPayload("URL encoded", "..%2F..%2F..%2Fetc%2Fpasswd"),
            SecurityPayload("Double encoded", "..%252F..%252F..%252Fetc%252Fpasswd"),
        )
}

/**
 * Command injection attack payloads.
 */
class CommandInjectionProvider : SecurityTestProvider {
    override val testType: String = "CommandInjection"
    override val displayName: String = "Command Injection"

    override fun applicableLocations(): Set<ParameterLocation> = setOf(ParameterLocation.BODY, ParameterLocation.QUERY)

    override fun generatePayloads(): List<SecurityPayload> =
        listOf(
            SecurityPayload("Unix semicolon", "; ls -la"),
            SecurityPayload("Unix pipe", "| cat /etc/passwd"),
            SecurityPayload("Unix backtick", "`id`"),
            SecurityPayload("Unix subshell", "\$(whoami)"),
            SecurityPayload("Windows ampersand", "& dir"),
        )
}

/**
 * LDAP injection attack payloads.
 */
class LdapInjectionProvider : SecurityTestProvider {
    override val testType: String = "LDAPInjection"
    override val displayName: String = "LDAP Injection"

    override fun applicableLocations(): Set<ParameterLocation> = setOf(ParameterLocation.BODY, ParameterLocation.QUERY)

    override fun generatePayloads(): List<SecurityPayload> =
        listOf(
            SecurityPayload("Wildcard", "*"),
            SecurityPayload("Filter bypass", "admin)(&)"),
        )
}

/**
 * XML External Entity (XXE) attack payloads.
 */
class XxeProvider : SecurityTestProvider {
    override val testType: String = "XXE"
    override val displayName: String = "XXE"

    override fun applicableLocations(): Set<ParameterLocation> = setOf(ParameterLocation.BODY)

    override fun generatePayloads(): List<SecurityPayload> =
        listOf(
            SecurityPayload(
                "External entity",
                "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>",
            ),
            SecurityPayload("CDATA", "<![CDATA[<script>alert('XSS')</script>]]>"),
        )
}

/**
 * HTTP header injection attack payloads.
 */
class HeaderInjectionProvider : SecurityTestProvider {
    override val testType: String = "HeaderInjection"
    override val displayName: String = "Header Injection"

    override fun applicableLocations(): Set<ParameterLocation> = setOf(ParameterLocation.HEADER)

    override fun generatePayloads(): List<SecurityPayload> =
        listOf(
            SecurityPayload("CRLF injection", "value\r\nX-Injected: header"),
            SecurityPayload("Null byte", "value\u0000injection"),
        )
}
