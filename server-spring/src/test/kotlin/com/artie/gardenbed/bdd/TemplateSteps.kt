package com.artie.gardenbed.bdd

import com.artie.gardenbed.bdd.HttpWorld.Companion.FORMS_REL
import com.fasterxml.jackson.databind.JsonNode
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Steps for SPEC §6.9.1. Expansion here follows the [0.5] rule and nothing
 * else: `{name}` takes the Layer 1 top-level property `name`, which must be
 * a string or a number. No other lookup, so a test cannot pass by guessing.
 */
class TemplateSteps(private val http: HttpWorld) {

    @Then("every target is a template whose variables are top-level properties of bed {long}")
    fun everyTargetResolves(id: Long) {
        val layer1 = layer1For(id).json
        val forms = http.last.json.path("_forms")
        assertTrue(forms.size() > 0, "no forms in ${http.last.json}")
        forms.fields().forEach { (name, form) ->
            val target = form.path("_links").path("target")
            assertTrue(target.path("templated").asBoolean(), "$name target is not templated: $target")
            val variables = VARIABLE.findAll(target.path("href").asText()).map { it.groupValues[1] }.toList()
            assertTrue(variables.isNotEmpty(), "$name template has no variables: $target")
            variables.forEach { v ->
                val value = layer1.path(v)
                assertTrue(value.isTextual || value.isNumber, "{$v} in $name does not resolve from Layer 1 $layer1")
            }
        }
    }

    @When("the client expands the {string} target from bed {long} and posts {} with the ETag it holds")
    fun expandAndPost(affordance: String, id: Long, body: String) {
        val layer1 = layer1For(id)
        val href = http.last.json.path("_forms").path(affordance).path("_links").path("target").path("href").asText()
        val etag = checkNotNull(layer1.headers.eTag) { "client holds no ETag" }
        http.post(expand(href, layer1.json), body, mapOf("If-Match" to etag))
    }

    @Then("the two affordance-set links are identical")
    fun twoLinksIdentical() {
        val (first, second) = http.exchanges.takeLast(2)
            .map { it.json.path("_links").path(FORMS_REL).path("href").asText() }
        assertTrue(first.startsWith("/affordance-sets/"), "no affordance-set link: $first")
        assertEquals(first, second)
    }

    private fun layer1For(id: Long): Exchange = http.exchanges.last { it.path == "/beds/$id" }

    private fun expand(template: String, layer1: JsonNode): String =
        VARIABLE.replace(template) { match -> layer1.path(match.groupValues[1]).asText() }

    private companion object {
        val VARIABLE = Regex("""\{([A-Za-z0-9_]+)}""")
    }
}
