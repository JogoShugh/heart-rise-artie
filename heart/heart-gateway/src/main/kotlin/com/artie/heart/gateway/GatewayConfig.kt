package com.artie.heart.gateway

import com.artie.heart.http4k.Credentials
import java.nio.file.Path

/** Where the upstream is, where its OpenAPI description is (a URL, or a mounted file), and how to call it. */
class UpstreamSettings(val url: String, val spec: String, val auth: UpstreamAuth)

/**
 * Everything a gateway needs, from its environment (spec/PLAN-OPENAPI-GATEWAY.md,
 * Container): its upstream, the profile its relations are named in, the
 * connector that says how the upstream signals, its port, and who may call
 * it. A missing or wrong setting stops the gateway at start, naming the
 * variable and never a value.
 */
class GatewayConfig private constructor(
    val upstream: UpstreamSettings,
    /** The profile its relations are named in; a connector names its own, and wins. */
    val profile: String?,
    /** The folder of the connector that says how the upstream signals; none means its affordance link alone. */
    val connector: Path?,
    val port: Int,
    val credentials: Credentials,
    /** Where to keep the record of upstream calls (GATEWAY_RECORD); none keeps no record. */
    val record: Path? = null,
) {
    companion object {
        const val DEFAULT_PORT = 8090

        fun from(env: Map<String, String>): GatewayConfig {
            fun value(name: String): String? = env[name]?.trim()?.ifEmpty { null }
            val url = checkNotNull(value("UPSTREAM_URL")) { "set UPSTREAM_URL: where the upstream is" }.trimEnd('/')
            val connector = value("GATEWAY_CONNECTOR")?.let { Path.of(it) }
            val profile = value("GATEWAY_PROFILE")
            check(profile != null || connector != null) {
                "set GATEWAY_PROFILE, or GATEWAY_CONNECTOR to a connector naming one"
            }
            val upstream = UpstreamSettings(url, value("UPSTREAM_SPEC") ?: "$url/openapi.json", auth(::value))
            val port = value("PORT")?.let { checkNotNull(it.toIntOrNull()) { "PORT must be a number" } } ?: DEFAULT_PORT
            // Calling the upstream as each caller, the gateway lets the upstream say whose credential is good.
            val credentials = if (upstream.auth == UpstreamAuth.Forwarded) {
                Credentials.forwarded()
            } else {
                Credentials.parse(
                    value("HEART_AUTH_BASIC_USERS").orEmpty(),
                    value("HEART_AUTH_BEARER_TOKENS").orEmpty(),
                )
            }
            val record = value("GATEWAY_RECORD")?.let { Path.of(it) }
            return GatewayConfig(upstream, profile, connector, port, credentials, record)
        }

        private fun auth(value: (String) -> String?): UpstreamAuth = when (val scheme = value("UPSTREAM_AUTH")) {
            null -> UpstreamAuth.None
            "none" -> UpstreamAuth.None
            "caller" -> UpstreamAuth.Forwarded
            "bearer" -> UpstreamAuth.Bearer(
                checkNotNull(value("UPSTREAM_TOKEN")) { "UPSTREAM_AUTH is bearer, so set UPSTREAM_TOKEN" },
            )
            "basic" -> {
                val user = value("UPSTREAM_USER")
                val password = value("UPSTREAM_PASSWORD")
                check(user != null && password != null) {
                    "UPSTREAM_AUTH is basic, so set UPSTREAM_USER and UPSTREAM_PASSWORD"
                }
                UpstreamAuth.Basic(user, password)
            }
            else -> error("UPSTREAM_AUTH must be none, bearer, basic or caller, not $scheme")
        }
    }
}
