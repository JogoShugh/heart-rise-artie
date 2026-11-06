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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** library.feature: Riverside Public Library, read and acted on in memory, as each dialect's client would. */
class LibrarySteps {
    private val library = LibraryService()
    private val mapper = ObjectMapper()
    private var answer: Response? = null
    private var dialect = HSF
    private var pages = mutableListOf<JsonNode>()
    private var kinds: JsonNode? = null

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

    @When("Ana looks up her account by her card number, as {}")
    fun looksUpAccount(asked: String) {
        dialect = asked
        val found = send(Method.GET, "/accounts?id=${HeartLibrary.CARD}", ACCEPT.getValue(HSF))
        val account = mapper.readTree(found.bodyString())["_links"]["item"][0]["href"].asText()
        answer = send(Method.GET, account, ACCEPT.getValue(asked))
    }

    @Then("the account links her loans: {}")
    fun linksLoans(titles: String) {
        val linked = if (dialect == "HTML") {
            ITEM.findAll(body()).map { it.groupValues[1].replace("&#39;", "'") }.toList()
        } else {
            json()["_links"]["item"].map { it["title"].asText() }
        }
        assertEquals(titles.split(", "), linked, body())
    }

    @Then("the loan offers {}")
    fun offers(acts: String) = assertEquals(acts.split(", "), offered(), body())

    @Given("the library as it opens, with its five members")
    fun opens() = library.reset()

    @When("an agent searches the accounts for {string}")
    fun searchesAccounts(name: String) {
        dialect = HSF
        answer = send(Method.GET, "/accounts?name=${URLEncoder.encode(name, UTF_8)}", ACCEPT.getValue(HSF), AGENT)
    }

    @Then("it finds exactly one account, {string}")
    fun findsOne(name: String) {
        val found = items(json())
        assertEquals(listOf(name), found.map { it["title"].asText() }, body())
        answer = send(Method.GET, found.single()["href"].asText(), ACCEPT.getValue(HSF), AGENT)
    }

    @Then("that account links {}")
    fun accountLinks(books: String) =
        assertEquals(books.split(", "), items(json()).map { it["title"].asText() }, body())

    @When("an agent reads loan {long}")
    fun agentReads(loan: Long) {
        dialect = HSF
        answer = send(Method.GET, "/loans/$loan", ACCEPT.getValue(HSF), AGENT)
    }

    @Then("reporting a problem takes its {string} from the list at {string}")
    fun problemFrom(property: String, href: String) {
        val options = reportForm()["schema"]["properties"][property]["hr:options"]
        assertEquals(href, options["link"]["href"].asText(), options.toString())
        kinds = options
    }

    @Then("that list holds these kinds")
    fun listHolds(table: DataTable) {
        val options = checkNotNull(kinds)
        val href = options["link"]["href"].asText()
        val listed = mapper.readTree(send(Method.GET, href, "application/json").bodyString())
        val (value, prompt) = options["valueField"].asText() to options["promptField"].asText()
        val read = listed.at(options["items"].asText()).map { it[value].asText() to it[prompt].asText() }
        assertEquals(table.asMaps().map { it.getValue("value") to it.getValue("prompt") }, read, listed.toString())
    }

    @Then("reporting a problem asks for {string} from these choices")
    fun asksFromChoices(property: String, table: DataTable) {
        val select = checkNotNull(Regex("""<select[^>]*name="$property"[^>]*>(.*?)</select>""").find(body())) { body() }
        val choices = OPTION.findAll(select.groupValues[1]).map { it.groupValues[1] to unescaped(it.groupValues[2]) }
        assertEquals(table.asMaps().map { it.getValue("value") to it.getValue("prompt") }, choices.toList())
    }

    @Then("it asks for a {string} in a text box of at most {int} characters")
    fun asksText(property: String, most: Int) =
        assertTrue(Regex("""<textarea[^>]*name="$property"[^>]*maxlength="$most"""").containsMatchIn(body()), body())

    @When("Ana reports {string} with loan {long}, with a comment of {int} characters")
    fun reports(problem: String, loan: Long, length: Int) {
        val read = send(Method.GET, "/loans/$loan", ACCEPT.getValue(HSF))
        val form = mapper.readTree(read.bodyString())["_forms"]["library:report-problem"]
        val sent = mapOf("problem" to problem) + if (length > 0) mapOf("comment" to "a".repeat(length)) else emptyMap()
        val target = form["_links"]["target"]["href"].asText()
        answer = library.handler(
            request(Method.POST, target, ACCEPT.getValue(HSF), basic(HeartLibrary.CARD, PIN))
                .header("If-Match", read.header("ETag")).header("Content-Type", "application/json")
                .body(mapper.writeValueAsString(sent)),
        )
    }

    @Then("loan {long} shows the problem reported as {word}")
    fun reported(loan: Long, problem: String) {
        val read = mapper.readTree(send(Method.GET, "/loans/$loan", "application/json").bodyString())
        assertEquals(problem, read["reported"]?.get("problem")?.asText() ?: "none", read.toString())
    }

    @When("an agent searches the members by company for {string}")
    fun searchesMembers(company: String) {
        pages = mutableListOf()
        var page: String? = "/members?company=${URLEncoder.encode(company, UTF_8)}"
        while (page != null) {
            val answered = mapper.readTree(send(Method.GET, page, ACCEPT.getValue(HSF), AGENT).bodyString())
            pages.add(answered)
            page = answered["_links"]?.get("next")?.get("href")?.asText()
        }
    }

    @Then("the search lists {int} members, {int} to a page, over {int} pages")
    fun listsMembers(members: Int, perPage: Int, count: Int) {
        assertEquals(count, pages.size)
        assertEquals(members, pages.sumOf { items(it).size })
        assertTrue(pages.all { items(it).size <= perPage }, "a page lists more than $perPage")
    }

    @Then("every member it lists works at {string}")
    fun worksAt(company: String) = pages.flatMap(::items).forEach { item ->
        val member = mapper.readTree(send(Method.GET, item["href"].asText(), "application/json", AGENT).bodyString())
        assertEquals(company, member["company"]?.asText(), member.toString())
    }

    @When("an OpenAPI client reads loan {long} as plain JSON")
    fun readsPlain(loan: Long) {
        answer = send(Method.GET, "/loans/$loan", "application/json")
    }

    @Then("the answer's body has no controls")
    fun noControls() =
        assertEquals(emptyList(), json().fieldNames().asSequence().filter { it.startsWith("_") }.toList())

    @Then("its affordance link names a set offering {}")
    fun linkedSet(acts: String) {
        val link = checkNotNull(answer).header("Link").orEmpty()
        val href = checkNotNull(FORMS_LINK.find(link)) { "no affordance link in \"$link\"" }.groupValues[1]
        val set = mapper.readTree(send(Method.GET, href, "application/hal+json").bodyString())
        val offered = set["_forms"].fieldNames().asSequence().map { it.removePrefix("library:") }.toList()
        assertEquals(acts.split(", "), offered)
    }

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
    ): Response = library.handler(
        request(method, path, accept, authorization).let { r -> ifMatch?.let { r.header("If-Match", it) } ?: r },
    )

    private fun request(method: Method, path: String, accept: String, authorization: String): Request {
        val request = Request(method, path).header("Authorization", authorization).header("Accept", accept)
        return if (accept.startsWith("application/hal+json")) request.header("Accept-Profile", PROFILE) else request
    }

    /** The form for reporting a problem, from the last answer. */
    private fun reportForm(): JsonNode =
        checkNotNull(json()["_forms"]?.get("library:report-problem")) { "no form for reporting a problem: ${body()}" }

    private fun unescaped(text: String) = text.replace("&#39;", "'")

    private fun body(): String = checkNotNull(answer).bodyString()

    private fun json(): JsonNode = mapper.readTree(body())

    /** The links a HAL answer gives under `item`, one or many. */
    private fun items(node: JsonNode): List<JsonNode> =
        node["_links"]?.get("item")?.let { if (it.isArray) it.toList() else listOf(it) }.orEmpty()

    private companion object {
        const val PIN = "1234"
        const val HSF = "HAL Schema Forms"
        const val HAL_FORMS = "HAL-FORMS"
        const val PROFILE = "<https://w3id.org/heart-rise/hal-schema-forms>"

        /** An agent's credential: the library's Bearer token, not a member's card. */
        const val AGENT = "Bearer ${HeartLibrary.TOKEN}"

        /** The affordance link of a plain JSON answer (SPEC §6.1): the set its state offers. */
        val FORMS_LINK = Regex("<([^>]+)>;\\s*rel=\"https://w3id.org/heart-rise/rels/forms\"")

        val ACCEPT = mapOf(
            HSF to "application/hal+json",
            HAL_FORMS to "application/prs.hal-forms+json",
            "HTML" to "text/html",
        )

        /** An item link in the HTML, by its text. */
        val ITEM = Regex("""<a rel="item" href="[^"]*">([^<]*)""")

        /** One choice of a select in the HTML: its value, and its prompt. */
        val OPTION = Regex("""<option value="([^"]*)"[^>]*>([^<]*)</option>""")

        /** An act's button in the HTML: its relation, and whether it is greyed. */
        val BUTTON = Regex("""<button type="button" class="act" data-rel="library:([\w-]+)"( aria-disabled="true")?""")

        fun basic(card: String, pin: String) =
            "Basic " + Base64.getEncoder().encodeToString("$card:$pin".toByteArray())
    }
}
