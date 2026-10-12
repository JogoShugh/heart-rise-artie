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

    /** The bookmark's act, with the bookmark's current ETag as its validator. */
    @When("a client builds a bed from the bookmark with {}")
    fun build(body: String) {
        val index = http.get("/", mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT))
        val etag = checkNotNull(index.headers.eTag) { "the bookmark has no ETag" }
        http.post("/actions/build-bed", body, mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT, "If-Match" to etag))
    }

    @Then("the answer is 201, naming the new bed in Location")
    fun created() {
        assertEquals(201, http.last.status, String(http.last.bytes))
        assertTrue(http.last.headers.location.toString().contains("/beds/"), "${http.last.headers.location}")
    }

    @Then("the new bed is {string}, named {string}, {int} by {int} feet")
    fun newBed(status: String, label: String, widthFt: Int, lengthFt: Int) {
        bed = http.get(http.last.headers.location.toString(), mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT)).json
        val actual = listOf("status", "label", "widthFt", "lengthFt").map { current().path(it).asText() }
        assertEquals(listOf(status, label, widthFt.toString(), lengthFt.toString()), actual)
    }

    @When("a client reads the new bed as of {string}")
    fun readNewBedAsOf(instant: String) {
        val bed = checkNotNull(http.last.headers.location) { "no bed was built" }
        http.get("$bed?asOf=$instant", mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT))
    }

    @Then("the answer is {int}")
    fun answer(status: Int) = assertEquals(status, http.last.status, String(http.last.bytes))

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

    @Then("bed {long} still has moisture {int}")
    fun moisture(id: Long, moisture: Int) {
        read(id)
        assertEquals(moisture, current().path("moistureLevel").asInt())
    }

    @Then("the act is refused by the rule {string} at {string}")
    fun ruleRefused(reason: String, pointer: String) {
        val problem = http.last.json
        val violations = problem.path("violations").map { it.path("pointer").asText() to it.path("message").asText() }
        assertEquals(
            listOf(422, PROBLEM_TYPES + "rule-refused", listOf(pointer to reason)),
            listOf(http.last.status, problem.path("type").asText(), violations),
        )
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

    private companion object {
        const val PROBLEM_TYPES = "https://github.com/jogoshugh/heart-rise/problems/"
    }
}
