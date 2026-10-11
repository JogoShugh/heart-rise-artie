package com.artie.heart.tck.lamp

import com.artie.heart.Acted
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.fixtures.Dim
import com.artie.heart.fixtures.InstallLamp
import com.artie.heart.fixtures.Lamp
import com.artie.heart.fixtures.Lamps
import com.artie.heart.spring.HeartAutoConfiguration
import com.artie.heart.tck.TckAct
import com.artie.heart.tck.TckFixture
import com.artie.heart.tck.TckWorld
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.util.concurrent.ConcurrentHashMap

/**
 * A server that declares only the lamp, and writes no HTTP code: the proof,
 * by the conformance features, that a declaration is all a domain needs.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class LampServer {
    private val lamps = ConcurrentHashMap<Long, Lamp>()

    @Bean
    fun lamps(): HeartResource<*, *> = HeartResource(
        layers = Layers(Lamps.type(Lamps.profile("home"))),
        load = { id -> lamps[id] ?: throw ResourceNotFoundException("no lamp $id") },
        perform = { id, act, input ->
            val lamp = lamps.getValue(id)
            val next = when (act) {
                "dim" -> lamp.copy(brightness = checkNotNull(input.bind(Dim::class.java).brightness))
                "switchOff" -> lamp.copy(status = "OFF")
                else -> lamp.copy(status = "ON")
            }.let { it.copy(version = it.version + 1) }
            lamps[id] = next
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
}

@CucumberContextConfiguration
@WebFluxTest
@Import(LampServer::class, HeartAutoConfiguration::class, TckWorld::class)
class CucumberSpringConfig
