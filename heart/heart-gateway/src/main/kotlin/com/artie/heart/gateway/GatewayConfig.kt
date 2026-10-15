package com.artie.heart.gateway

import com.artie.heart.http4k.Credentials

/**
 * Everything a gateway needs, from its environment (spec/PLAN-OPENAPI-GATEWAY.md,
 * Container): where the upstream is, where its OpenAPI description is (a URL,
 * or a path to a mounted file), how the gateway calls it, the profile its
 * relations are named in, its port, and who may call it. A missing or wrong
 * setting stops the gateway at start, naming the variable and never a value.
 */
class GatewayConfig private constructor(
    val upstreamUrl: String,
    val spec: String,
    val auth: UpstreamAuth,
    val profile: String,
    val port: Int,
    val credentials: Credentials,
) {
    companion object {
        const val DEFAULT_PORT = 8090

        fun from(env: Map<String, String>): GatewayConfig {
            fun value(name: String): String? = env[name]?.trim()?.ifEmpty { null }
            val url = checkNotNull(value("UPSTREAM_URL")) { "set UPSTREAM_URL: where the upstream is" }.trimEnd('/')
            val profile =
                checkNotNull(value("GATEWAY_PROFILE")) { "set GATEWAY_PROFILE: the name of the gateway's profile" }
            val auth = when (val scheme = value("UPSTREAM_AUTH") ?: "none") {
                "none" -> UpstreamAuth.None
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
                else -> error("UPSTREAM_AUTH must be none, bearer or basic, not $scheme")
            }
            val port = value("PORT")?.let { checkNotNull(it.toIntOrNull()) { "PORT must be a number" } } ?: DEFAULT_PORT
            val credentials = Credentials.parse(
                value("HEART_AUTH_BASIC_USERS").orEmpty(),
                value("HEART_AUTH_BEARER_TOKENS").orEmpty(),
            )
            return GatewayConfig(url, value("UPSTREAM_SPEC") ?: "$url/openapi.json", auth, profile, port, credentials)
        }
    }
}
