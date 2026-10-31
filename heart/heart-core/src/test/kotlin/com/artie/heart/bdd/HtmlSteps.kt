package com.artie.heart.bdd

import com.artie.heart.HtmlFields
import com.artie.heart.HtmlView
import com.artie.heart.JsonTrees
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/** A resource rendered as HTML, read back as a browser would (html.feature). */
class HtmlSteps(private val world: LampWorld) {
    private lateinit var page: Document
    private lateinit var field: Element

    @When("lamp {long} is rendered as HTML")
    fun rendered(id: Long) = renderedUnder(id, "seq-3.html.L0.test")

    @When("lamp {long} is rendered as HTML under the validator {string}")
    fun renderedUnder(id: Long, etag: String) {
        page = Jsoup.parse(HtmlView.of(world.layers, world.lamps.getValue(id), etag))
    }

    @Then("the HTML shows the state")
    fun state(table: DataTable) = table.asMaps().forEach { row ->
        val term = checkNotNull(page.selectFirst("dl.state dt[title=${row.getValue("member")}]")) { row }
        assertEquals(row.getValue("label"), term.text())
        assertEquals(row.getValue("value"), term.nextElementSibling()?.text())
    }

    @Then("the HTML shows these acts, in order")
    fun acts(table: DataTable) {
        val shown = page.select(".acts button.act").map { button ->
            val greyed = button.attr("aria-disabled") == "true"
            mapOf(
                "act" to button.attr("data-rel"),
                "label" to button.text(),
                "offered" to if (greyed) "no" else "yes",
                "reason" to if (greyed) page.getElementById(button.attr("aria-describedby"))?.text().orEmpty() else "",
            )
        }
        assertEquals(table.asMaps().map { row -> row.mapValues { it.value.orEmpty() } }, shown)
    }

    @Then("the {string} dialog sends {word} to {string} with If-Match {string}")
    fun sends(key: String, method: String, target: String, etag: String) {
        val form = form(key)
        assertEquals(target, form.attr("hx-${method.lowercase()}"), form.outerHtml())
        assertEquals(etag, ObjectMapper().readTree(form.attr("hx-headers")).path("If-Match").asText())
    }

    @Then("the {string} dialog asks for these fields")
    fun fields(key: String, table: DataTable) {
        val asked = form(key).select(".field").map { field ->
            val control = control(field)
            mapOf(
                "field" to control.attr("name"),
                "label" to label(field),
                "control" to kind(field),
                "required" to if (control.hasAttr("required")) "yes" else "no",
                "constraints" to constraints(control),
            )
        }
        assertEquals(table.asMaps().map { it + ("constraints" to parsed(it.getValue("constraints"))) }, asked)
    }

    @Then("the {string} dialog asks for no fields")
    fun noFields(key: String) {
        assertTrue(form(key).select(".field").isEmpty())
        assertTrue(form(key).select("button[type=submit]").isNotEmpty())
    }

    @When("a field {string} with the schema {} is rendered as HTML")
    fun fieldRendered(name: String, schema: String) {
        val html = HtmlFields.of("act-test", name, JsonTrees.plain(schema) as Map<*, *>, false)
        field = checkNotNull(Jsoup.parseBodyFragment(html).selectFirst(".field"))
    }

    @Then("^the field is a (.+) with (.+)$")
    fun fieldIs(control: String, constraints: String) {
        assertEquals(control, kind(field))
        assertEquals(parsed(constraints), constraints(control(field)))
    }

    @Then("its label reads {string} and its hint reads {string}")
    fun words(label: String, hint: String) {
        assertEquals(label, label(field))
        assertEquals(hint, field.selectFirst(".hint")?.text())
    }

    @Then("the rendered field has no element {string} or {string}")
    fun noElements(first: String, second: String) {
        assertNull(field.selectFirst(first))
        assertNull(field.selectFirst(second))
    }

    private fun form(key: String): Element = checkNotNull(page.selectFirst("dialog form[data-rel=$key]")) { key }

    private fun control(field: Element): Element = checkNotNull(field.selectFirst("select, textarea, input"))

    private fun label(field: Element): String = checkNotNull(field.selectFirst("label")).ownText().trim()

    private fun kind(field: Element): String {
        val control = control(field)
        return when {
            control.tagName() != "input" -> control.tagName()
            control.attr("type") == "text" && field.selectFirst("[data-list]") != null -> "text list"
            else -> control.attr("type")
        }
    }

    /** The control's constraints, as `min=1 max=100`; a choice's options and default; "none" when it has none. */
    private fun constraints(control: Element): Map<String, String> {
        val bounds = CONSTRAINTS.filter { control.hasAttr(it) }.associateWith { control.attr(it) }
        val options = control.select("option")
        val choices = if (options.isEmpty()) emptyMap() else mapOf(
            "options" to options.joinToString(",") { it.attr("value") },
            "selected" to options.first { it.hasAttr("selected") }.attr("value"),
        )
        return bounds + choices
    }

    private fun parsed(constraints: String): Map<String, String> =
        if (constraints == "none") emptyMap() else PAIR.findAll(constraints).associate {
            it.groupValues[1] to it.groupValues[2]
        }

    private companion object {
        val CONSTRAINTS = listOf("min", "max", "step", "minlength", "maxlength", "pattern", "placeholder")
        val PAIR = Regex("(\\w+)=(.*?)(?= \\w+=|$)")
    }
}
