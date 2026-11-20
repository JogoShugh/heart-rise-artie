package com.artie.heart.binding

import com.artie.heart.binding.HttpWorld.Companion.accept
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.http.HttpHeaders

/** Acts as a browser sends them, and the refusals it is shown (html.feature). */
class HtmlActSteps(private val http: HttpWorld) {

    private fun page(): Document = Jsoup.parse(String(http.last.responseBody ?: ByteArray(0)))

    @When("a browser sends {word} {string} with {string} and the HTML's ETag")
    fun browserSends(method: String, path: String, form: String) =
        sendUnder(method, path, form, resourceOf(path), "html")

    @When("a browser sends {word} {string} with {string} and the ETag of its hal-schema-forms representation")
    fun browserSendsJsonETag(method: String, path: String, form: String) =
        sendUnder(method, path, form, resourceOf(path), "hal-schema-forms")

    @When("a browser sends {word} {string} with {string} and the bookmark's ETag")
    fun browserSendsAtBookmark(method: String, path: String, form: String) = sendUnder(method, path, form, "/", "html")

    @Then("the response is an HTML problem reading {string}")
    fun htmlProblem(title: String) {
        assertEquals("text/html;charset=UTF-8", http.last.responseHeaders.contentType.toString())
        assertEquals(title, page().selectFirst(".problem strong")?.text())
    }

    @Then("the HTML problem names the field {string}")
    fun problemNames(pointer: String) = assertTrue(page().select(".problem li[data-pointer=$pointer]").isNotEmpty())

    /** Sends the form under the ETag of [read] as [representation] is given it. */
    private fun sendUnder(method: String, path: String, form: String, read: String, representation: String) {
        val held = http.get(read, mapOf(HttpHeaders.ACCEPT to accept(representation)))
        http.sendForm(method, path, form, mapOf(HttpHeaders.IF_MATCH to checkNotNull(held.responseHeaders.eTag)))
    }

    /** The resource an act's target belongs to: `/lamps/7/actions/dim` is `/lamps/7`'s. */
    private fun resourceOf(target: String) = target.substringBefore("/actions/")
}
