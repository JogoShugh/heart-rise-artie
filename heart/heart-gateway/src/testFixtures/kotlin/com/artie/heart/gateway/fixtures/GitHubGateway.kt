package com.artie.heart.gateway.fixtures

import com.artie.heart.ServingLimits
import com.artie.heart.gateway.Connector
import com.artie.heart.gateway.Gateway
import com.artie.heart.gateway.GatewaySpec
import com.artie.heart.gateway.UpstreamAuth
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartSettings
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.HttpHandler
import java.nio.file.Path

/**
 * The gateway in front of a small GitHub, as a client meets it: GitHub's own
 * description (a slice), the [connector] folder (the shipped one is
 * heart-gateway/connectors/github), and each caller's own credential
 * forwarded to [github]. For clients' tests, in process, with no network.
 */
object GitHubGateway {
    /**
     * With [validatorRequired] false, the gateway's development switch for acts with no validator is on; with a
     * [webhookSecret], it takes GitHub's webhooks signed with it, and holds reads that prefer to wait (Hub).
     */
    fun handler(
        github: GitHubUpstream,
        connector: Path,
        validatorRequired: Boolean = true,
        webhookSecret: String? = null,
    ): HttpHandler {
        val mapper = ObjectMapper()
        val loaded = Connector.load(connector, mapper)
        val description = checkNotNull(GitHubUpstream::class.java.getResource("/github/openapi.json")).readText()
        val gateway = Gateway(
            GatewaySpec(description, loaded.reads), github.handler, loaded.profile, UpstreamAuth.Forwarded,
            connector = loaded,
            // As the gateway runs: links under GitHub's API address are links into the API it serves.
            upstreamUrl = "https://api.github.com",
        )
        val settings = HeartSettings(limits = ServingLimits(validatorRequired = validatorRequired))
        val hub = webhookSecret?.let { com.artie.heart.gateway.Hub(it, "https://api.github.com", mapper) }
        return gateway.handler(Credentials.forwarded(), settings, hub = hub)
    }
}
