package com.artie.gardenbed.bdd

import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** Steps for forms named by link relations, their descriptions, and the ALPS profile (SPEC §6.1, §8.5). */
class RelationSteps(private val http: HttpWorld) {

    @Then("the response declares the CURIE {string} as {string}")
    fun declaresCurie(name: String, href: String) {
        val curies = http.last.json.path("_links").path("curies")
        val declared = curies.any {
            it.path("name").asText() == name && it.path("href").asText() == href && it.path("templated").asBoolean()
        }
        assertTrue(declared, "no CURIE $name -> $href in $curies")
    }

    @Then("the response offers exactly the forms {string}")
    fun offersForms(names: String) {
        val keys = http.last.json.path("_forms").fieldNames().asSequence().toSet()
        assertEquals(names.split(',').map { it.trim() }.toSet(), keys)
    }

    @Then("the {string} form has a title and a description")
    fun formDescribed(name: String) {
        val form = http.last.json.path("_forms").path(name)
        assertTrue(form.path("title").asText().isNotBlank(), "no title on $name: $form")
        assertTrue(form.path("description").asText().isNotBlank(), "no description on $name: $form")
    }

    @Then("the {string} form's field {string} has a title and a description")
    fun fieldDescribed(name: String, field: String) {
        val property = http.last.json.path("_forms").path(name).path("schema").path("properties").path(field)
        assertTrue(property.path("title").asText().isNotBlank(), "no title on $field: $property")
        assertTrue(property.path("description").asText().isNotBlank(), "no description on $field: $property")
    }

    @Then("the ALPS document describes {string} as {string}")
    fun alpsDescribes(id: String, type: String) {
        val descriptors = http.last.json.path("alps").path("descriptor")
        val descriptor = descriptors.firstOrNull { it.path("id").asText() == id }
        assertTrue(descriptor != null, "no descriptor $id in $descriptors")
        assertEquals(type, descriptor!!.path("type").asText())
        assertTrue(descriptor.path("doc").path("value").asText().isNotBlank(), "no doc for $id")
    }

    @Then("the response has no {string}")
    fun hasNo(path: String) {
        val node = path.split('.').fold(http.last.json) { at, key -> at.path(key) }
        assertTrue(node.isMissingNode, "$path present: $node")
    }
}
