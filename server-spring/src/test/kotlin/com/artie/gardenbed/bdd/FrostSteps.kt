package com.artie.gardenbed.bdd

import com.artie.gardenbed.bdd.HttpWorld.Companion.HAL_SCHEMA_FORMS_ACCEPT
import com.artie.gardenbed.service.GardenWeather
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking

/** The weather, and clients that act on what they read before it changed. */
class FrostSteps(private val http: HttpWorld, private val weather: GardenWeather) {
    private var etagRead: String? = null

    /** The weather, as the operator reports it (POST /actuator/frost): not a client's act, so outside HEART. */
    @When("the frost hits")
    fun frost() {
        runBlocking { weather.frost() }
    }

    @Given("a client read bed {long}")
    fun readBed(id: Long) {
        etagRead = checkNotNull(http.get("/beds/$id", mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT)).headers.eTag)
    }

    @When("the client posts {} to {word} on bed {long} with the ETag it read")
    fun postWithEtagRead(body: String, action: String, id: Long) {
        val headers = mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT, "If-Match" to checkNotNull(etagRead))
        http.post("/beds/$id/actions/$action", body, headers)
    }

    /** The OpenAPI twin's client: plain JSON, and no ETag to send. */
    @When("a plain JSON client posts {} to {word} on bed {long}")
    fun postPlain(body: String, action: String, id: Long) {
        http.post("/beds/$id/actions/$action", body, mapOf("Accept" to "application/json"))
    }
}
