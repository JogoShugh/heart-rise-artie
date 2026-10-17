package com.artie.heart.gateway.fixtures

import com.artie.heart.gateway.Connector
import com.artie.heart.gateway.Gateway
import com.artie.heart.gateway.GatewaySpec
import com.artie.heart.gateway.UpstreamAuth
import com.artie.heart.http4k.Credentials
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
    fun handler(github: GitHubUpstream, connector: Path): HttpHandler {
        val mapper = ObjectMapper()
        val loaded = Connector.load(connector, mapper)
        val description = checkNotNull(GitHubUpstream::class.java.getResource("/github/openapi.json")).readText()
        val gateway = Gateway(
            GatewaySpec(description, loaded.include), github.handler, loaded.profile, UpstreamAuth.Forwarded,
            connector = loaded,
        )
        return gateway.handler(Credentials.forwarded())
    }
}
