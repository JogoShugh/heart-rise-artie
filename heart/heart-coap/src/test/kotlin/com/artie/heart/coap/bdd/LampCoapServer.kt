package com.artie.heart.coap.bdd

import com.artie.heart.Acted
import com.artie.heart.ActInput
import com.artie.heart.ConcurrentWriteException
import com.artie.heart.RuleRefusedException
import com.artie.heart.Found
import com.artie.heart.HeartResource
import com.artie.heart.Layers
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.fixtures.Dim
import com.artie.heart.fixtures.InstallLamp
import com.artie.heart.fixtures.Lamp
import com.artie.heart.fixtures.Lamps
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import java.util.concurrent.ConcurrentHashMap

/** The lamps this test server holds. */
class LampStore {
    val lamps = ConcurrentHashMap<Long, Lamp>()
}

/**
 * A server that declares one resource type, the lamp, and nothing else: no
 * CoAP code of its own. heart-coap's auto-configuration serves it on UDP.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class LampCoapServer {
    @Bean
    fun lampStore() = LampStore()

    @Bean
    fun lamps(store: LampStore): HeartResource<*, *> = HeartResource(
        layers = Layers(Lamps.type(Lamps.profile("home"))),
        load = { id -> store.lamps[id] ?: throw ResourceNotFoundException("no lamp $id") },
        perform = { lamp, act, input ->
            val next = when (act) {
                "dim" -> lamp.copy(brightness = brightness(lamp, input))
                "switchOff" -> lamp.copy(status = "OFF")
                else -> lamp.copy(status = "ON")
            }.let { it.copy(version = it.version + 1) }
            // Recorded only if the lamp is still the one the client's validator matched.
            if (!store.lamps.replace(lamp.id, lamp, next)) {
                throw ConcurrentWriteException("lamp ${lamp.id} was written first")
            }
            Acted(next)
        },
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

/** The brightness to dim [lamp] to. A rule only the lamp knows: not the brightness it already has. */
private fun brightness(lamp: Lamp, input: ActInput): Int {
    val brightness = checkNotNull(input.bind(Dim::class.java).brightness)
    if (brightness == lamp.brightness) {
        throw RuleRefusedException.at("/brightness", "The lamp is already at $brightness")
    }
    return brightness
}

@CucumberContextConfiguration
@SpringBootTest(
    classes = [LampCoapServer::class],
    // coaps admits one identity, "lamp-client", whose key is "lamp-client-key!" in base64.
    properties = ["heart.coap.port=0", "heart.coaps.port=0", "heart.coaps.psk=lamp-client:bGFtcC1jbGllbnQta2V5IQ=="],
)
class CucumberSpringConfig
