package com.artie.heart.sims.library

import com.artie.heart.Replies
import com.artie.heart.Signatures
import com.artie.heart.Signed
import com.artie.heart.Signer
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
import java.security.PublicKey
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** library.feature: Riverside Public Library, read and acted on in memory, as each dialect's client would. */
class LibrarySteps {
    private val library = LibraryService(keysAt = ::keysAt)
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

    // Ana's request to be told when a loan can be renewed (tell-me-when-renewable.feature). Phase 1: the request, its
    // watch, and the entry's form. The reply resource and the library's answers come in later phases.
    private val caller = CallerService(watchKeys = ::watchKeys)

    /** The watch the library brought into being for Ana's request, as the 201's Location named it, absolute. */
    private var anaWatch: String? = null

    @Given("Ana's reply {word} is awaiting")
    fun replyAwaiting(reply: String) = caller.open(reply)

    @Given("Ana has asked to be told when loan {long} can be renewed, with reply {word}")
    fun hasAsked(loan: Long, reply: String) {
        asksToBeTold(loan, reply, null)
        assertEquals(201, answer?.status?.code, body())
    }

    /** The keys Ana's watch at [watch] links as `hr:keys`: read from the watch, never from an answer. */
    private fun watchKeys(watch: String): Map<String, PublicKey> {
        val read = mapper.readTree(send(Method.GET, java.net.URI(watch).path, ACCEPT.getValue(HSF)).bodyString())
        val keys = library.handler(Request(Method.GET, read["_links"]["hr:keys"]["href"].asText()))
        return mapper.readTree(keys.bodyString())["keys"].associate { jwk ->
            jwk["kid"].asText() to Signatures.keyOf(mapper.convertValue(jwk, Map::class.java))
        }
    }

    @When("the library reports progress of {int} percent on reply {word}, for the watch {string}")
    fun progressForWatch(percent: Int, reply: String, watch: String) =
        libraryAnswers(reply, "progress", mapper.writeValueAsString(mapOf("state" to "awaiting", "percent" to percent)),
            watch = watch)

    @Then("reply {word} links {string} to Ana's watch")
    fun linksAnaWatch(reply: String, rel: String) {
        val read = mapper.readTree(replyBody(reply))
        assertEquals(checkNotNull(anaWatch), read["_links"][rel]?.get("href")?.asText(), read.toString())
    }

    @Then("reply {word} shows the failure's problem, {string}")
    fun showsProblem(reply: String, title: String) {
        val read = mapper.readTree(replyBody(reply))
        assertEquals(title, read["_embedded"]?.get("problem")?.get("title")?.asText(), read.toString())
    }

    /** The keys at [href], one of Ana's addresses, read as the JWK Set her service publishes there. */
    private fun keysAt(href: String): Map<String, PublicKey> {
        val read = caller.handler(Request(Method.GET, java.net.URI(href).path))
        return mapper.readTree(read.bodyString())["keys"].associate { jwk ->
            jwk["kid"].asText() to Signatures.keyOf(mapper.convertValue(jwk, Map::class.java))
        }
    }

    @Then("the library holds no watch")
    fun noWatch() = assertTrue(library.watches().isEmpty(), library.watches().toString())

    @When("""^Ana asks to be told when loan (\d+) can be renewed, with reply (\S+) and her key(?:, (.+))?$""")
    fun asksToBeTold(loan: Long, reply: String, signature: String?) {
        assertTrue(caller.has(reply), "no awaiting reply $reply")
        val read = send(Method.GET, "/", ACCEPT.getValue(HSF))
        val entry = mapper.readTree(read.bodyString())
        val form = checkNotNull(entry["_forms"]?.get("library:tell-me-when-renewable")) { "no entry form: $entry" }
        val sent = mapOf(
            "loan" to loan,
            "_links" to mapOf(
                "hr:reply-to" to mapOf(
                    "href" to "https://caller.example/replies/$reply",
                    "type" to "application/hal+json",
                    "profile" to "https://w3id.org/heart-rise/hal-schema-forms",
                ),
                "hr:keys" to mapOf("href" to "https://caller.example/keys", "type" to "application/jwk-set+json"),
            ),
        )
        val target = form["_links"]["target"]["href"].asText()
        val method = Method.valueOf(form["method"].asText())
        val body = mapper.writeValueAsString(sent)
        val ifMatch = checkNotNull(read.header("ETag")) { "no ETag: ${read.bodyString()}" }
        val ask = request(method, target, ACCEPT.getValue(HSF), basic(HeartLibrary.CARD, PIN))
            .header("If-Match", ifMatch)
            .header("Content-Type", form["contentType"].asText())
        // Ana signs her request with the key her envelope's hr:keys names, unless the scenario says otherwise.
        val signer = when (signature) {
            null -> AnaKey.signer
            "signed with another key" -> AnaKey.otherSigner
            else -> null
        }
        val signed = signer?.sign(method.name, HeartLibrary.ORIGIN + target, body, ifMatch, SIGNED_AT)
        answer = library.handler(if (signed == null) ask.body(body) else signedRequest(ask, signed, body))
        // Ana keeps the watch her request created: she checks every answer on her reply by it.
        answer?.takeIf { it.status.code == 201 }?.header("Location")?.let { location ->
            anaWatch = HeartLibrary.ORIGIN + location
            caller.asked(reply, HeartLibrary.ORIGIN + location)
        }
    }

    @Then("the library's entry and the watch both link {string} to the library's key set")
    fun linksKeys(rel: String) {
        val location = checkNotNull(answer?.header("Location")) { "no Location: ${body()}" }
        val entry = mapper.readTree(send(Method.GET, "/", ACCEPT.getValue(HSF)).bodyString())
        val watch = mapper.readTree(send(Method.GET, location, ACCEPT.getValue(HSF)).bodyString())
        listOf(entry, watch).forEach { read ->
            assertEquals(Signatures.KEYS, read["_links"][rel]?.get("href")?.asText(), read.toString())
        }
    }

    @Then("the library's key set holds the key {string}, which verifies the library's signatures")
    fun keySetHolds(keyId: String) {
        // Read with no credential at all: whoever checks the library's signatures reads its keys.
        val read = library.handler(Request(Method.GET, Signatures.KEYS))
        assertEquals(Signatures.JWK_SET, read.header("Content-Type"), read.bodyString())
        val jwk = mapper.readTree(read.bodyString())["keys"].single { it["kid"].asText() == keyId }
        val signed = LibraryKey.signer.sign("PATCH", "https://caller.example/replies/r-7f3", "{}", "\"v1\"", SIGNED_AT)
        assertTrue(Signatures.verify(signed, "{}", Signatures.keyOf(mapper.convertValue(jwk, Map::class.java))))
    }

    @Then("the library answers {int} with a watch that is awaiting")
    fun answersWatch(status: Int) {
        assertEquals(status, answer?.status?.code, body())
        val location = checkNotNull(answer?.header("Location")) { "no Location: ${body()}" }
        val watch = mapper.readTree(send(Method.GET, location, ACCEPT.getValue(HSF)).bodyString())
        assertEquals("awaiting", watch["standing"].asText(), watch.toString())
    }

    /** The library's own Bearer token at Ana's replies, and what it sends a reply. */
    @When("the library reports progress of {int} percent on reply {word}, with the note {string}")
    fun libraryReportsProgress(percent: Int, reply: String, note: String) {
        val sent = mapper.writeValueAsString(mapOf("state" to "awaiting", "percent" to percent, "note" to note))
        libraryAnswers(reply, "progress", sent)
    }

    @When("the library reads Ana's reply {word}")
    fun libraryReads(reply: String) {
        answer = caller.handler(request(Method.GET, "/replies/$reply", ACCEPT.getValue(HSF), REPLY_AUTH))
    }

    @Then("each of the reply's forms targets the reply's own address")
    fun formsTargetReply(table: DataTable) {
        val read = mapper.readTree(body())
        val self = read["_links"]["self"]["href"].asText()
        table.asMaps().forEach { row ->
            val form = checkNotNull(read["_forms"]?.get(row.getValue("form"))) { "no ${row["form"]}: $read" }
            assertEquals(self, form["_links"]["target"]["href"].asText(), row.toString())
            assertEquals(row.getValue("method"), form["method"].asText(), row.toString())
            assertEquals(row.getValue("content type"), form["contentType"].asText(), row.toString())
        }
    }

    @Then("the reply accepts patches as {string}")
    fun acceptsPatches(types: String) = assertEquals(types, answer?.header("Accept-Patch"), body())

    @When("the library sends progress of {int} percent to {string}")
    fun progressTo(percent: Int, path: String) {
        val reply = path.substringBefore("/actions")
        val read = caller.handler(request(Method.GET, reply, ACCEPT.getValue(HSF), REPLY_AUTH))
        val ifMatch = checkNotNull(read.header("ETag")) { "no ETag: ${read.bodyString()}" }
        val sent = mapper.writeValueAsString(mapOf("percent" to percent))
        val signed = LibraryKey.signer.sign("PUT", "https://caller.example$path", sent, ifMatch, SIGNED_AT)
        val put = request(Method.PUT, path, ACCEPT.getValue(HSF), REPLY_AUTH).header("If-Match", ifMatch)
        answer = caller.handler(signedRequest(put.header("Content-Type", "application/json"), signed, sent))
    }

    @Then("reply {word} is awaiting at {int} percent")
    fun replyAwaitingAt(reply: String, percent: Int) {
        val now = mapper.readTree(replyBody(reply))
        assertEquals("awaiting", now["state"].asText(), now.toString())
        assertEquals(percent, now["percent"].asInt(), now.toString())
    }

    @Then("reply {word} offers progress, completed and failed")
    fun replyOffers(reply: String) {
        val forms = mapper.readTree(replyBody(reply))["_forms"]
        assertTrue(listOf("hr:progress", "hr:completed", "hr:failed").all { forms.has(it) }, forms.toString())
    }

    private fun replyBody(reply: String): String =
        caller.handler(request(Method.GET, "/replies/$reply", ACCEPT.getValue(HSF), REPLY_AUTH)).bodyString()

    /** What the library read of a form on Ana's reply: where it sends, by which method, as which type. */
    private data class ReplyForm(val target: String, val method: Method, val contentType: String)

    // What the library knows across its answers to one reply: its forms, and the last result it sent.
    private val replyForms = mutableMapOf<String, ReplyForm>()
    private var lastResult: Map<String, Any?>? = null
    private var lastSigned: Signed? = null
    private var lastSent: String? = null
    private var lastContentType: String? = null
    private var replyBeforeRepeat: String? = null
    private var noHoldRecorded: Long? = null

    /**
     * The library reads the reply, then sends [sent] on its [relation] form, by the form's method, to its target, as
     * its content type, signed by [signer] (RFC 9421), or unsigned when it has none.
     */
    private fun libraryAnswers(
        reply: String,
        relation: String,
        fields: String,
        signer: Signer? = LibraryKey.signer,
        watch: String? = null,
    ) {
        // Each answer names the watch it answers for: the library's own, for the request that named this reply.
        val inReplyTo = watch ?: libraryWatch(reply)
        val sent = mapper.writeValueAsString(
            mapper.readValue(fields, Map::class.java) +
                ("_links" to mapOf(Replies.IN_REPLY_TO to mapOf("href" to inReplyTo, "type" to HAL))),
        )
        val read = caller.handler(request(Method.GET, "/replies/$reply", ACCEPT.getValue(HSF), REPLY_AUTH))
        val ifMatch = checkNotNull(read.header("ETag")) { "no ETag: ${read.bodyString()}" }
        // A repeat goes to the form the first answer found: an answered reply offers no form for it any more.
        val form = replyForms.getOrPut(relation) {
            val found = mapper.readTree(read.bodyString())["_forms"]["hr:$relation"]
            ReplyForm(
                java.net.URI(found["_links"]["target"]["href"].asText()).path,
                Method.valueOf(found["method"].asText()),
                found["contentType"].asText(),
            )
        }
        val signed = signer?.sign(form.method.name, "https://caller.example${form.target}", sent, ifMatch, SIGNED_AT)
        lastSigned = signed
        lastSent = sent
        lastContentType = form.contentType
        val act = request(form.method, form.target, ACCEPT.getValue(HSF), REPLY_AUTH)
            .header("If-Match", ifMatch)
            .header("Content-Type", form.contentType)
        answer = caller.handler(if (signed == null) act.body(sent) else signedRequest(act, signed, sent))
    }

    /** The library's watch for the request that named reply [reply], as its absolute address. */
    private fun libraryWatch(reply: String): String {
        val watch = library.watches().single { it.reply.endsWith("/replies/$reply") }
        return "${HeartLibrary.ORIGIN}/watches/${watch.id}"
    }

    private fun signedRequest(request: Request, signed: Signed, sent: String): Request =
        request.header("Content-Digest", signed.contentDigest)
            .header("Signature-Input", "sig1=${signed.params}")
            .header("Signature", "sig1=:${signed.signature}:")
            .body(sent)

    /** The library sends [result] on the completed form. */
    private fun libraryCompletes(reply: String, result: Map<String, Any?>) {
        lastResult = result
        libraryAnswers(reply, "completed", mapper.writeValueAsString(mapOf("state" to "completed", "result" to result)))
    }

    @Given("the library has no record of the hold on loan {long}")
    fun noHoldRecord(loan: Long) {
        // The library's record of the hold is gone; the failure below says so.
        noHoldRecorded = loan
    }

    @When("the library sends the failure on reply {word}: {string}")
    fun libraryFails(reply: String, title: String) {
        checkNotNull(noHoldRecorded) { "the library still has its record of the hold" }
        val problem = mapOf("state" to "failed", "type" to NO_HOLD_RECORD, "title" to title)
        libraryAnswers(reply, "failed", mapper.writeValueAsString(problem))
    }

    @Then("reply {word} is answered as failed")
    fun answeredFailed(reply: String) {
        val now = mapper.readTree(replyBody(reply))
        assertEquals("failed", now["state"].asText(), now.toString())
    }

    @Then("the failure is a problem document, {word}")
    fun failureIsProblem(mediaType: String) = assertEquals(mediaType, lastContentType)

    @Given("the hold on loan {long} has been returned")
    fun holdReturned(loan: Long) = library.releaseHold(loan)

    @When("the library sends the result on reply {word}: loan {long} can be renewed from {word}")
    fun libraryAnswers(reply: String, loan: Long, from: String) =
        libraryCompletes(reply, mapOf("loan" to loan, "renewableFrom" to from))

    @Then("reply {word} is answered as completed")
    fun answeredCompleted(reply: String) {
        val now = mapper.readTree(replyBody(reply))
        assertEquals("completed", now["state"].asText(), now.toString())
    }

    @Then("the result is signed with the library's key")
    fun resultSigned() {
        val signed = checkNotNull(lastSigned) { "no result was sent" }
        assertTrue(Signatures.verify(signed, checkNotNull(lastSent), LibraryKey.pair.public), signed.toString())
    }

    @Then("reply {word} offers nothing more")
    fun offersNothing(reply: String) {
        val forms = mapper.readTree(replyBody(reply))["_forms"]
        assertTrue(forms == null || forms.size() == 0, forms.toString())
    }

    @Given("reply {word} is answered as completed, with loan {long} renewable from {word}")
    fun answeredWithResult(reply: String, loan: Long, from: String) =
        libraryCompletes(reply, mapOf("loan" to loan, "renewableFrom" to from))

    @When("the library sends the same result again on reply {word}")
    fun sendsSameAgain(reply: String) {
        replyBeforeRepeat = replyBody(reply)
        libraryCompletes(reply, checkNotNull(lastResult) { "no result was sent" })
    }

    @Then("the library is answered {int}")
    fun libraryAnswered(status: Int) = assertEquals(status, answer?.status?.code, body())

    @Then("reply {word} is unchanged")
    fun replyUnchanged(reply: String) = assertEquals(checkNotNull(replyBeforeRepeat), replyBody(reply))

    @When("the library sends a different result on reply {word}: loan {long} renewable from {word}")
    fun sendsDifferent(reply: String, loan: Long, from: String) =
        libraryCompletes(reply, mapOf("loan" to loan, "renewableFrom" to from))

    @Then("the library is refused with {int}")
    fun libraryRefused(status: Int) = assertEquals(status, answer?.status?.code, body())

    @Then("the library is refused with {int}, as the problem {string}")
    fun libraryRefusedAs(status: Int, type: String) {
        assertEquals(status, answer?.status?.code, body())
        assertEquals(type, mapper.readTree(body())["type"].asText(), body())
    }

    @Then("the refusal asks for a Signature, over {string} {string} {string} {string}, by the key {string}")
    fun asksForSignature(method: String, target: String, digest: String, ifMatch: String, key: String) {
        assertTrue(answer?.header("WWW-Authenticate").orEmpty().startsWith("Signature "), body())
        val components = listOf(method, target, digest, ifMatch).joinToString(" ") { "\"$it\"" }
        assertEquals("sig1=($components);keyid=\"$key\";alg=\"ed25519\"", answer?.header("Accept-Signature"))
    }

    @Then("the refusal says hr:completed is not offered")
    fun refusalSays() = assertTrue(body().contains("hr:completed is not offered"), body())

    @When(
        """^the library sends the result on reply (\S+) """ +
            """(signed with another key|unsigned): loan (\d+) can be renewed from (\S+)$""",
    )
    fun libraryAnswersUnvouched(reply: String, how: String, loan: Long, from: String) {
        val result = mapOf("loan" to loan, "renewableFrom" to from)
        val sent = mapper.writeValueAsString(mapOf("state" to "completed", "result" to result))
        val signer = if (how == "unsigned") null else LibraryKey.otherSigner
        libraryAnswers(reply, "completed", sent, signer)
    }

    @Then("reply {word} is still awaiting")
    fun stillAwaiting(reply: String) = assertEquals("awaiting", mapper.readTree(replyBody(reply))["state"].asText())

    @Then("the library's entry offers the request, with its reply and key as links")
    fun entryOffersRequest() {
        val entry = mapper.readTree(send(Method.GET, "/", ACCEPT.getValue(HSF)).bodyString())
        val form = checkNotNull(entry["_forms"]?.get("library:tell-me-when-renewable")) { "no entry form: $entry" }
        val links = form["schema"]["properties"]["_links"]["properties"]
        assertTrue(links.has("hr:reply-to") && links.has("hr:keys"), form.toString())
    }

    private companion object {
        const val PIN = "1234"
        const val HSF = "HAL Schema Forms"
        const val HAL = "application/hal+json"
        const val HAL_FORMS = "HAL-FORMS"
        const val PROFILE = "<https://w3id.org/heart-rise/hal-schema-forms>"

        /** An agent's credential: the library's Bearer token, not a member's card. */
        const val AGENT = "Bearer ${HeartLibrary.TOKEN}"

        /** The library's own problem type: it has no record of the hold a request asked about. */
        const val NO_HOLD_RECORD = "https://riverside-library.example/problems/no-hold-record"

        /** The library's credential at Ana's replies. */
        const val REPLY_AUTH = "Bearer ${CallerService.LIBRARY_TOKEN}"

        /** The instant the library's results are signed at: fixed, so a signature is the same on every run. */
        const val SIGNED_AT = 1794153600L

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
