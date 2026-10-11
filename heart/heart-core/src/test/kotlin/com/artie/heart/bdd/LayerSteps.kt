package com.artie.heart.bdd

import com.artie.heart.ContentAddress
import com.artie.heart.Dialect
import com.artie.heart.JsonTrees
import com.artie.heart.Layers
import com.artie.heart.Rung
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@Suppress("UNCHECKED_CAST")
class LayerSteps(private val world: LampWorld) {
    private val profile get() = world.profile
    private val layers get() = world.layers
    private val lamps get() = world.lamps
    private var body: Map<String, Any?> = emptyMap()
    private var set: Map<String, Any?> = emptyMap()
    private var setLink: Map<String, Any?> = emptyMap()
    private var explanation: Map<String, Any?> = emptyMap()

    @When("lamp {long} is rendered at L{int}")
    fun rendered(id: Long, n: Int) {
        body = layers.layer1(lamps.getValue(id), Rung.valueOf("L$n"))
    }

    @Then("the body has {string} {string} and {string} {long}")
    fun bodyHas(key: String, value: String, idKey: String, id: Long) {
        assertEquals(value, body[key])
        assertEquals(id, body[idKey])
    }

    @Then("the body links {string} to {string}")
    fun bodyLinks(rel: String, href: String) = assertEquals(href, link(body, rel)["href"])

    @Then("the body offers exactly {string}")
    fun bodyOffers(keys: String) = assertEquals(names(keys), forms(body).keys)

    @Then("the body has no forms")
    fun noForms() = assertFalse("_forms" in body)

    @Then("the {string} form targets {string} with POST")
    fun formTargets(key: String, href: String) {
        val form = forms(body).getValue(key) as Map<String, Any?>
        assertEquals(href, link(form, "target")["href"])
        assertFalse("templated" in link(form, "target"))
        assertEquals("POST", form["method"])
        assertFalse("hr:templatePointers" in form)
    }

    /** At L0 the forms are in the body; at L1 they are in the affordance set fetched after it. */
    @Then("the {string} form embeds its schema, with its address as {string}")
    fun formEmbeds(key: String, member: String) = embedsSchema(forms(set.ifEmpty { body }), key, member)

    @Then("the body links {string} to an explanation, with its integrity")
    fun linksExplanation(rel: String) {
        val href = link(body, rel)["href"] as String
        val served = checkNotNull(layers.explanations[href.removePrefix(Layers.EXPLANATIONS)])
        assertEquals(ContentAddress.integrity(served), link(body, rel)["integrity"])
    }

    @Then("the body links {string} to an affordance set, with its integrity, type and profile")
    fun linksSet(rel: String) {
        setLink = link(body, rel)
        assertTrue((setLink["href"] as String).startsWith(Layers.AFFORDANCE_SETS))
        assertEquals(Dialect.JSONSCHEMA.mediaType, setLink["type"])
        assertEquals(Dialect.JSONSCHEMA.profile, setLink["profile"])
        assertNotNull(setLink["integrity"])
    }

    @When("the affordance set is fetched")
    fun fetchSet() {
        set = JsonTrees.plain(served()) as Map<String, Any?>
    }

    @Then("its bytes hash to its address and match the link's integrity")
    fun setHashes() {
        val address = (setLink["href"] as String).removePrefix(Layers.AFFORDANCE_SETS)
        assertEquals(address, ContentAddress.address(served()))
        assertEquals(setLink["integrity"], ContentAddress.integrity(served()))
    }

    @Then("the set offers exactly {string}")
    fun setOffers(keys: String) = assertEquals(names(keys), forms(set).keys)

    @Then("the {string} form targets {string}, templated, with the pointer {string} at {string}")
    fun setFormTargets(key: String, href: String, variable: String, pointer: String) {
        val form = forms(set).getValue(key) as Map<String, Any?>
        assertEquals(href, link(form, "target")["href"])
        assertEquals(true, link(form, "target")["templated"])
        assertEquals(mapOf(variable to pointer), form["hr:templatePointers"])
    }

    @Then("the {string} form refers to its schema by {string}, with its integrity")
    fun setFormRefers(key: String, member: String) {
        val schema = (forms(set).getValue(key) as Map<String, Any?>)["schema"] as Map<String, Any?>
        val address = (schema.getValue(member) as String).removePrefix(Layers.SCHEMAS)
        val served = checkNotNull(layers.schemas[address])
        assertEquals(ContentAddress.integrity(served), schema["hr:integrity"])
    }

    @Then("lamps {long} and {long} link to the same affordance set at L1 and at L2")
    fun sameSet(a: Long, b: Long) {
        for (rung in listOf(Rung.L1, Rung.L2)) {
            val first = link(layers.layer1(lamps.getValue(a), rung), "hr:forms")["href"]
            assertEquals(first, link(layers.layer1(lamps.getValue(b), rung), "hr:forms")["href"])
        }
    }

    @When("the explanation is fetched")
    fun fetchExplanation() {
        val href = link(body, "hr:unavailable")["href"] as String
        val served = layers.explanations.getValue(href.removePrefix(Layers.EXPLANATIONS))
        explanation = JsonTrees.plain(served) as Map<String, Any?>
    }

    @Then("it explains {string} as {string}")
    fun explains(curie: String, reason: String) {
        val entry = (explanation["unavailable"] as List<Map<String, Any?>>)
            .single { it["rel"] == profile.reference(curie.substringAfter(':')) }
        assertEquals(reason, entry["reason"])
        assertEquals(profile.relation(curie.substringAfter(':')).title, entry["title"])
    }

    @Then("it explains nothing else")
    fun nothingElse() = assertEquals(1, (explanation["unavailable"] as List<*>).size)

    @Then("every affordance set, schema and explanation of the lamp type is listed by its address")
    fun everyListed() {
        // Two keys at two renderings; three acts and installing, but switching on and
        // off share one schema, so one address; one explanation per key.
        assertEquals(4, layers.affordanceSets.size)
        assertEquals(3, layers.schemas.size)
        assertEquals(2, layers.explanations.size)
    }

    @Then("each listed body hashes to its address")
    fun eachHashes() {
        (layers.affordanceSets + layers.schemas + layers.explanations).forEach { (address, served) ->
            assertEquals(address, ContentAddress.address(served))
        }
    }

    private fun served(): String =
        layers.affordanceSets.getValue((setLink["href"] as String).removePrefix(Layers.AFFORDANCE_SETS))

    private fun embedsSchema(forms: Map<String, Any?>, key: String, member: String) {
        val schema = (forms.getValue(key) as Map<String, Any?>)["schema"] as Map<String, Any?>
        val address = (schema.getValue(member) as String).removePrefix(Layers.SCHEMAS)
        val served = checkNotNull(layers.schemas[address])
        assertEquals(JsonTrees.plain(served), schema - member)
    }

    private fun names(keys: String): Set<String> = keys.split(',').map { it.trim() }.toSet()

    private fun forms(of: Map<String, Any?>): Map<String, Any?> = of["_forms"] as Map<String, Any?>

    private fun link(of: Map<String, Any?>, rel: String): Map<String, Any?> =
        (of["_links"] as Map<String, Any?>).getValue(rel) as Map<String, Any?>
}
