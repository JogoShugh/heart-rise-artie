package com.artie.heart.bdd

import com.artie.heart.Act
import com.artie.heart.AlpsType
import com.artie.heart.Layers
import com.artie.heart.Offers
import com.artie.heart.Profile
import com.artie.heart.Relation
import com.artie.heart.ResourceType
import com.artie.heart.StateView
import io.cucumber.java.en.Given

/** A lamp: the smallest resource type with an act that needs a body and acts that do not. */
data class Lamp(val id: Long, val status: String, val brightness: Int, val version: Int)

/** The lamp type and the lamps of one scenario, shared by every step class. */
class LampWorld {
    lateinit var profile: Profile
    lateinit var type: ResourceType<Lamp, String>
    lateinit var layers: Layers<Lamp, String>
    val lamps = mutableMapOf<Long, Lamp>()

    @Given("a lamp resource type declared with the profile {string}")
    fun aLampType(name: String) {
        profile = Profile(
            name = name,
            title = "Lamps",
            doc = "The acts a lamp offers.",
            relations = listOf(
                Relation("switch-on", AlpsType.UNSAFE, "Switch on", "Turns the lamp on.", "Offered while off."),
                Relation("switch-off", AlpsType.UNSAFE, "Switch off", "Turns the lamp off.", "Offered while on."),
                Relation("dim", AlpsType.IDEMPOTENT, "Dim the lamp", "Sets the brightness.", "Offered while on."),
            ),
        )
        type = ResourceType(
            collection = "lamps",
            profile = profile,
            acts = listOf(
                // Switching on and off take no input, so they share one schema.
                Act.of("switchOn", "switch-on", SwitchOff::class.java),
                Act.of("switchOff", "switch-off", SwitchOff::class.java),
                Act.of("dim", "dim", Dim::class.java),
            ),
            offers = Offers(
                keys = listOf("ON", "OFF"),
                offered = { key -> if (key == "ON") listOf("switchOff", "dim") else listOf("switchOn") },
                whyAbsent = { key, _ ->
                    if (key == "ON") "The lamp is already on." else "The lamp is off; switch it on."
                },
            ),
            state = StateView(
                keyOf = { it.status },
                idOf = { it.id },
                versionOf = { it.version },
                bodyOf = { linkedMapOf("id" to it.id, "status" to it.status, "brightness" to it.brightness) },
            ),
        )
        layers = Layers(type)
    }

    @Given("lamp {long} is {string} at version {int}")
    fun lampIs(id: Long, status: String, version: Int) {
        lamps[id] = Lamp(id, status, brightness = 50, version = version)
    }
}
