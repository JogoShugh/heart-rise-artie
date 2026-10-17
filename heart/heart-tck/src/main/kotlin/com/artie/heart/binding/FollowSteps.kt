package com.artie.heart.binding

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.When
import org.springframework.http.HttpHeaders

/**
 * What a client does with what it read: follows links, fills forms, and
 * acts with the validator and negotiation headers it read with.
 */
class FollowSteps(private val http: HttpWorld) {

    @When("the client gets it again at Prefer {string} with the ETag it holds")
    fun againAtOtherRung(prefer: String) {
        val held = http.layer1()
        val headers = http.negotiation(held) + mapOf("Prefer" to prefer, HttpHeaders.IF_NONE_MATCH to etag())
        http.get(held.url.path, headers)
    }

    @When("the client posts {} to {string} with the ETag and headers it synced with")
    fun postSynced(body: String, path: String) {
        http.post(path, body, http.negotiation(http.layer1()) + ifMatch())
    }

    @When("the client posts {} to {string} with the ETag it holds but Prefer {string}")
    fun postWithOtherPrefer(body: String, path: String, prefer: String) {
        val synced = http.negotiation(http.layer1()) - "Prefer"
        http.post(path, body, (if (prefer.isBlank()) synced else synced + ("Prefer" to prefer)) + ifMatch())
    }

    @When("the client follows the {string} link")
    fun follow(rel: String) {
        val accept = checkNotNull(http.last.requestHeaders.getFirst(HttpHeaders.ACCEPT))
        http.get(http.json.path("_links").path(rel).path("href").asText(), mapOf(HttpHeaders.ACCEPT to accept))
    }

    @When("the client follows the same {string} link with Prefer {string}")
    fun followAgain(rel: String, prefer: String) {
        http.get(http.json(http.layer1()).path("_links").path(rel).path("href").asText(), mapOf("Prefer" to prefer))
    }

    @When("the client expands {string} by its template pointers and posts {} with the ETag it holds")
    fun expandAndPost(key: String, body: String) {
        val held = http.layer1()
        val form = http.json.path("_forms").path(key)
        val pointers = form.path("hr:templatePointers")
        val target = VARIABLE.replace(form.path("_links").path("target").path("href").asText()) {
            http.json(held).at(pointers.path(it.groupValues[1]).asText()).asText()
        }
        http.post(target, body, http.negotiation(held) + ifMatch())
    }

    @When("the client fills the {string} form with id {long} and follows it")
    fun fillAndFollow(key: String, id: Long) {
        val href = http.json.path("_forms").path(key).path("_links").path("target").path("href").asText()
        http.get(href.replace("{id}", id.toString()), mapOf(HttpHeaders.ACCEPT to "application/hal+json"))
    }

    @When("the client fills the {string} form with these values and follows it")
    fun fillWithAndFollow(key: String, table: DataTable) {
        val values = table.asMaps().associate { it.getValue("field") to it.getValue("value") }
        val target = http.json.path("_forms").path(key).path("_links").path("target").path("href").asText()
        http.get(expand(target, values), mapOf(HttpHeaders.ACCEPT to "application/hal+json"))
    }

    private fun etag(): String = checkNotNull(http.layer1().responseHeaders.eTag)

    private fun ifMatch(): Pair<String, String> = HttpHeaders.IF_MATCH to etag()

    private companion object {
        val VARIABLE = Regex("""\{([A-Za-z0-9_]+)}""")
        val QUERY = Regex("""\{\?([A-Za-z0-9_,]+)}""")

        /** RFC 6570 levels 1 and 3, as far as forms use them: `{name}` and `{?a,b}`, leaving out unfilled ones. */
        fun expand(template: String, values: Map<String, String>): String {
            val query = QUERY.replace(template) { match ->
                val pairs = match.groupValues[1].split(",").mapNotNull { name -> values[name]?.let { "$name=$it" } }
                if (pairs.isEmpty()) "" else pairs.joinToString("&", "?")
            }
            return VARIABLE.replace(query) { checkNotNull(values[it.groupValues[1]]) }
        }
    }
}
