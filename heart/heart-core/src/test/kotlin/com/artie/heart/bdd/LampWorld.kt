package com.artie.heart.bdd

import com.artie.heart.Layers
import com.artie.heart.Profile
import com.artie.heart.ResourceType
import com.artie.heart.fixtures.Lamp
import com.artie.heart.fixtures.Lamps
import io.cucumber.java.en.Given

/** The lamp type and the lamps of one scenario, shared by every step class. */
class LampWorld {
    lateinit var profile: Profile
    lateinit var type: ResourceType<Lamp, String>
    lateinit var layers: Layers<Lamp, String>
    val lamps = mutableMapOf<Long, Lamp>()

    @Given("a lamp resource type declared with the profile {string}")
    fun aLampType(name: String) {
        profile = Lamps.profile(name)
        type = Lamps.type(profile)
        layers = Layers(type)
    }

    @Given("lamp {long} is {string} at version {int}")
    fun lampIs(id: Long, status: String, version: Int) {
        lamps[id] = Lamp(id, status, brightness = 50, version = version)
    }
}
