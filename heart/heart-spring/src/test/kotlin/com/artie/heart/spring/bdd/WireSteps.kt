package com.artie.heart.spring.bdd

import com.artie.heart.Rung
import com.artie.heart.ServingLimits
import com.artie.heart.fixtures.Lamp
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.EntityExchangeResult
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * What only a served response shows: headers, links followed, acts sent with
 * the headers a client read with, and the server's limits. heart-core's own
 * features check how each body is rendered.
 */
class WireSteps(
    private val client: WebTestClient,
    private val store: LampStore,
    private val http: HttpWorld,
    private val limits: ServingLimits,
) {
    private val json: JsonNode get() = json(http.last)

    /** The limits are one bean for the whole run; every scenario starts at the defaults. */
    @After
    fun restoreLimits() {
        limits.maxRung = Rung.L2
        limits.pageSize = ServingLimits.DEFAULT_PAGE_SIZE
    }

    @Given("lamps {long} to {long} are {string} in the lamp server")
    fun lamps(from: Long, to: Long, status: String) =
        (from..to).forEach { store.lamps[it] = Lamp(it, status, brightness = 50, version = 1) }

    @Given("the server lists {int} lamps per page")
    fun pageSize(size: Int) {
        limits.pageSize = size
    }

    @Given("the server serves rungs up to {word}")
    fun servesUpTo(rung: String) {
        limits.maxRung = Rung.valueOf(rung)
    }

    @When("a client gets {string} with Accept {string} and Accept-Profile {string}")
    fun getNegotiated(path: String, accept: String, profile: String) =
        get(path, mapOf(HttpHeaders.ACCEPT to accept, "Accept-Profile" to profile).filterValues { it.isNotBlank() })

    @When("the client gets it again at Prefer {string} with the ETag it holds")
    fun againAtOtherRung(prefer: String) {
        val held = layer1()
        get(held.url.path, negotiation(held) + mapOf("Prefer" to prefer, HttpHeaders.IF_NONE_MATCH to etag(held)))
    }

    @When("the client posts {} to {string} with the ETag and headers it synced with")
    fun postSynced(body: String, path: String) = post(path, body, negotiation(layer1()) + ifMatch())

    @When("the client posts {} to {string} with the ETag it holds but Prefer {string}")
    fun postWithOtherPrefer(body: String, path: String, prefer: String) {
        val synced = negotiation(layer1()) - "Prefer"
        post(path, body, (if (prefer.isBlank()) synced else synced + ("Prefer" to prefer)) + ifMatch())
    }

    @When("the client follows the {string} link")
    fun follow(rel: String) {
        val accept = checkNotNull(http.last.requestHeaders.getFirst(HttpHeaders.ACCEPT))
        get(json.path("_links").path(rel).path("href").asText(), mapOf(HttpHeaders.ACCEPT to accept))
    }

    @When("the client follows the same {string} link with Prefer {string}")
    fun followAgain(rel: String, prefer: String) {
        get(json(layer1()).path("_links").path(rel).path("href").asText(), mapOf("Prefer" to prefer))
    }

    @When("the client expands {string} by its template pointers and posts {} with the ETag it holds")
    fun expandAndPost(key: String, body: String) {
        val held = layer1()
        val form = json.path("_forms").path(key)
        val pointers = form.path("hr:templatePointers")
        val target = VARIABLE.replace(form.path("_links").path("target").path("href").asText()) {
            json(held).at(pointers.path(it.groupValues[1]).asText()).asText()
        }
        post(target, body, negotiation(held) + ifMatch())
    }

    @When("the client fills the {string} form with id {long} and follows it")
    fun fillAndFollow(key: String, id: Long) {
        val href = json.path("_forms").path(key).path("_links").path("target").path("href").asText()
        get(href.replace("{id}", id.toString()), mapOf(HttpHeaders.ACCEPT to HAL_JSON))
    }

    @Then("the response carries its forms under {string}")
    fun formsUnder(key: String) = assertTrue(json.has(key), "no $key in $json")

    @Then("the response has no Preference-Applied header")
    fun noPreferenceApplied() = assertNull(http.last.responseHeaders.getFirst(PREFERENCE_APPLIED))

    @Then("Preference-Applied is {string}")
    fun preferenceApplied(value: String) = assertEquals(value, http.last.responseHeaders.getFirst(PREFERENCE_APPLIED))

    @Then("Preference-Applied is absent")
    fun preferenceAppliedAbsent() = noPreferenceApplied()

    @Then("the home document's {string} hint {string} is {string}")
    fun hint(rel: String, hint: String, value: String) =
        assertEquals(value, hints(rel).path(hint).joinToString(", ") { it.asText() })

    @Then("the home document's {string} hint {string} is absent")
    fun hintAbsent(rel: String, hint: String) = assertFalse(hints(rel).has(hint), "${hints(rel)}")

    @Then("the response has no body")
    fun noBody() = assertTrue((http.last.responseBody ?: ByteArray(0)).isEmpty(), "a body came back")

    @Then("the response body is exactly {}")
    fun bodyIsExactly(expected: String) = assertEquals(ObjectMapper().readTree(expected), json)

    @Then("the response carries no {word} header")
    fun carriesNo(name: String) =
        assertNull(http.last.responseHeaders.getFirst(name), "$name: ${http.last.responseHeaders.getFirst(name)}")

    @Then("the response has no Vary header")
    fun noVary() = assertTrue(http.last.responseHeaders.vary.isEmpty(), "${http.last.responseHeaders.vary}")

    @Then("the last two responses have identical bytes")
    fun identical() {
        val (first, second) = http.results.takeLast(2)
        assertArrayEquals(first.responseBody, second.responseBody)
    }

    @Then("the problem's {string} member is {string}")
    fun member(name: String, value: String) = assertEquals(value, json.path(name).asText(), "$json")

    @Then("the problem lists a violation at {string}")
    fun violation(pointer: String) =
        assertTrue(json.path("violations").any { it.path("pointer").asText() == pointer }, "$json")

    @Then("the problem's detail is {string}")
    fun detail(detail: String) = assertEquals(detail, json.path("detail").asText(), "$json")

    @Then("the response declares the CURIE {string} as {string}")
    fun curie(name: String, href: String) {
        val curies = json.path("_links").path("curies")
        val declared = curies.any {
            it.path("name").asText() == name && it.path("href").asText() == href && it.path("templated").asBoolean()
        }
        assertTrue(declared, "no CURIE $name -> $href in $curies")
    }

    @Then("the {string} form has a title and a description")
    fun described(key: String) {
        val form = json.path("_forms").path(key)
        assertTrue(form.path("title").asText().isNotBlank(), "no title on $key: $form")
        assertTrue(form.path("description").asText().isNotBlank(), "no description on $key: $form")
    }

    @Then("the response has no member {string}")
    fun hasNo(path: String) {
        val node = path.split('.').fold(json) { at, key -> at.path(key) }
        assertTrue(node.isMissingNode, "$path present: $node")
    }

    @Then("the template targeting {string} has the property {string}, required, from {int} to {int}")
    fun templateProperty(target: String, name: String, min: Int, max: Int) {
        val template = json.path("_templates").first { it.path("target").asText() == target }
        val property = template.path("properties").first { it.path("name").asText() == name }
        val actual = listOf("required", "min", "max").map { property.path(it).asText() }
        assertEquals(listOf("true", min.toString(), max.toString()), actual, "$property")
    }

    @Then("no property of any template is read-only")
    fun noReadOnly() {
        val properties = json.path("_templates").flatMap { it.path("properties") }
        val readOnly = properties.filter { it.path("readOnly").asBoolean() }
        assertTrue(readOnly.isEmpty(), "read-only: $readOnly")
    }

    @Then("the ALPS document describes {string} as {string}")
    fun alps(id: String, type: String) {
        val descriptor = json.path("alps").path("descriptor").firstOrNull { it.path("id").asText() == id }
        assertEquals(type, checkNotNull(descriptor) { "no descriptor $id" }.path("type").asText())
    }

    @Then("the response links {string} to {string}")
    fun linksTo(rel: String, href: String) = assertEquals(href, json.path("_links").path(rel).path("href").asText())

    @Then("the response has no link under {string}")
    fun noLink(rel: String) = assertFalse(json.path("_links").has(rel), "found $rel in ${json.path("_links")}")

    @Then("the two indexes offer forms with the same titles")
    fun sameTitles() {
        val (hsf, hf) = http.results.takeLast(2).map(::json)
        val forms = hsf.path("_forms").map { it.path("title").asText() }.toSet()
        assertTrue(forms.isNotEmpty(), "no forms in $hsf")
        assertEquals(forms, hf.path("_templates").map { it.path("title").asText() }.toSet())
    }

    private fun hints(rel: String): JsonNode =
        json.path("resources").fields().asSequence().first { it.key.endsWith(rel) }.value.path("hints")

    /** The latest read of a lamp's Layer 1, whatever came after it. */
    private fun layer1(): EntityExchangeResult<ByteArray> =
        http.results.last { it.method.name() == "GET" && it.url.path.matches(Regex("/lamps/\\d+")) }

    private fun negotiation(read: EntityExchangeResult<ByteArray>): Map<String, String> =
        listOf(HttpHeaders.ACCEPT, "Accept-Profile", "Prefer")
            .mapNotNull { name -> read.requestHeaders.getFirst(name)?.let { name to it } }.toMap()

    private fun ifMatch(): Pair<String, String> = HttpHeaders.IF_MATCH to etag(layer1())

    private fun etag(read: EntityExchangeResult<ByteArray>): String = checkNotNull(read.responseHeaders.eTag)

    private fun get(path: String, headers: Map<String, String>): EntityExchangeResult<ByteArray> =
        exchange(client.get().uri(path), headers)

    private fun post(path: String, body: String, headers: Map<String, String>) {
        exchange(client.post().uri(path).header(HttpHeaders.CONTENT_TYPE, "application/json").bodyValue(body), headers)
    }

    private fun exchange(
        spec: WebTestClient.RequestHeadersSpec<*>,
        headers: Map<String, String>,
    ): EntityExchangeResult<ByteArray> {
        headers.forEach { (name, value) -> spec.header(name, value) }
        return spec.exchange().expectBody().returnResult().also { http.results = http.results + it }
    }

    private fun json(result: EntityExchangeResult<ByteArray>): JsonNode =
        ObjectMapper().readTree(result.responseBody ?: ByteArray(0))

    private companion object {
        const val HAL_JSON = "application/hal+json"
        const val PREFERENCE_APPLIED = "Preference-Applied"
        val VARIABLE = Regex("""\{([A-Za-z0-9_]+)}""")
    }
}
