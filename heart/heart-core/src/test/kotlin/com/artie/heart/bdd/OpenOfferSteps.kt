package com.artie.heart.bdd

import com.artie.heart.ContentAddress
import com.artie.heart.Layers
import com.artie.heart.Offers
import com.artie.heart.ResourceType
import com.artie.heart.Rung
import com.artie.heart.SetRendering
import com.artie.heart.SharedContent
import com.artie.heart.fixtures.Lamp
import com.artie.heart.fixtures.Lamps
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/** A lamp type whose offer keys are met at run time, and the content the server holds for them. */
class OpenOfferSteps {
    private val profile = Lamps.profile("home")
    private lateinit var layers: Layers<Lamp, String>
    private lateinit var shared: SharedContent
    private val rendered = mutableListOf<Map<String, Any?>>()
    private var refusal: Throwable? = null

    @Given("a lamp type whose offers are open, with no keys declared")
    fun openType() {
        val declared = Lamps.type(profile)
        val type = ResourceType(
            collection = declared.collection,
            profile = profile,
            acts = declared.acts,
            offers = Offers(
                keys = emptyList(),
                offered = { key ->
                    when (key) {
                        "ON" -> listOf("switchOff", "dim")
                        "OFF" -> listOf("switchOn")
                        else -> listOf("explode")
                    }
                },
                whyAbsent = { key, _ ->
                    if (key == "ON") "The lamp is already on." else "The lamp is off; switch it on."
                },
                open = true,
            ),
            state = declared.state,
        )
        layers = Layers(type)
        shared = SharedContent(listOf(layers))
    }

    @Given("the lamp type as declared, with closed offers")
    fun closedType() {
        layers = Layers(Lamps.type(profile))
        shared = SharedContent(listOf(layers))
    }

    @When("^(?:a|another) lamp in the key \"(.+)\" is rendered at L(\\d)$")
    fun render(key: String, rung: Int) {
        val lamp = Lamp(rendered.size + 1L, key, brightness = 50, version = 1)
        refusal = runCatching { rendered += layers.layer1(lamp, Rung.entries[rung]) }.exceptionOrNull()
    }

    @Then("the server holds the affordance set its {string} link names, matching the link's integrity")
    fun holdsSet(rel: String) {
        val link = link(rendered.last(), rel)
        val set = checkNotNull(shared.affordanceSets[address(link, Layers.AFFORDANCE_SETS)]) { "no set at $link" }
        assertEquals(link["integrity"], ContentAddress.integrity(set))
    }

    @Then("that set offers exactly {string}")
    fun offersExactly(keys: String) {
        val link = link(rendered.last(), Layers.FORMS_REL)
        val set = shared.affordanceSets.getValue(address(link, Layers.AFFORDANCE_SETS))
        val forms = ObjectMapper().readTree(set).path("_forms").fieldNames().asSequence().toList()
        assertEquals(keys.split(", ").toSet(), forms.toSet())
    }

    @Then("both link the same affordance set")
    fun sameSet() = assertEquals(link(rendered[0], Layers.FORMS_REL), link(rendered[1], Layers.FORMS_REL))

    @Then("the server holds {int} affordance set for each rendering")
    fun setsPerRendering(count: Int) = assertEquals(count * SetRendering.entries.size, shared.affordanceSets.size)

    @Then("the server holds {int} affordance sets for it before any lamp is rendered")
    fun setsAtStart(count: Int) = assertEquals(count, shared.affordanceSets.size)

    @Then("the server holds the explanation its {string} link names")
    fun holdsExplanation(rel: String) {
        val link = link(rendered.last(), rel)
        val explanation = shared.explanations[address(link, Layers.EXPLANATIONS)]
        assertEquals(link["integrity"], explanation?.let { ContentAddress.integrity(it) })
    }

    @Then("the explanation says {string} is absent because {string}")
    fun says(key: String, reason: String) {
        val link = link(rendered.last(), Layers.UNAVAILABLE_REL)
        val explanation = ObjectMapper().readTree(shared.explanations.getValue(address(link, Layers.EXPLANATIONS)))
        val reasons = explanation.path("unavailable")
            .associate { it.path("rel").asText() to it.path("reason").asText() }
        assertEquals(reason, reasons[profile.reference(key.substringAfter(':'))], "reasons: $reasons")
    }

    @Then("rendering it is refused, saying {}")
    fun refused(message: String) {
        assertTrue(refusal is IllegalArgumentException, "not refused: ${refusal ?: "it was rendered"}")
        assertEquals(message, refusal?.message)
    }

    @Suppress("UNCHECKED_CAST")
    private fun link(body: Map<String, Any?>, rel: String): Map<String, Any?> =
        (body.getValue("_links") as Map<String, Any?>).getValue(rel) as Map<String, Any?>

    private fun address(link: Map<String, Any?>, prefix: String): String =
        (link.getValue("href") as String).removePrefix(prefix)
}
