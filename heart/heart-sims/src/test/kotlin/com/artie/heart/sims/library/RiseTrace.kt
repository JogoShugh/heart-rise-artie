package com.artie.heart.sims.library

import com.artie.heart.ContentAddress
import com.artie.heart.Signatures
import com.artie.heart.Signer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Uri
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Writes the RISE library's exchanges as run traces for the run report (heart-run-report), one per rung: L0, where
 * each answer carries its forms and their schemas, and L2, where it links its shared affordance set and each form
 * links its schema by `$ref`. It writes only when RISE_TRACE_OUT names a folder, so an ordinary test run writes
 * nothing. With RISE_TRACE_LIVE set, each run goes over HTTP to the simulator serving both parties (`MainKt rise`),
 * so every address in the trace answers while the simulator runs; otherwise to both, in memory.
 */
class RiseTrace {
    @Test
    fun writesTheLibraryTraces() {
        val out = System.getenv("RISE_TRACE_OUT")?.let(::File) ?: return
        out.mkdirs()
        val live = System.getenv("RISE_TRACE_LIVE") != null
        mapOf("l0" to null, "l2" to "forms=link, schema=link").forEach { (name, prefer) ->
            File(out, "$name.yaml").writeText(RiseRun(prefer, live).trace())
        }
    }
}

/**
 * One RISE run: Ana's requests to the library, and the library's answers on her replies, each request and answer
 * as they went, in order, with every address absolute. Every address after the entry is one an earlier answer
 * handed over. [prefer] is the rung the parties ask for (RFC 7240 Prefer); none is L0.
 */
private class RiseRun(private val prefer: String?, private val live: Boolean) {
    private val mapper = ObjectMapper()
    private val yaml = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
    private val steps = mutableListOf<Map<String, Any?>>()
    private val library: LibraryService by lazy {
        LibraryService(keysAt = { RiseParties.keysAt(caller.handler, it) })
    }
    private val caller: CallerService by lazy {
        CallerService(watchKeys = { RiseParties.watchKeys(library.handler, it) })
    }
    private val client = JavaHttpClient()

    /** The last ETag each address answered with: a party acts on it rather than reading again. */
    private val etags = mutableMapOf<String, String>()

    /** The forms each party has found, by the address they act on and their relation. */
    private val forms = mutableMapOf<Pair<String, String>, JsonNode>()

    /** The last representation each address answered with, read for its links. */
    private val held = mutableMapOf<String, JsonNode>()

    /** The shared sets and schemas already fetched, by address: content-addressed, each read once. */
    private val fetched = mutableMapOf<String, JsonNode>()

    /** At L2, the shared affordance set each address last linked. */
    private val sets = mutableMapOf<String, JsonNode>()

    /** One party: where it sends, and the credential it sends. */
    private class Party(val origin: String, val authorization: String)

    private val ana = Party(HeartLibrary.ORIGIN, basic(HeartLibrary.CARD, PIN))
    private val theLibrary = Party(CallerService.ORIGIN, REPLY_AUTH)

    fun trace(): String {
        setUp()
        val read = send(ana, "Ana reads the library's entry, as HAL Schema Forms", 200, get("/"))
        val entry = mapper.readTree(read.bodyString())
        answered(entry)
        failed(entry)
        cancelled(entry)
        return yaml.writeValueAsString(mapOf("steps" to steps))
    }

    /** Ana's first request: progress, refusals, the result, its replay, and a cancel after the answer. */
    private fun answered(entry: JsonNode) {
        ask(entry, "r-7f3", null, 401, "Ana asks to be told when loan {loan} can be renewed, unsigned")
        val watch = ask(entry, "r-7f3", AnaKey.signer, 201,
            "Ana asks to be told when loan {loan} can be renewed, with reply {reply}, signed with her key")
        val reply = href(readWatch(watch, "Ana reads her watch: its keys, its reply, and her cancel"), "hr:reply-to")
        read(theLibrary, reply, "The library reads Ana's reply {reply}, linked from the watch, for its forms", "r-7f3")
        answer(reply, "progress", watch, mapOf("state" to "awaiting", "percent" to 0,
            "note" to "one ahead in the hold queue"), LibraryKey.signer, 200,
            "The library reports progress of {percent} percent, naming the watch it answers for")
        answer(reply, "progress", "${HeartLibrary.ORIGIN}/watches/99", mapOf("state" to "awaiting", "percent" to 50),
            LibraryKey.signer, 409, "An answer that names another watch, {watch}, is sent")
        answer(reply, "completed", watch, RESULT_2026_12_03, LibraryKey.otherSigner, 401,
            "A result signed with another key, claiming the library's keyid, is sent")
        answer(reply, "completed", watch, RESULT_2026_12_03, null, 401, "An unsigned result is sent")
        control("/_sim/library/holds/3310/release") { library.releaseHold(LOAN) }
        answer(reply, "completed", watch, RESULT_2026_12_03, LibraryKey.signer, 200,
            "The hold is returned, and the library sends the result, signed")
        answered(watch)
        answer(reply, "completed", watch, RESULT_2026_12_03, LibraryKey.signer, 200,
            "The same result is sent again, and is answered as the first was")
        answer(reply, "completed", watch, RESULT_2026_12_10, LibraryKey.signer, 409,
            "A different result is sent, after the answer")
        val after = readWatch(watch, "Ana reads her watch after the answer: no cancel, and why")
        val why = after["_links"]?.get("hr:unavailable")?.let { href(after, "hr:unavailable") }
            ?: href(sets.getValue(watch), "hr:unavailable")
        send(ana, "Ana reads why the watch offers no cancel", 200, get(why))
        cancel(watch, after, 409, "Ana cancels anyway, by the form she read before")
        send(theLibrary, "The library reads the reply's history, by the link its last answer gave", 200,
            get(href(held.getValue(reply), "version-history")), mapOf("relation" to "version-history"))
    }

    /** Ana's second request: the library has no record of the hold, and says so as a failure. */
    private fun failed(entry: JsonNode) {
        val watch = ask(entry, "r-b04", AnaKey.signer, 201,
            "Ana asks again, with reply {reply}, on the entry's ETag she already holds")
        val reply = href(readWatch(watch, "Ana reads her second watch"), "hr:reply-to")
        read(theLibrary, reply, "The library reads Ana's reply {reply} for its forms", "r-b04")
        val problem = mapOf("state" to "failed", "type" to NO_HOLD_RECORD, "title" to "No hold record for loan 3310")
        answer(reply, "failed", watch, problem, LibraryKey.signer, 415,
            "The failure is sent as a merge patch, not the problem document its form asks for",
            contentType = "application/merge-patch+json")
        answer(reply, "failed", watch, problem, LibraryKey.signer, 200,
            "The library has no record of the hold, and sends the failure as a problem document")
        answered(watch)
    }

    /** Ana's third request, cancelled while it awaits; and her reply read in each of its formats. */
    private fun cancelled(entry: JsonNode) {
        val watch = ask(entry, "r-c21", AnaKey.signer, 201, "Ana asks a third time, with reply {reply}")
        val read = readWatch(watch, "Ana reads her third watch, which offers {relation}", "hr:cancel")
        cancel(watch, read, 200, "Ana cancels the request, by the watch's form")
        val reply = href(read, "hr:reply-to")
        send(theLibrary, "Ana's reply {reply}, read as HTML", 200, get(reply, accept = HTML), mapOf("reply" to "r-c21"))
        send(theLibrary, "Ana's reply {reply}, read as HAL-FORMS", 200, get(reply, accept = HAL_FORMS, profile = null),
            mapOf("reply" to "r-c21"))
        send(theLibrary, "Ana's reply {reply}, asked for as XML, which it is not served in", 406,
            get(reply, accept = "application/xml", profile = null), mapOf("reply" to "r-c21"))
    }

    /** Ana asks the entry's form for news of loan 3310 on [reply], signed by [signer]; the watch's address, if made. */
    private fun ask(entry: JsonNode, reply: String, signer: Signer?, expect: Int, say: String): String {
        val form = entry["_forms"]["library:tell-me-when-renewable"]
        val body = json(
            mapOf(
                "loan" to LOAN,
                "_links" to mapOf(
                    "hr:reply-to" to mapOf("href" to "${CallerService.ORIGIN}/replies/$reply", "type" to HAL,
                        "profile" to HSF_PROFILE),
                    "hr:keys" to mapOf(
                        "href" to "${CallerService.ORIGIN}${Signatures.KEYS}",
                        "type" to Signatures.JWK_SET,
                    ),
                ),
            ),
        )
        val request = signed(
            hal(Request(Method.valueOf(form["method"].asText()), target(form, entry)))
                .header("If-Match", etags.getValue("/"))
                .header("Content-Type", form["contentType"].asText()),
            HeartLibrary.ORIGIN, body, signer,
        )
        val answer = send(ana, say, expect, request, mapOf("loan" to LOAN, "reply" to reply))
        val location = answer.header("Location") ?: return ""
        // Ana keeps the watch her request created: her service checks every answer on her reply by it.
        return (HeartLibrary.ORIGIN + location).also { watch ->
            control("/_sim/replies/$reply/asked", watch) { caller.asked(reply, watch) }
        }
    }

    private fun readWatch(watch: String, say: String, relation: String? = null): JsonNode =
        read(ana, watch, say, params = listOfNotNull(relation?.let { "relation" to it }).toMap())

    /** [party] reads [address], and holds what it hands over. */
    private fun read(
        party: Party,
        address: String,
        say: String,
        reply: String? = null,
        params: Map<String, Any?> = listOfNotNull(reply?.let { "reply" to it }).toMap(),
    ): JsonNode {
        hold(party, address, send(party, say, 200, get(address), params))
        return held.getValue(address)
    }

    /** Ana sends hr:cancel on [watch], by its form in [read], or as she last found it. */
    private fun cancel(watch: String, read: JsonNode, expect: Int, say: String) {
        val form = forms.getValue(watch to "cancel")
        val request = hal(Request(Method.valueOf(form["method"].asText()), target(form, read)))
            .header("If-Match", etags.getValue(watch))
            .header("Content-Type", form["contentType"].asText())
            .body(json(mapOf("standing" to "cancelled")))
        send(ana, say, expect, request, mapOf("relation" to "hr:cancel"))
            .also { if (it.status.successful) hold(ana, watch, it) }
    }

    /**
     * The library sends [fields] on [reply]'s [relation] form, naming [watch] as what it answers, by the form's
     * method and type (or [contentType], to show a refusal), on the ETag its last answer gave, signed by [signer].
     */
    @Suppress("LongParameterList")
    private fun answer(
        reply: String,
        relation: String,
        watch: String,
        fields: Map<String, Any?>,
        signer: Signer?,
        expect: Int,
        say: String,
        contentType: String? = null,
    ) {
        val form = forms.getValue(reply to relation)
        val body = json(fields + ("_links" to mapOf("hr:in-reply-to" to mapOf("href" to watch, "type" to HAL))))
        val request = signed(
            hal(Request(Method.valueOf(form["method"].asText()), target(form, held.getValue(reply))))
                .header("Authorization", REPLY_AUTH)
                .header("If-Match", etags.getValue(reply))
                .header("Content-Type", contentType ?: form["contentType"].asText()),
            CallerService.ORIGIN, body, signer,
        )
        val params = mapOf("relation" to "hr:$relation", "watch" to watch) + fields.filterKeys { it == "percent" }
        send(theLibrary, say, expect, request, params).also { if (it.status.successful) hold(theLibrary, reply, it) }
    }

    /**
     * What [answer] at [address] hands [party]: its ETag to act on next, its links, and its forms by relation. At
     * L0 the forms are in the answer, their schemas in them. At L2 the answer links its shared set (`hr:forms`),
     * and each form there links its schema by `$ref`: each read once, and only if its bytes match the integrity
     * of the link that led to it.
     */
    private fun hold(party: Party, address: String, answer: Response) {
        answer.header("ETag")?.let { etags[address] = it }
        val read = mapper.readTree(answer.bodyString())
        held[address] = read
        val found = read["_forms"] ?: read["_links"]?.get("hr:forms")?.let { link ->
            val say = "{party} follows {relation} to the shared affordance set it names"
            // At L2 the set holds the forms, and the link to why the others are absent.
            pinned(party, link, say, "hr:forms").also { sets[address] = it }["_forms"]
        }
        found?.properties()?.forEach { (key, form) ->
            form["schema"]?.takeIf { it.has("\$ref") }?.let { schema ->
                pinned(party, schema, "{party} reads the schema {relation} links by \$ref", key, schema["\$ref"])
            }
            forms[address to key.substringAfter(':')] = form
        }
    }

    /** What [link] names, read once and checked against the integrity it carries. */
    private fun pinned(
        party: Party,
        link: JsonNode,
        say: String,
        relation: String,
        at: JsonNode = link["href"],
    ): JsonNode = fetched.getOrPut(at.asText()) {
        val answer = send(party, say, 200, get(at.asText()), mapOf("party" to party.name, "relation" to relation))
        val integrity = (link["integrity"] ?: link["hr:integrity"]).asText()
        check(ContentAddress.integrity(answer.bodyString()) == integrity) {
            "${at.asText()} does not match $integrity"
        }
        mapper.readTree(answer.bodyString())
    }

    private val Party.name: String get() = if (this === ana) "Ana" else "The library"

    /** [request] signed (RFC 9421) by [signer] for [origin], with its Content-Digest; unsigned when there is none. */
    private fun signed(request: Request, origin: String, body: String, signer: Signer?): Request {
        val ifMatch = checkNotNull(request.header("If-Match")) { "no If-Match to sign" }
        val signed = signer?.sign(request.method.name, origin + request.uri.path, body, ifMatch, SIGNED_AT)
            ?: return request.body(body)
        return request.header("Content-Digest", signed.contentDigest)
            .header("Signature-Input", "sig1=${signed.params}")
            .header("Signature", "sig1=:${signed.signature}:")
            .body(body)
    }

    /** One exchange by [party], timed and recorded in order, its address absolute: [say] is what it does. */
    private fun send(
        party: Party,
        say: String,
        expect: Int,
        request: Request,
        params: Map<String, Any?> = emptyMap(),
    ): Response {
        val sent = if (request.header("Authorization") == null) {
            request.header("Authorization", party.authorization)
        } else {
            request
        }
        val at = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val started = System.nanoTime()
        val response = handler(party)(sent)
        val ms = (System.nanoTime() - started) / NANOS_PER_MILLI
        check(response.status.code == expect) {
            "$say: expected $expect, got ${response.status.code}: ${response.bodyString()}"
        }
        steps += linkedMapOf(
            "at" to at,
            "mode" to "script",
            "say" to say,
            "params" to params,
            "request" to requestOf(sent, party.origin),
            "response" to responseOf(response),
            "expect" to expect,
            "ms" to ms,
        ) + listOfNotNull(
            // The run's one known address, its bookmark: the report judges every address after it.
            if (steps.isEmpty()) "entry" to true else null,
        )
        // The entry's forms are held after the entry is recorded: at L2, what they link is read after it.
        if (request.uri.path == "/" && response.status.successful) hold(party, "/", response)
        return response
    }

    /** Where [party]'s requests go: the simulator's process, at the party's origin, or the service in memory. */
    private fun handler(party: Party): HttpHandler = when {
        live -> { request -> client(request.uri(Uri.of(party.origin + request.uri))) }
        party === ana -> library.handler
        else -> caller.handler
    }

    /** What the parties do between their requests, out of band: in memory, or by the simulator's control route. */
    private fun control(route: String, body: String = "", inMemory: () -> Unit) {
        if (live) {
            check(client(Request(Method.POST, HeartLibrary.ORIGIN + route).body(body)).status.successful) { route }
        } else {
            inMemory()
        }
    }

    private fun setUp() = control("/_sim/reset") {}.also {
        control("/_sim/rise") {
            library.lend(listOf(RiseParties.HOBBIT))
            RiseParties.REPLIES.forEach(caller::open)
        }
    }

    /** The library has sent its one answer on [watch]: it can't be cancelled after. */
    private fun answered(watch: String) {
        val id = watch.substringAfterLast('/')
        control("/_sim/library/watches/$id/answered") { library.answered(id.toLong()) }
    }

    private fun requestOf(request: Request, origin: String): Map<String, Any?> = linkedMapOf(
        "method" to request.method.name,
        "url" to origin + request.uri.toString(),
        "headers" to headersOf(request),
        "body" to bodyOf(request.bodyString(), request.header("Content-Type")),
    )

    private fun responseOf(response: Response): Map<String, Any?> = linkedMapOf(
        "status" to response.status.code,
        "headers" to SHOWN_ON_ANSWER.mapNotNull { name -> response.header(name)?.let { name to it } }.toMap(),
        "body" to bodyOf(response.bodyString(), response.header("Content-Type")),
    )

    /** The headers a request shows, its credential redacted to its scheme: a trace never holds a secret. */
    private fun headersOf(request: Request): Map<String, String> =
        SHOWN_ON_REQUEST.mapNotNull { name ->
            request.header(name)?.let { value ->
                name to if (name == "Authorization") value.substringBefore(' ') + " <token>" else value
            }
        }.toMap()

    /** A JSON body as its data, any other as its text; nothing when there is none. */
    private fun bodyOf(text: String, contentType: String?): Any? = when {
        text.isEmpty() -> null
        contentType?.contains("json") == true -> mapper.readValue(text, Any::class.java)
        else -> text
    }

    /**
     * A form's target, as the path a service is sent: at L2 a URI template, each variable filled from the
     * resource [state] by its JSON Pointer in `hr:templatePointers` (HAL-SCHEMA-FORMS-HR Rule 1).
     */
    private fun target(form: JsonNode, state: JsonNode): String {
        var href = form["_links"]["target"]["href"].asText()
        form["hr:templatePointers"]?.properties()?.forEach { (name, pointer) ->
            href = href.replace("{$name}", state.at(pointer.asText()).asText())
        }
        return path(href)
    }

    /** The address [read] links as [rel]: handed over, never made up; a link that is missing stops the run. */
    private fun href(read: JsonNode, rel: String): String =
        checkNotNull(read["_links"]?.get(rel)?.get("href")?.asText()) { "no $rel link in $read" }

    private fun json(value: Any): String = mapper.writeValueAsString(value)

    /** The path of [href], which may be absolute: what a service is sent, at its own origin. */
    private fun path(href: String): String = URI(href).let { it.rawPath + (it.rawQuery?.let { q -> "?$q" } ?: "") }

    private fun get(href: String, accept: String = HAL, profile: String? = PROFILE): Request =
        preferred(Request(Method.GET, path(href)).header("Accept", accept))
            .let { r -> profile?.let { r.header("Accept-Profile", it) } ?: r }

    private fun hal(request: Request): Request =
        preferred(request.header("Accept", HAL).header("Accept-Profile", PROFILE))

    /** [request] asking for this run's rung: at L2, forms and schemas by link. */
    private fun preferred(request: Request): Request = prefer?.let { request.header("Prefer", it) } ?: request

    private companion object {
        const val PIN = "1234"
        const val LOAN = 3310L
        const val HAL = "application/hal+json"
        const val HAL_FORMS = "application/prs.hal-forms+json"
        const val HTML = "text/html"
        const val HSF_PROFILE = "https://w3id.org/heart-rise/hal-schema-forms"
        const val PROFILE = "<$HSF_PROFILE>"
        const val REPLY_AUTH = "Bearer ${CallerService.LIBRARY_TOKEN}"
        const val SIGNED_AT = 1794153600L
        const val NANOS_PER_MILLI = 1_000_000L
        const val NO_HOLD_RECORD = "https://riverside-library.example/problems/no-hold-record"

        val RESULT_2026_12_03 =
            mapOf("state" to "completed", "result" to mapOf("loan" to 3310, "renewableFrom" to "2026-12-03"))
        val RESULT_2026_12_10 =
            mapOf("state" to "completed", "result" to mapOf("loan" to 3310, "renewableFrom" to "2026-12-10"))

        val SHOWN_ON_REQUEST = listOf(
            "Authorization", "Accept", "Accept-Profile", "Prefer", "If-Match", "Content-Type",
            "Content-Digest", "Signature-Input", "Signature",
        )
        val SHOWN_ON_ANSWER = listOf(
            "Location", "ETag", "Content-Type", "Content-Profile", "Preference-Applied", "WWW-Authenticate",
            "Accept-Signature", "Accept-Patch",
        )

        fun basic(card: String, pin: String) = "Basic " + Base64.getEncoder().encodeToString("$card:$pin".toByteArray())
    }
}
