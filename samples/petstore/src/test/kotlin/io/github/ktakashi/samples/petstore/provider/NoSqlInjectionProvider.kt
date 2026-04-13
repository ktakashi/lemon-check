package io.github.ktakashi.samples.petstore.provider

import io.github.ktakashi.lemoncheck.autotest.ParameterLocation
import io.github.ktakashi.lemoncheck.autotest.provider.SecurityPayload
import io.github.ktakashi.lemoncheck.autotest.provider.SecurityTestProvider

/**
 * Example custom security test provider for demonstrating extensibility in Kotlin.
 *
 * This provider tests for NoSQL injection attacks, which are common in
 * MongoDB and similar databases.
 *
 * This Kotlin implementation works alongside the Java-based EmojiTestProvider,
 * demonstrating that providers can be written in either language.
 */
class NoSqlInjectionProvider : SecurityTestProvider {
    override val testType: String = "NoSQLInjection"
    override val displayName: String = "NoSQL Injection"
    override val priority: Int = 100 // Higher than built-in providers

    override fun applicableLocations(): Set<ParameterLocation> = setOf(ParameterLocation.BODY, ParameterLocation.QUERY)

    override fun generatePayloads(): List<SecurityPayload> =
        listOf(
            SecurityPayload(
                name = "MongoDB \$ne operator",
                payload = "{\"\$ne\": null}",
            ),
            SecurityPayload(
                name = "MongoDB \$gt operator",
                payload = "{\"\$gt\": \"\"}",
            ),
            SecurityPayload(
                name = "MongoDB \$where",
                payload = "{\"\$where\": \"sleep(5000)\"}",
            ),
            SecurityPayload(
                name = "MongoDB \$regex",
                payload = "{\"\$regex\": \".*\"}",
            ),
        )
}
