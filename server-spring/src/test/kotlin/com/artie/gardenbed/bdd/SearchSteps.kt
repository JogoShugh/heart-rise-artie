package com.artie.gardenbed.bdd

import com.artie.gardenbed.bdd.HttpWorld.Companion.HAL_SCHEMA_FORMS_ACCEPT
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse

/** The garden's search pages: each bed listed, and embedded with its state (SPEC §6.11). */
class SearchSteps(private val http: HttpWorld) {

    @When("a client searches the beds for {string}")
    fun search(name: String) {
        http.get("/beds?name=$name", mapOf("Accept" to HAL_SCHEMA_FORMS_ACCEPT))
    }

    @Then("the page embeds these beds")
    fun embedsThese(table: DataTable) {
        val beds = http.last.json.path("_embedded").path("item").map { bed ->
            mapOf(
                "self" to bed.path("_links").path("self").path("href").asText(),
                "title" to bed.path("_links").path("self").path("title").asText(),
                "status" to bed.path("status").asText(),
                "cropName" to bed.path("cropName").let { if (it.isNull || it.isMissingNode) "" else it.asText() },
            )
        }
        assertEquals(table.asMaps().map { row -> row.mapValues { it.value.orEmpty() } }, beds, "${http.last.json}")
    }

    @Then("no embedded bed carries forms")
    fun noForms() = http.last.json.path("_embedded").path("item").forEach {
        assertFalse(it.has("_forms") || it.has("_templates"), "$it")
    }
}
