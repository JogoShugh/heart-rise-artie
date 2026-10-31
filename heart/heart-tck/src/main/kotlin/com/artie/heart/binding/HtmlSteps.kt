package com.artie.heart.binding

import com.artie.heart.binding.HttpWorld.Companion.accept
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Then
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders

/** A resource's HTML as a binding serves it to a browser (html.feature). */
class HtmlSteps(private val http: HttpWorld) {

    private fun page(): Document = Jsoup.parse(String(http.last.responseBody ?: ByteArray(0)))

    @Then("the HTML's forms send the response's ETag as If-Match")
    fun formsSendETag() {
        val etag = checkNotNull(http.last.responseHeaders.eTag)
        val forms = page().select("dialog form[data-rel]")
        assertTrue(forms.isNotEmpty())
        forms.forEach { form ->
            val sent = ObjectMapper().readTree(form.attr("hx-headers")).path("If-Match").asText()
            assertEquals(etag, sent, form.attr("data-rel"))
        }
    }

    @Then("the HTML of {string} offers exactly the forms of its hal-schema-forms representation")
    fun agrees(path: String) {
        val json = http.json(http.get(path, mapOf(HttpHeaders.ACCEPT to accept("hal-schema-forms"))))
        val html = Jsoup.parse(String(http.get(path, mapOf(HttpHeaders.ACCEPT to accept("html"))).responseBody!!))
        val offered = html.select(".acts button.act:not([aria-disabled=true])").map { it.attr("data-rel") }.toSet()
        val dialogs = html.select("dialog form[data-rel]").map { it.attr("data-rel") }.toSet()
        val forms = json.path("_forms").fieldNames().asSequence().toSet()
        assertEquals(forms, offered)
        assertEquals(forms, dialogs)
    }

    @Then("the grid lists")
    fun gridLists(table: DataTable) {
        val rows = page().select(".grid tbody tr").map { row ->
            mapOf("href" to row.attr("data-item"), "Title" to row.selectFirst("td")?.text())
        }
        assertEquals(table.asMaps(), rows)
    }

    @Then("the page shows {string} as {string}")
    fun pageShows(member: String, value: String) =
        assertEquals(value, page().selectFirst("dl.state dt[title=$member]")?.nextElementSibling()?.text())

    @Then("each asset the HTML names is served by the server with Cache-Control {string}")
    fun assets(cacheControl: String) {
        val names = page().select("script[src], link[rel=stylesheet]")
            .map { it.attr("src").ifEmpty { it.attr("href") } }
        assertEquals(ASSETS, names.size, "$names")
        names.forEach { name ->
            val served = http.get(name)
            assertTrue(served.status.is2xxSuccessful, name)
            assertEquals(cacheControl, served.responseHeaders.cacheControl, name)
            assertTrue((served.responseBody?.size ?: 0) > 0, name)
        }
    }

    private companion object {
        /** htmx, heart-forms.js and heart.css. */
        const val ASSETS = 3
    }
}
