package com.artie.heart.gateway

import com.artie.heart.ServingLimits
import com.artie.heart.http4k.Credentials
import java.nio.file.Path

/** Where the upstream is, where its OpenAPI description is (a URL, or a mounted file), and how to call it. */
class UpstreamSettings(val url: String, val spec: String, val auth: UpstreamAuth)

/**
 * How a gateway serves its callers: its [port], who may call it, and its [limits] (ServingLimits;
 * a validator on every act unless HEART_ACTS_WITHOUT_VALIDATOR=allowed, for a trial, says no), and the [care] it
 * takes unless a request names its own (GATEWAY_CARE; every kind unless a trial says otherwise).
 */
class Serving(
    val port: Int,
    val credentials: Credentials,
    val limits: ServingLimits,
    val tls: Tls? = null,
    val care: GatewayCare = GatewayCare.EVERY,
)

/** A keystore to serve https from (GATEWAY_TLS_KEYSTORE), and its password, a secret never written back. */
class Tls(val keystore: Path, val password: String) {
    override fun toString() = "Tls($keystore)"
}

/**
 * Everything a gateway needs, from its environment (spec/PLAN-OPENAPI-GATEWAY.md,
 * Container): its upstream, the profile its relations are named in, the
 * connector that says how the upstream signals, and how it serves its
 * callers. A missing or wrong setting stops the gateway at start, naming the
 * variable and never a value.
 */
class GatewayConfig private constructor(
    val upstream: UpstreamSettings,
    /** The profile its relations are named in; a connector names its own, and wins. */
    val profile: String?,
    /** The folder of the connector that says how the upstream signals; none means its affordance link alone. */
    val connector: Path?,
    val serving: Serving,
    /** Where to keep the record of upstream calls (GATEWAY_RECORD); none keeps no record. */
    val record: Path? = null,
    /**
     * The secret the upstream signs its webhooks with (GATEWAY_WEBHOOK_SECRET): with it, the gateway takes them, and
     * holds reads that prefer to wait (Hub); without, neither. Never written back.
     */
    val webhookSecret: String? = null,
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
            val record = value("GATEWAY_RECORD")?.let { Path.of(it) }
            val secret = value("GATEWAY_WEBHOOK_SECRET")
            return GatewayConfig(upstream, profile, connector, serving(::value, upstream.auth), record, secret)
        }

        /** How the gateway serves its callers, from [value]s, calling the upstream as [auth] says. */
        private fun serving(value: (String) -> String?, auth: UpstreamAuth): Serving {
            val port = value("PORT")?.let { checkNotNull(it.toIntOrNull()) { "PORT must be a number" } } ?: DEFAULT_PORT
            // Calling the upstream as each caller, the gateway lets the upstream say whose credential is good.
            val credentials = if (auth == UpstreamAuth.Forwarded) {
                Credentials.forwarded()
            } else {
                Credentials.parse(
                    value("HEART_AUTH_BASIC_USERS").orEmpty(),
                    value("HEART_AUTH_BEARER_TOKENS").orEmpty(),
                )
            }
            val required = when (val switch = value("HEART_ACTS_WITHOUT_VALIDATOR")) {
                null -> true
                "allowed" -> false
                else -> error("HEART_ACTS_WITHOUT_VALIDATOR must be allowed, or unset, not $switch")
            }
            val tls = value("GATEWAY_TLS_KEYSTORE")?.let { keystore ->
                val password = checkNotNull(value("GATEWAY_TLS_PASSWORD")) {
                    "GATEWAY_TLS_KEYSTORE is set, so set GATEWAY_TLS_PASSWORD"
                }
                Tls(Path.of(keystore), password)
            }
            val care = value("GATEWAY_CARE")?.let { words ->
                try {
                    GatewayCare.parse(words)
                } catch (unknown: IllegalArgumentException) {
                    error("GATEWAY_CARE ${unknown.message}")
                }
            } ?: GatewayCare.EVERY
            return Serving(port, credentials, ServingLimits(validatorRequired = required), tls, care)
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
