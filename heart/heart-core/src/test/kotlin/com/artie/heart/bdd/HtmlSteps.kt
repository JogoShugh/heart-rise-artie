package com.artie.heart.bdd

import com.artie.heart.ActRequests
import com.artie.heart.HtmlFields
import com.artie.heart.HtmlList
import com.artie.heart.ServiceIndex
import com.artie.heart.HtmlProblem
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
    private var input: String = ""

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

    @When("the form {string} is read by the schema {}")
    fun formRead(form: String, schema: String) {
        input = ActRequests.fromForm(schema, form)
    }

    @Then("the form's input is {}")
    fun inputIs(json: String) = assertEquals(ObjectMapper().readTree(json), ObjectMapper().readTree(input))

    @When("the problem {} is rendered as HTML")
    fun problemRendered(problem: String) {
        field = Jsoup.parseBodyFragment(HtmlProblem.of(problem)).body()
    }

    @Then("the HTML problem reads {string}")
    fun problemReads(title: String) = assertEquals(title, field.selectFirst(".problem strong")?.text())

    @Then("the HTML problem names the field {string} as {string}")
    fun problemNames(pointer: String, message: String) =
        assertEquals(message, field.selectFirst(".problem li[data-pointer=$pointer]")?.text())

    @When("the representation {} is rendered as HTML")
    fun representationRendered(body: String) {
        page = Jsoup.parse(HtmlView.render(JsonTrees.plain(body) as Map<*, *>, "e", emptyList(), emptyList()))
    }

    @Then("the page is headed {string}")
    fun headed(heading: String) = assertEquals(heading, page.selectFirst("h1")?.text())

    @Then("the HTML lists {string} as {string} linked to {string}")
    fun embeddedListed(heading: String, words: String, href: String) {
        val section = checkNotNull(
            page.select("section.embedded").firstOrNull { it.selectFirst("h2")?.text() == heading },
        )
        val link = checkNotNull(section.selectFirst("li a"))
        assertEquals(words, link.text())
        assertEquals(href, link.attr("href"))
    }

    @When("the service index of lamps is rendered as HTML under the validator {string}")
    fun indexRendered(etag: String) {
        page = Jsoup.parse(HtmlView.index(ServiceIndex(listOf(world.type)), etag))
    }

    @Then("the {string} dialog goes to the address {string}")
    fun goesTo(key: String, template: String) {
        val form = form(key)
        assertEquals("get", form.attr("method"))
        assertEquals(template, form.attr("data-template"))
    }

    @When("the page {} is rendered as HTML titled {string}")
    fun pageRendered(body: String, title: String) {
        page = Jsoup.parse(HtmlList.page(JsonTrees.plain(body) as Map<*, *>, title))
    }

    @Then("the grid has these rows")
    fun gridRows(table: DataTable) {
        val heads = page.select(".grid thead th").map { it.text() }.dropLast(1)
        val rows = page.select(".grid tbody tr").map { row ->
            mapOf("href" to row.attr("data-item")) + heads.zip(row.select("td").map { it.text() })
        }
        assertEquals(table.asMaps(), rows)
    }

    @Then("each row's acts are read from its match when asked")
    fun rowActs() = page.select(".grid tbody tr").forEach { row ->
        assertEquals(row.attr("data-item"), row.selectFirst("button[data-acts]")?.attr("data-acts"))
    }

    @Then("the page links {string} to {string}")
    fun pageLinks(rel: String, href: String) =
        assertEquals(href, page.selectFirst("nav.paging a[rel=$rel]")?.attr("href"))

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
