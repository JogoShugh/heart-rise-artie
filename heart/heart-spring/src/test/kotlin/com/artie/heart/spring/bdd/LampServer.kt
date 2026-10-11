package com.artie.heart.spring.bdd

import com.artie.heart.Layers
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.fixtures.Dim
import com.artie.heart.fixtures.Lamp
import com.artie.heart.fixtures.Lamps
import com.artie.heart.spring.Acted
import com.artie.heart.spring.HeartAutoConfiguration
import com.artie.heart.spring.HeartResource
import io.cucumber.spring.CucumberContextConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import java.util.concurrent.ConcurrentHashMap

/** The lamps this test server holds. */
class LampStore {
    val lamps = ConcurrentHashMap<Long, Lamp>()
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
        load = { id -> store.lamps[id] ?: throw ResourceNotFoundException("no lamp $id") },
        perform = { id, act, input ->
            val lamp = store.lamps.getValue(id)
            val next = when (act) {
                "dim" -> lamp.copy(brightness = checkNotNull(input.bind(Dim::class.java).brightness))
                "switchOff" -> lamp.copy(status = "OFF")
                else -> lamp.copy(status = "ON")
            }.let { it.copy(version = it.version + 1) }
            store.lamps[id] = next
            Acted(next)
        },
    )
}

@CucumberContextConfiguration
@WebFluxTest
@Import(LampServer::class, HeartAutoConfiguration::class, HttpWorld::class)
class CucumberSpringConfig
