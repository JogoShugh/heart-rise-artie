package com.artie.heart.binding

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse

/** The resources a response embeds (SPEC §6.1, `_embedded`), and the members its state keeps. */
class EmbeddedSteps(private val http: HttpWorld) {

    @Then("the response member {string} is {string}")
    fun memberIs(path: String, value: String) {
        val node = path.split('.').fold(http.json) { at, key -> at.path(key) }
        assertEquals(value, node.asText(), "$path in ${http.json}")
    }

    @Then("the response embeds {string} at {string}")
    fun embeds(relation: String, href: String) {
        val embedded = http.json.path("_embedded").path(relation)
        assertEquals(href, embedded.path("_links").path("self").path("href").asText(), "${http.json}")
        assertFalse(embedded.has("_forms") || embedded.has("_templates"), "forms in $embedded")
    }
}
