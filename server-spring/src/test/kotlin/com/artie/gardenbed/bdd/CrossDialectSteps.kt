package com.artie.gardenbed.bdd

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals

/** Steps for SPEC §6.10.6: two dialects, one legality decision. */
class CrossDialectSteps(private val http: HttpWorld) {

    /** [targets] is a comma-separated list of concrete act URIs. */
    @Then("both dialects offer exactly the act targets {}")
    fun bothDialectsOffer(targets: String) {
        val expected = targets.split(',').map { it.trim() }.toSet()
        val (halForms, schemaForms) = http.exchanges.takeLast(2)
        val templateTargets = halForms.json.path("_templates").map { it.path("target").asText() }.toSet()
        val formTargets = schemaForms.json.path("_forms")
            .map { it.path("_links").path("target").path("href").asText() }
            .toSet()
        assertEquals(expected, templateTargets, "HAL-FORMS: ${halForms.json}")
        assertEquals(expected, formTargets, "HAL Schema Forms: ${schemaForms.json}")
    }

    @Then("the two responses have different ETags")
    fun differentETags() {
        val (first, second) = http.exchanges.takeLast(2)
        assertNotEquals(first.headers.eTag, second.headers.eTag)
    }
}
