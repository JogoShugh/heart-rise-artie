package com.artie.heart.sims.library

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import java.time.LocalDate
import java.util.Base64
import kotlin.test.assertEquals

/** library.feature: Riverside Public Library, read and acted on in memory, as each dialect's client would. */
class LibrarySteps {
    private val library = LibraryService()
    private val mapper = ObjectMapper()
    private var answer: Response? = null
    private var dialect = HSF

    @Given("Riverside Public Library lends Ana Torres these books")
    fun lends(table: DataTable) = library.lend(
        table.asMaps().map { row ->
            Loan(
                id = row.getValue("loan").toLong(),
                title = row.getValue("title"),
                author = row.getValue("author"),
                due = LocalDate.parse(row.getValue("due")),
                renewals = row.getValue("renewals").toInt(),
                holdWaiting = row.getValue("hold waiting") == "yes",
            )
        },
    )

    @When("Ana reads loan {long} as {}")
    fun reads(loan: Long, asked: String) {
        dialect = asked
        answer = send(Method.GET, "/loans/$loan", ACCEPT.getValue(asked))
    }

    @When("someone reads loan {long} as HTML, signed in with card {string} and PIN {string}")
    fun readsSignedIn(loan: Long, card: String, pin: String) {
        answer = send(Method.GET, "/loans/$loan", ACCEPT.getValue("HTML"), basic(card, pin))
    }

    @Then("the loan offers {}")
    fun offers(acts: String) = assertEquals(acts.split(", "), offered(), body())

    @Then("renewing is absent, because {string}")
    fun renewingAbsent(why: String) = assertEquals(why, whyAbsent("renew"), body())

    @Then("loan {long} offers nothing as HAL Schema Forms, and renewing is absent, because {string}")
    fun offersNothing(loan: Long, why: String) {
        reads(loan, HSF)
        assertEquals(emptyList(), offered(), body())
        renewingAbsent(why)
    }

    @When("Ana renews loan {long}")
    fun renews(loan: Long) = acts(loan, "renew")

    @When("Ana returns loan {long}")
    fun returns(loan: Long) = acts(loan, "return")

    @When("Ana renews loan {long} anyway, with the version she read")
    fun renewsAnyway(loan: Long) {
        val read = send(Method.GET, "/loans/$loan", ACCEPT.getValue(HSF))
        answer = send(Method.POST, "/loans/$loan/actions/renew", ACCEPT.getValue(HSF), ifMatch = read.header("ETag"))
    }

    @Then("the answer is {int}")
    fun answerIs(status: Int) = assertEquals(status, checkNotNull(answer).status.code, body())

    @Then("loan {long} is due {string}, renewed {int} time(s)")
    fun isDue(loan: Long, due: String, renewals: Int) {
        val now = checkNotNull(library.loan(loan))
        assertEquals(due to renewals, now.due.toString() to now.renewals)
    }

    @When("Ana searches the loans for {string}")
    fun searches(text: String) {
        dialect = HSF
        answer = send(Method.GET, "/loans" + if (text.isEmpty()) "" else "?name=$text", ACCEPT.getValue(HSF))
    }

    @Then("she finds {}")
    fun finds(titles: String) {
        val items = json()["_links"]?.get("item")?.map { it["title"].asText() }.orEmpty()
        assertEquals(titles.split(", "), items, body())
    }

    /** The act [relation] of loan [loan], sent where its form says, with the ETag Ana read it at. */
    private fun acts(loan: Long, relation: String) {
        val read = send(Method.GET, "/loans/$loan", ACCEPT.getValue(HSF))
        val form = mapper.readTree(read.bodyString())["_forms"]["library:$relation"]
        val method = Method.valueOf(form["method"]?.asText() ?: "POST")
        val target = form["_links"]["target"]["href"].asText()
        answer = send(method, target, ACCEPT.getValue(HSF), ifMatch = read.header("ETag"))
    }

    /** The acts the last answer offers, by relation, in the order it lists them. */
    private fun offered(): List<String> = when (dialect) {
        HSF -> json()["_forms"]?.fieldNames()?.asSequence()?.map { it.removePrefix("library:") }?.toList().orEmpty()
        HAL_FORMS -> json()["_templates"]?.fieldNames()?.asSequence()?.toList().orEmpty()
        else -> BUTTON.findAll(body()).filter { it.groupValues[2].isEmpty() }.map { it.groupValues[1] }.toList()
    }

    /** Why the last answer leaves [relation] out, as its dialect says it. */
    private fun whyAbsent(relation: String): String? = when (dialect) {
        HSF -> {
            val href = json()["_links"]["hr:unavailable"]["href"].asText()
            val explained = mapper.readTree(send(Method.GET, href, "application/json").bodyString())
            explained["unavailable"].firstOrNull { it["rel"].asText().endsWith("#$relation") }?.get("reason")?.asText()
        }
        else -> Regex("""<span class="why" id="why-library-$relation" hidden>([^<]*)""").find(body())
            ?.groupValues?.get(1)?.replace("&#39;", "'")
    }

    private fun send(
        method: Method,
        path: String,
        accept: String,
        authorization: String = basic(HeartLibrary.CARD, PIN),
        ifMatch: String? = null,
    ): Response {
        var request = Request(method, path).header("Authorization", authorization).header("Accept", accept)
        if (accept.startsWith("application/hal+json")) request = request.header("Accept-Profile", PROFILE)
        ifMatch?.let { request = request.header("If-Match", it) }
        return library.handler(request)
    }

    private fun body(): String = checkNotNull(answer).bodyString()

    private fun json(): JsonNode = mapper.readTree(body())

    private companion object {
        const val PIN = "1234"
        const val HSF = "HAL Schema Forms"
        const val HAL_FORMS = "HAL-FORMS"
        const val PROFILE = "<https://w3id.org/heart-rise/hal-schema-forms>"

        val ACCEPT = mapOf(
            HSF to "application/hal+json",
            HAL_FORMS to "application/prs.hal-forms+json",
            "HTML" to "text/html",
        )

        /** An act's button in the HTML: its relation, and whether it is greyed. */
        val BUTTON = Regex("""<button type="button" class="act" data-rel="library:([\w-]+)"( aria-disabled="true")?""")

        fun basic(card: String, pin: String) =
            "Basic " + Base64.getEncoder().encodeToString("$card:$pin".toByteArray())
    }
}
