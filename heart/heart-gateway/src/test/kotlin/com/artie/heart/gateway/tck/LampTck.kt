package com.artie.heart.gateway.tck

import com.artie.heart.Acted
import com.artie.heart.ConcurrentWriteException
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.ServingLimits
import com.artie.heart.fixtures.Dim
import com.artie.heart.fixtures.InstallLamp
import com.artie.heart.fixtures.Lamp
import com.artie.heart.fixtures.Lamps
import com.artie.heart.gateway.Gateway
import com.artie.heart.gateway.GatewaySpec
import com.artie.heart.gateway.UpstreamAuth
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
import com.artie.heart.http4k.HeartSettings
import com.artie.heart.tck.TckAct
import com.artie.heart.tck.TckFixture
import com.artie.heart.tck.TckWorld
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.spring.CucumberContextConfiguration
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.concurrent.ConcurrentHashMap

/**
 * The upstream: the lamp, served by heart-http4k, and called by the gateway
 * only as plain JSON, its OpenAPI twin, with each answer linking the
 * affordance set of the lamp's state. The gateway reads its OpenAPI
 * description and nothing else of the lamp's declaration.
 */
class LampUpstream {
    val lamps = ConcurrentHashMap<Long, Lamp>()

    private val resource: HeartResource<*, *> = HeartResource(
        layers = Layers(Lamps.type(Lamps.profile("home"))),
        load = { id -> lamps[id] ?: throw ResourceNotFoundException("no lamp $id") },
        perform = { lamp, act, input ->
            val next = when (act) {
                "dim" -> lamp.copy(brightness = checkNotNull(input.bind(Dim::class.java).brightness))
                "switchOff" -> lamp.copy(status = "OFF")
                else -> lamp.copy(status = "ON")
            }.let { it.copy(version = it.version + 1) }
            if (!lamps.replace(lamp.id, lamp, next)) throw ConcurrentWriteException("lamp ${lamp.id} was written first")
            Acted(next)
        },
        performAtBookmark = { _, input ->
            val id = (lamps.keys.maxOrNull() ?: 0) + 1
            val lamp = Lamp(id, "OFF", checkNotNull(input.bind(InstallLamp::class.java).brightness), version = 1)
            lamps[id] = lamp
            Acted(lamp)
        },
        find = { name, _ ->
            lamps.values.map { Found(it.id, "Lamp ${it.id}") }
                .filter { name == null || it.title.contains(name, ignoreCase = true) }
        },
    )

    /** The upstream admits only the gateway's own token. */
    val handler: HttpHandler = HeartHttp(
        listOf(resource),
        Credentials.parse(users = "", tokens = UPSTREAM_TOKEN_SHA256),
        HeartSettings(limits = ServingLimits(affordanceLinks = true)),
    )
}

@Configuration(proxyBeanMethods = false)
class LampThroughGateway {
    private val upstream = LampUpstream()

    /** The gateway reads the upstream's own OpenAPI description, and names its relations in the profile "home". */
    private val gateway = Gateway(
        GatewaySpec(upstream.handler(Request(Method.GET, "/openapi.json")).bodyString()),
        upstream.handler,
        profile = "home",
        auth = UpstreamAuth.Bearer(UPSTREAM_TOKEN),
    )

    @Bean
    fun mapper(): ObjectMapper = ObjectMapper()

    /** The resource types the gateway built from the upstream's description. */
    @Bean
    fun gatewayResources(): List<HeartResource<*, *>> = gateway.resources

    /** The gateway, on a port the system picks. */
    @Bean(destroyMethod = "stop")
    fun server(): Http4kServer =
        gateway.handler(Credentials.parse(users = "", tokens = GATEWAY_TOKEN_SHA256)).asServer(Jetty(0)).start()

    /** Every request carries the gateway's token by default, as a domain's test context may. */
    @Bean
    fun client(server: Http4kServer): WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:${server.port()}")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $GATEWAY_TOKEN")
            .build()

    /** Lamp 7, on: dimming is offered, switching on is not. */
    @Bean
    fun lampFixture(): TckFixture = object : TckFixture {
        override val collection = "lamps"
        override val offered = TckAct(
            key = "home:dim",
            path = "dim",
            body = """{"brightness": 20}""",
            invalidBody = """{"brightness": 0}""",
            invalidPointer = "/brightness",
            method = "PUT",
        )
        override val absent = TckAct(
            key = "home:switch-on",
            path = "switch-on",
            body = "{}",
            reason = "The lamp is already on.",
        )
        override val credential = "Bearer $GATEWAY_TOKEN"
        override val searchTerm = "lamp 7"
        override val bookmarkAct = TckAct(
            key = "home:install-lamp",
            path = "install-lamp",
            body = """{"brightness": 40}""",
            invalidBody = """{"brightness": 101}""",
            invalidPointer = "/brightness",
        )

        override suspend fun ready(): Long {
            upstream.lamps.clear()
            upstream.lamps[7] = Lamp(7, "ON", brightness = 50, version = 3)
            return 7
        }
    }
}

/** The gateway admits its callers' token; the upstream admits only the gateway's. Each is held as its SHA-256. */
internal const val GATEWAY_TOKEN = "lamp-token"
internal const val GATEWAY_TOKEN_SHA256 = "a1011f8452b5270bb1b7dd59ea154bca26711ace2c21d4607c0fd4d5b26d14cb"
internal const val UPSTREAM_TOKEN = "upstream-token"
internal val UPSTREAM_TOKEN_SHA256: String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(UPSTREAM_TOKEN.toByteArray()).joinToString("") { "%02x".format(it) }

@CucumberContextConfiguration
@ContextConfiguration(classes = [LampThroughGateway::class, TckWorld::class])
class CucumberSpringConfig
