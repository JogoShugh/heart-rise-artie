package com.artie.heart.http4k.tck

import com.artie.heart.Acted
import com.artie.heart.ConcurrentWriteException
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.fixtures.Dim
import com.artie.heart.fixtures.InstallLamp
import com.artie.heart.fixtures.Lamp
import com.artie.heart.fixtures.Lamps
import com.artie.heart.tck.TckAct
import com.artie.heart.tck.TckFixture
import com.artie.heart.tck.TckWorld
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.spring.CucumberContextConfiguration
import org.http4k.core.HttpHandler
import com.artie.heart.http4k.Credentials
import com.artie.heart.http4k.HeartHttp
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
 * The lamp, declared exactly as heart-tck's own lamp server declares it, and
 * served by heart-http4k on a random port. No Spring serves it: Spring only
 * wires the conformance features' steps, and their client talks to the port.
 */
@Configuration(proxyBeanMethods = false)
class LampOverHttp4k {
    private val lamps = ConcurrentHashMap<Long, Lamp>()

    @Bean
    fun lamps(): HeartResource<*, *> = HeartResource(
        layers = Layers(Lamps.type(Lamps.profile("home"))),
        load = { id -> lamps[id] ?: throw ResourceNotFoundException("no lamp $id") },
        perform = { lamp, act, input ->
            val next = when (act) {
                "dim" -> lamp.copy(brightness = checkNotNull(input.bind(Dim::class.java).brightness))
                "switchOff" -> lamp.copy(status = "OFF")
                else -> lamp.copy(status = "ON")
            }.let { it.copy(version = it.version + 1) }
            // Recorded only if the lamp is still the one the client's ETag matched.
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

    @Bean
    fun mapper(): ObjectMapper = ObjectMapper()

    /** The server under test, on a port the system picks. */
    @Bean(destroyMethod = "stop")
    fun server(resources: List<HeartResource<*, *>>): Http4kServer = app(resources).asServer(Jetty(0)).start()

    /** Every request carries the lamp token by default, as a domain's test context may. */
    @Bean
    fun client(server: Http4kServer): WebTestClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:${server.port()}")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $LAMP_TOKEN")
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
        )
        override val absent = TckAct(
            key = "home:switch-on",
            path = "switch-on",
            body = "{}",
            reason = "The lamp is already on.",
        )
        override val credential = "Bearer $LAMP_TOKEN"
        override val searchTerm = "lamp 7"
        override val bookmarkAct = TckAct(
            key = "home:install-lamp",
            path = "install-lamp",
            body = """{"brightness": 40}""",
            invalidBody = """{"brightness": 101}""",
            invalidPointer = "/brightness",
        )

        override suspend fun ready(): Long {
            lamps.clear()
            lamps[7] = Lamp(7, "ON", brightness = 50, version = 3)
            return 7
        }
    }

    private fun app(resources: List<HeartResource<*, *>>): HttpHandler =
        HeartHttp(resources, Credentials.parse(users = "", tokens = LAMP_TOKEN_SHA256))
}

/** The lamp server admits one Bearer token; it is held as its SHA-256 in hex. */
internal const val LAMP_TOKEN = "lamp-token"
internal const val LAMP_TOKEN_SHA256 = "a1011f8452b5270bb1b7dd59ea154bca26711ace2c21d4607c0fd4d5b26d14cb"

@CucumberContextConfiguration
@ContextConfiguration(classes = [LampOverHttp4k::class, TckWorld::class])
class CucumberSpringConfig
