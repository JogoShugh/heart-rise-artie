package com.artie.heart.gateway.github

import com.artie.heart.HeartResource
import com.artie.heart.gateway.Connector
import com.artie.heart.gateway.Gateway
import com.artie.heart.gateway.GatewaySpec
import com.artie.heart.gateway.UpstreamAuth
import com.artie.heart.gateway.fixtures.GitHubUpstream
import com.artie.heart.gateway.fixtures.Pull
import com.artie.heart.http4k.Credentials
import com.artie.heart.tck.TckAct
import com.artie.heart.tck.TckFixture
import com.artie.heart.tck.TckWorld
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.spring.CucumberContextConfiguration
import java.nio.file.Path
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * HEART's conformance features through the gateway in front of a small
 * GitHub, with the connector that ships with the gateway: GitHub's own
 * example pull request, number 1347 of octocat/Hello-World, open and
 * clean. Merge is offered, reopen is not. The gateway calls GitHub as each
 * caller; the caller here is "ada".
 */
@Configuration(proxyBeanMethods = false)
class GitHubThroughGateway {
    private val github = GitHubUpstream().apply { callers["ada-token"] = "ada" }
    private val mapper = ObjectMapper()
    private val connector = Connector.load(Path.of("connectors", "github"), mapper)

    private val gateway = Gateway(
        GatewaySpec(checkNotNull(javaClass.getResource("/github/openapi.json")).readText(), connector.reads),
        github.handler,
        connector.profile,
        UpstreamAuth.Forwarded,
        connector = connector,
    )

    @Bean
    fun mapper(): ObjectMapper = mapper

    /** The resource types the gateway built from GitHub's description and the connector. */
    @Bean
    fun gatewayResources(): List<HeartResource<*, *>> = gateway.resources

    /** The gateway, on a port the system picks, admitting any credential for GitHub to judge. */
    @Bean(destroyMethod = "stop")
    fun server(): Http4kServer = gateway.handler(Credentials.forwarded()).asServer(Jetty(0)).start()

    @Bean
    fun client(server: Http4kServer): WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:${server.port()}")
            .defaultHeader(HttpHeaders.AUTHORIZATION, CREDENTIAL)
            .build()

    /** The example pull request, open and clean: merge is offered, reopen is not. */
    @Bean
    fun pullFixture(): TckFixture = object : TckFixture {
        override val collection = "pulls"
        override val offered = TckAct(
            key = "github:merge",
            path = "merge",
            body = """{"merge_method": "squash"}""",
            invalidBody = """{"merge_method": "sideways"}""",
            invalidPointer = "/merge_method",
        )
        override val absent = TckAct(
            key = "github:reopen",
            path = "reopen",
            body = "{}",
            reason = "The pull request is already open.",
        )
        override val credential = CREDENTIAL
        override val searchTerm = "Amazing"

        // GitHub's paths, as the connector's resource file declares them: read from the pull request's own URL,
        // since its repository is embedded, and kept in the state as hr:address.
        override fun address(id: Long) = "/repos/octocat/Hello-World/pulls/$id"
        override fun variables(id: Long) = mapOf("owner" to "octocat", "repo" to "Hello-World", "pull_number" to "$id")
        override val pointers = mapOf(
            "owner" to "/hr:address/owner",
            "repo" to "/hr:address/repo",
            "pull_number" to "/hr:address/pull_number",
        )
        override val searchTarget = "/repos/{owner}/{repo}/pulls{?state,sort,direction}"
        override val searchValues = mapOf("owner" to "octocat", "repo" to "Hello-World", "state" to "open")
        override val lookupTarget = "/repos/{owner}/{repo}/pulls/{pull_number}"

        override suspend fun ready(): Long {
            github.pulls.clear()
            github.pulls += Pull("octocat", "Hello-World", NUMBER, "open", "Amazing new feature", head = HEAD)
            return NUMBER
        }
    }

    private companion object {
        const val CREDENTIAL = "Bearer ada-token"
        const val NUMBER = 1347L
        const val HEAD = "6dcb09b5b57875f334f61aebed695e2e4193db5e"
    }
}

@CucumberContextConfiguration
@ContextConfiguration(classes = [GitHubThroughGateway::class, TckWorld::class])
class GitHubSpringConfig
