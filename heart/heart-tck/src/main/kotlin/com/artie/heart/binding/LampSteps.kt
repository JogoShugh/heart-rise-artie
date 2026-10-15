package com.artie.heart.binding

import com.artie.heart.PastEvent
import com.artie.heart.fixtures.Lamp
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Duration
import java.time.Instant

/** The lamps the server holds: set up by a scenario, and checked after its requests. */
class LampSteps(private val store: LampStore) {
    @Before
    fun emptyStore() {
        store.lamps.clear()
        store.events.clear()
        store.writeAfterNextRead.clear()
    }

    @Given("lamp {long} is {string} at version {int} in the lamp server")
    fun lampIs(id: Long, status: String, version: Int) {
        store.lamps[id] = Lamp(id, status, brightness = BRIGHTNESS, version = version)
    }

    @Given("lamps {long} to {long} are {string} in the lamp server")
    fun lamps(from: Long, to: Long, status: String) {
        for (id in from..to) store.lamps[id] = Lamp(id, status, brightness = BRIGHTNESS, version = 1)
    }

    @Given("another client's dim to {int} lands on lamp {long} just after the server checks the next act's If-Match")
    fun dimLandsAfterCheck(brightness: Int, id: Long) {
        store.writeAfterNextRead[id] = { lamp -> lamp.copy(brightness = brightness, version = lamp.version + 1) }
    }

    @Given("lamp {long} was switched on {int} days ago and dimmed to {int} {int} day ago, in the lamp server")
    fun lampWithPast(id: Long, onAgo: Long, brightness: Int, dimAgo: Long) {
        val events = listOf(
            PastEvent(1, "SwitchedOn", Instant.now().minus(Duration.ofDays(onAgo)), emptyMap<String, Any>()),
            PastEvent(2, "Dimmed", Instant.now().minus(Duration.ofDays(dimAgo)), mapOf("brightness" to brightness)),
        )
        store.events[id] = events
        store.lamps[id] = store.at(id, Instant.now())
    }

    @Then("lamp {long} has brightness {int} at version {int}")
    fun hasBrightness(id: Long, brightness: Int, version: Int) {
        val lamp = store.lamps.getValue(id)
        assertEquals(brightness to version, lamp.brightness to lamp.version, "$lamp")
    }

    @Then("the lamp server holds only lamp {long}")
    fun holdsOnly(id: Long) = assertEquals(setOf(id), store.lamps.keys)

    @Then("lamp {long} is still at version {int}")
    fun stillAt(id: Long, version: Int) = assertEquals(version, store.lamps.getValue(id).version)

    private companion object {
        /** Every lamp a scenario sets up shines at half its brightness. */
        const val BRIGHTNESS = 50
    }
}
