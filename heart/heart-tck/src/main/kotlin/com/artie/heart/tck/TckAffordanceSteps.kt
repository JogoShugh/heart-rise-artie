package com.artie.heart.tck

import com.artie.heart.Dialect
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

/** The bookmark's affordances: each form the server offers, by its integrity, and the way to its schema. */
class TckAffordanceSteps(private val world: TckWorld) {

    @Then("every form the resource offers is listed there by its integrity, with a link to the schema it uses")
    fun affordancesLinkSchemas() {
        val listed = world.json.path("forms")
        world.get(world.resource, mapOf(HttpHeaders.ACCEPT to Dialect.JSONSCHEMA.mediaType))
        val forms = world.forms()
        assertTrue(forms.isNotEmpty(), "the resource offers no forms")
        forms.forEach { (rel, form) ->
            val entry = listed.path(form.path("hr:integrity").asText())
            assertEquals(rel, entry.path("relation").asText(), "$rel is not listed by its integrity: $listed")
            val schema = entry.path("_links").path("describedby").path("href").asText()
            assertEquals(form.path("schema").path("\$id").asText(), schema, "$rel's schema")
            world.get(schema)
            assertEquals(HttpStatus.OK.value(), world.last.status.value(), "$rel's schema at $schema")
        }
    }
}
