package com.artie.heart.tck

import com.artie.heart.ContentAddress
import com.artie.heart.Dialect
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

/** The bookmark's affordances: each form the server offers, by its integrity, and the way to its schema. */
class TckAffordanceSteps(private val world: TckWorld) {

    @When("a client gets {string} as hal-schema-forms with Prefer {string}")
    fun getPathWithPrefer(path: String, prefer: String) {
        world.get(path, mapOf(HttpHeaders.ACCEPT to Dialect.JSONSCHEMA.mediaType, "Prefer" to prefer))
    }

    /** At L2 a schema is linked by `$ref`; what is served there must hash to the `hr:integrity` beside it. */
    @Then("every schema the set links by \$ref is served there, matching its integrity")
    fun linkedSchemas() {
        val held = world.json
        val linked = world.forms().values.map { it.path("schema") }.filter { it.has("\$ref") }
        linked.forEach { schema ->
            val read = world.get(schema.path("\$ref").asText())
            val body = String(checkNotNull(read.responseBody) { "no schema at $schema" }, Charsets.UTF_8)
            assertEquals(schema.path("hr:integrity").asText(), ContentAddress.integrity(body), "$schema")
        }
        // At L1 the set holds each schema in place, so there is nothing to follow.
        val inPlace = held.path("_forms").all { !it.path("schema").has("\$ref") }
        assertTrue(linked.isNotEmpty() || inPlace, "set: $held")
    }

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
