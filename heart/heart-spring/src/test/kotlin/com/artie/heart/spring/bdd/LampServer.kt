package com.artie.heart.spring.bdd

import com.artie.heart.ActInput
import com.artie.heart.ConcurrentWriteException
import com.artie.heart.RuleRefusedException
import com.artie.heart.Found
import com.artie.heart.Layers
import com.artie.heart.Past
import com.artie.heart.PastEvent
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.fixtures.Dim
import com.artie.heart.fixtures.InstallLamp
import com.artie.heart.fixtures.Lamp
import com.artie.heart.fixtures.Lamps
import com.artie.heart.Acted
import com.artie.heart.spring.HeartAutoConfiguration
import com.artie.heart.HeartResource
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** The lamps this test server holds. */
class LampStore {
    val lamps = ConcurrentHashMap<Long, Lamp>()

    /** What happened to each lamp, for the lamps whose past a scenario sets up. */
    val events = ConcurrentHashMap<Long, List<PastEvent>>()

    /**
     * Another client's write, staged by a scenario to land on a lamp just
     * after the server next reads it: between an act's If-Match check and the act.
     */
    val writeAfterNextRead = ConcurrentHashMap<Long, (Lamp) -> Lamp>()

    /** Lamp [id] as the server reads it; a staged write lands right after. */
    fun read(id: Long): Lamp {
        val lamp = lamps[id] ?: throw notFound(id)
        writeAfterNextRead.remove(id)?.let { write -> lamps[id] = write(lamp) }
        return lamp
    }

    /** A lamp as of [instant]: switched off at brightness 50 until its events say otherwise. */
    fun at(id: Long, instant: Instant): Lamp =
        events[id].orEmpty().filter { !it.occurredAt.isAfter(instant) }.fold(Lamp(id, "OFF", 50, 0)) { lamp, past ->
            when (past.type) {
                "SwitchedOn" -> lamp.copy(status = "ON")
                else -> lamp.copy(brightness = (past.event as Map<*, *>)["brightness"] as Int)
            }.copy(version = past.sequenceNo)
        }
}

/**
 * A server that declares one resource type, the lamp, and nothing else: no
 * controllers of its own. It says only how a lamp is read and what each act does.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class LampServer {
    @Bean
    fun lampStore() = LampStore()

    @Bean
    fun lamps(store: LampStore): HeartResource<*, *> = HeartResource(
        layers = Layers(Lamps.type(Lamps.profile("home"))),
        load = { id -> store.read(id) },
        perform = { lamp, act, input ->
            val next = when (act) {
                "dim" -> lamp.copy(brightness = brightness(lamp, input))
                "switchOff" -> lamp.copy(status = "OFF")
                else -> lamp.copy(status = "ON")
            }.let { it.copy(version = it.version + 1) }
            // Recorded only if the lamp is still the one the client's ETag matched.
            if (!store.lamps.replace(lamp.id, lamp, next)) {
                throw ConcurrentWriteException("lamp ${lamp.id} was written first")
            }
            Acted(next)
        },
        past = Past(
            at = { id, instant -> store.lamps[id]?.let { store.at(id, instant) } ?: throw notFound(id) },
            history = { id -> store.lamps[id]?.let { store.events[id].orEmpty() } ?: throw notFound(id) },
        ),
        performAtBookmark = { _, input ->
            val id = (store.lamps.keys.maxOrNull() ?: 0) + 1
            val lamp = Lamp(id, "OFF", checkNotNull(input.bind(InstallLamp::class.java).brightness), version = 1)
            store.lamps[id] = lamp
            Acted(lamp)
        },
        find = { name, id ->
            store.lamps.values.sortedBy { it.id }
                .map { Found(it.id, "Lamp ${it.id}") }
                .filter { name == null || it.title.contains(name, ignoreCase = true) }
                .filter { id == null || it.id == id }
        },
    )
}

private fun notFound(id: Long) = ResourceNotFoundException("no lamp $id")

/** The brightness to dim [lamp] to. A rule only the lamp knows: not the brightness it already has. */
private fun brightness(lamp: Lamp, input: ActInput): Int {
    val brightness = checkNotNull(input.bind(Dim::class.java).brightness)
    if (brightness == lamp.brightness) {
        throw RuleRefusedException.at("/brightness", "The lamp is already at $brightness")
    }
    return brightness
}

@CucumberContextConfiguration
@WebFluxTest
@Import(LampServer::class, HeartAutoConfiguration::class, HttpWorld::class)
class CucumberSpringConfig
