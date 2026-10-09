package com.artie.gardenbed.bdd

import com.artie.gardenbed.bdd.HttpWorld.Companion.FORMS_REL
import com.fasterxml.jackson.databind.JsonNode
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Steps for SPEC §6.9.1 and HAL-SCHEMA-FORMS-HR Rule 1. Expansion here uses
 * only the form's declared hr:templatePointers (JSON Pointers into Layer 1),
 * never a same-name guess, so a test cannot pass unless the server declares
 * every variable.
 */
class TemplateSteps(private val http: HttpWorld) {

    @Then("every target is a template whose variables each have a template pointer into bed {long}")
    fun everyTargetResolves(id: Long) {
        val layer1 = layer1For(id).json
        val forms = http.last.json.path("_forms")
        assertTrue(forms.size() > 0, "no forms in ${http.last.json}")
        forms.fields().forEach { (name, form) ->
            val target = form.path("_links").path("target")
            assertTrue(target.path("templated").asBoolean(), "$name target is not templated: $target")
            val variables = VARIABLE.findAll(target.path("href").asText()).map { it.groupValues[1] }.toList()
            assertTrue(variables.isNotEmpty(), "$name template has no variables: $target")
            val pointers = form.path("hr:templatePointers")
            variables.forEach { v ->
                assertTrue(pointers.path(v).isTextual, "{$v} in $name has no template pointer: $form")
                val value = layer1.at(pointers.path(v).asText())
                assertTrue(value.isTextual || value.isNumber, "{$v} in $name does not resolve in Layer 1 $layer1")
            }
        }
    }

    @When("the client expands {string} from bed {long} by its template pointers and posts {} with its ETag")
    fun expandAndPost(affordance: String, id: Long, body: String) {
        val layer1 = layer1For(id)
        val form = http.last.json.path("_forms").path(affordance)
        val href = form.path("_links").path("target").path("href").asText()
        val etag = checkNotNull(layer1.headers.eTag) { "client holds no ETag" }
        val headers = http.negotiationHeaders(layer1) + ("If-Match" to etag)
        http.post(expand(href, form.path("hr:templatePointers"), layer1.json), body, headers)
    }

    @Then("the two affordance-set links are identical")
    fun twoLinksIdentical() {
        val (first, second) = http.exchanges.takeLast(2)
            .map { it.json.path("_links").path(FORMS_REL).path("href").asText() }
        assertTrue(first.startsWith("/affordance-sets/"), "no affordance-set link: $first")
        assertEquals(first, second)
    }

    private fun layer1For(id: Long): Exchange = http.exchanges.last { it.path == "/beds/$id" }

    private fun expand(template: String, pointers: JsonNode, layer1: JsonNode): String =
        VARIABLE.replace(template) { match -> layer1.at(pointers.path(match.groupValues[1]).asText()).asText() }

    private companion object {
        val VARIABLE = Regex("""\{([A-Za-z0-9_]+)}""")
    }
}
