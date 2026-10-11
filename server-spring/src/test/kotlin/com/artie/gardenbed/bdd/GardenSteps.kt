package com.artie.gardenbed.bdd

import com.artie.gardenbed.bdd.HttpWorld.Companion.HAL_SCHEMA_FORMS_ACCEPT
import com.fasterxml.jackson.databind.JsonNode
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** A gardener's view of a bed: what it holds, what may be done now, and why the rest may not. */
class GardenSteps(private val http: HttpWorld) {
    private var bed: JsonNode? = null

    @When("a client reads bed {long}")
    fun read(id: Long) {
        bed = http.get("/beds/$id", mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT)).json
    }

    @Then("bed {long} is {string} with crop {string}, growth {int} and moisture {int}")
    fun state(id: Long, status: String, crop: String, growth: Int, moisture: Int) {
        read(id)
        val actual = listOf("status", "cropName", "growthPoints", "moistureLevel").map { current().path(it).asText() }
        assertEquals(listOf(status, crop, growth.toString(), moisture.toString()), actual)
    }

    @Then("bed {long} is {string} with no crop and growth {int}")
    fun emptied(id: Long, status: String, growth: Int) {
        read(id)
        assertEquals(status, current().path("status").asText())
        assertTrue(current().path("cropName").isNull, "${current()}")
        assertEquals(growth, current().path("growthPoints").asInt())
    }

    @Then("the bed offers exactly {string}")
    fun offers(keys: String) {
        val forms = current().path("_forms").fieldNames().asSequence().toSet()
        assertEquals(keys.split(',').map { it.trim() }.toSet(), forms)
    }

    @Then("{string} is absent because {string}")
    fun absent(key: String, reason: String) {
        val explanation = http.get(current().path("_links").path("hr:unavailable").path("href").asText()).json
        val rel = "/profiles/garden#${key.substringAfter(':')}"
        val entry = explanation.path("unavailable").firstOrNull { it.path("rel").asText() == rel }
        assertEquals(reason, checkNotNull(entry) { "$key is not explained: $explanation" }.path("reason").asText())
    }

    /** The bed as last read, in HAL Schema Forms. */
    private fun current(): JsonNode = checkNotNull(bed) { "no bed read yet" }
}
