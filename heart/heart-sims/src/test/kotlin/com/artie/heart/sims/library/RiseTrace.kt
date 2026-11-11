package com.artie.heart.sims.library

import com.artie.heart.Signatures
import com.artie.heart.Signer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.security.PublicKey
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Writes the RISE library's exchanges as a run trace for the run report (heart-run-report): Ana's requests to the
 * library, and the library's answers on her replies, each request and answer as they went over the in-memory
 * services, in order, with every address absolute. Every address after the entry is one an earlier answer handed
 * over. It writes only when RISE_TRACE_OUT names the file, so an ordinary test run writes nothing.
 */
class RiseTrace {
    private val mapper = ObjectMapper()
    private val yaml = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
    private val steps = mutableListOf<Map<String, Any?>>()
    private val library = LibraryService(keysAt = ::keysAt)
    private val caller = CallerService(watchKeys = ::watchKeys)

    /** The last ETag each address answered with: a party acts on it rather than reading again. */
    private val etags = mutableMapOf<String, String>()

    /** The forms each party has found, by the address they act on and their relation. */
    private val forms = mutableMapOf<Pair<String, String>, JsonNode>()

    /** The last representation each address answered with, read for its links. */
    private val held = mutableMapOf<String, JsonNode>()

    /** One party: whom it sends to, where that is, and the credential it sends. */
    private class Party(val handler: HttpHandler, val origin: String, val authorization: String)

    private val ana by lazy { Party(library.handler, HeartLibrary.ORIGIN, basic(HeartLibrary.CARD, PIN)) }
    private val theLibrary by lazy { Party(caller.handler, CallerService.ORIGIN, REPLY_AUTH) }

    @Test
    fun writesTheLibraryTrace() {
        val out = System.getenv("RISE_TRACE_OUT") ?: return
        library.lend(
            listOf(
                Loan(
                    id = 3310,
                    title = "The Hobbit",
                    author = "J. R. R. Tolkien",
                    due = LocalDate.parse("2026-11-12"),
                    renewals = 0,
                    holdWaiting = true,
                ),
            ),
        )
        listOf("r-7f3", "r-b04", "r-c21").forEach(caller::open)
        val entry = entry()
        answered(entry)
        failed(entry)
        cancelled(entry)
        File(out).writeText(yaml.writeValueAsString(mapOf("steps" to steps)))
    }

    /** Ana reads the library's entry, its affordances, and the schema of the request she will make. */
    private fun entry(): JsonNode {
        val read = send(ana, "Ana reads the library's entry, as HAL Schema Forms", 200, get("/"))
        val entry = mapper.readTree(read.bodyString())
        val affordances = mapper.readTree(
            send(ana, "Ana follows the entry's {relation} link", 200, get(href(entry, "hr:affordances")),
                mapOf("relation" to "hr:affordances")).bodyString(),
        )
        val form = entry["_forms"]["library:tell-me-when-renewable"]
        val schema = affordances["forms"][form["hr:integrity"].asText()]["_links"]["describedby"]["href"].asText()
        send(ana, "Ana reads the schema of {relation}, found by its integrity", 200, get(schema),
            mapOf("relation" to "library:tell-me-when-renewable"))
        return entry
    }

    /** Ana's first request: progress, refusals, the result, its replay, and a cancel after the answer. */
    private fun answered(entry: JsonNode) {
        ask(entry, "r-7f3", null, 401, "Ana asks to be told when loan {loan} can be renewed, unsigned")
        val watch = ask(entry, "r-7f3", AnaKey.signer, 201,
            "Ana asks to be told when loan {loan} can be renewed, with reply {reply}, signed with her key")
        val watchRead = readWatch(watch, "Ana reads her watch: its keys, its reply, and her cancel")
        val reply = href(watchRead, "hr:reply-to")
        send(theLibrary, "The library reads Ana's reply {reply}, linked from the watch, for its forms", 200,
            get(reply), mapOf("reply" to "r-7f3")).also { hold(reply, it) }
        answer(reply, "progress", watch, mapOf("state" to "awaiting", "percent" to 0,
            "note" to "one ahead in the hold queue"), LibraryKey.signer, 200,
            "The library reports progress of {percent} percent, naming the watch it answers for")
        answer(reply, "progress", "${HeartLibrary.ORIGIN}/watches/99", mapOf("state" to "awaiting", "percent" to 50),
            LibraryKey.signer, 409, "An answer that names another watch, {watch}, is sent")
        answer(reply, "completed", watch, RESULT_2026_12_03, LibraryKey.otherSigner, 401,
            "A result signed with another key, claiming the library's keyid, is sent")
        answer(reply, "completed", watch, RESULT_2026_12_03, null, 401, "An unsigned result is sent")
        library.releaseHold(3310)
        answer(reply, "completed", watch, RESULT_2026_12_03, LibraryKey.signer, 200,
            "The hold is returned, and the library sends the result, signed")
        library.answered(watch.substringAfterLast('/').toLong())
        answer(reply, "completed", watch, RESULT_2026_12_03, LibraryKey.signer, 200,
            "The same result is sent again, and is answered as the first was")
        answer(reply, "completed", watch, RESULT_2026_12_10, LibraryKey.signer, 409,
            "A different result is sent, after the answer")
        val after = readWatch(watch, "Ana reads her watch after the answer: no cancel, and why")
        send(ana, "Ana reads why the watch offers no cancel", 200, get(href(after, "hr:unavailable")))
        cancel(watch, after, 409, "Ana cancels anyway, by the form she read before")
        send(theLibrary, "The library reads the reply's history, by the link its last answer gave", 200,
            get(href(checkNotNull(held[reply]), "version-history")), mapOf("relation" to "version-history"))
    }

    /** Ana's second request: the library has no record of the hold, and says so as a failure. */
    private fun failed(entry: JsonNode) {
        val watch = ask(entry, "r-b04", AnaKey.signer, 201,
            "Ana asks again, with reply {reply}, on the entry's ETag she already holds")
        val reply = href(readWatch(watch, "Ana reads her second watch"), "hr:reply-to")
        send(theLibrary, "The library reads Ana's reply {reply} for its forms", 200, get(reply),
            mapOf("reply" to "r-b04")).also { hold(reply, it) }
        val problem = mapOf("state" to "failed", "type" to NO_HOLD_RECORD, "title" to "No hold record for loan 3310")
        answer(reply, "failed", watch, problem, LibraryKey.signer, 415,
            "The failure is sent as a merge patch, not the problem document its form asks for",
            contentType = "application/merge-patch+json")
        answer(reply, "failed", watch, problem, LibraryKey.signer, 200,
            "The library has no record of the hold, and sends the failure as a problem document")
        library.answered(watch.substringAfterLast('/').toLong())
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
        val target = form["_links"]["target"]["href"].asText()
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
            hal(Request(Method.valueOf(form["method"].asText()), target)).header("If-Match", etags.getValue("/"))
                .header("Content-Type", form["contentType"].asText()),
            HeartLibrary.ORIGIN, body, signer,
        )
        val answer = send(ana, say, expect, request, mapOf("loan" to LOAN, "reply" to reply))
        val location = answer.header("Location") ?: return ""
        return (HeartLibrary.ORIGIN + location).also { caller.asked(reply, it) }
    }

    private fun readWatch(watch: String, say: String, relation: String? = null): JsonNode {
        val read = send(ana, say, 200, get(watch), listOfNotNull(relation?.let { "relation" to it }).toMap())
        hold(watch, read)
        return mapper.readTree(read.bodyString())
    }

    /** Ana sends hr:cancel on [watch], by its form in [read], or as she last found it. */
    private fun cancel(watch: String, read: JsonNode, expect: Int, say: String) {
        val form = read["_forms"]?.get("hr:cancel") ?: forms.getValue(watch to "cancel")
        forms[watch to "cancel"] = form
        val request = hal(Request(Method.valueOf(form["method"].asText()), target(form)))
            .header("If-Match", etags.getValue(watch))
            .header("Content-Type", form["contentType"].asText())
            .body(json(mapOf("standing" to "cancelled")))
        send(ana, say, expect, request, mapOf("relation" to "hr:cancel"))
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
            hal(Request(Method.valueOf(form["method"].asText()), target(form)))
                .header("Authorization", REPLY_AUTH)
                .header("If-Match", etags.getValue(reply))
                .header("Content-Type", contentType ?: form["contentType"].asText()),
            CallerService.ORIGIN, body, signer,
        )
        val params = mapOf("relation" to "hr:$relation", "watch" to watch) +
            fields.filterKeys { it == "percent" }
        send(theLibrary, say, expect, request, params).also { if (it.status.successful) hold(reply, it) }
    }

    /** What [answer] at [address] hands over: its ETag to act on next, its links, and its forms by relation. */
    private fun hold(address: String, answer: Response) {
        answer.header("ETag")?.let { etags[address] = it }
        val read = mapper.readTree(answer.bodyString())
        held[address] = read
        read["_forms"]?.properties()?.forEach { (key, form) ->
            forms[address to key.substringAfter(':')] = form
        }
    }

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
        val response = party.handler(sent)
        val ms = (System.nanoTime() - started) / NANOS_PER_MILLI
        check(response.status.code == expect) {
            "$say: expected $expect, got ${response.status.code}: ${response.bodyString()}"
        }
        if (request.uri.path == "/" && response.status.successful) hold("/", response)
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
        return response
    }

    /** The keys at [href], one of Ana's addresses: how the library reads the keys her request names. */
    private fun keysAt(href: String): Map<String, PublicKey> =
        keySet(caller.handler(Request(Method.GET, path(href))))

    /** The keys Ana's watch at [watch] links: how Ana checks the library's answers, never by a link they carry. */
    private fun watchKeys(watch: String): Map<String, PublicKey> {
        val read = library.handler(hal(Request(Method.GET, path(watch))).header("Authorization", ana.authorization))
        return keySet(library.handler(Request(Method.GET, path(href(mapper.readTree(read.bodyString()), "hr:keys")))))
    }

    private fun keySet(answer: Response): Map<String, PublicKey> =
        mapper.readTree(answer.bodyString())["keys"].associate { jwk ->
            jwk["kid"].asText() to Signatures.keyOf(mapper.convertValue(jwk, Map::class.java))
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

    /** A form's target, as the path the in-memory service is sent. */
    private fun target(form: JsonNode): String = path(form["_links"]["target"]["href"].asText())

    private fun href(read: JsonNode, rel: String): String = read["_links"]?.get(rel)?.get("href")?.asText().orEmpty()

    private fun json(value: Any): String = mapper.writeValueAsString(value)

    /** The path of [href], which may be absolute: what the in-memory service is sent. */
    private fun path(href: String): String = URI(href).let { it.rawPath + (it.rawQuery?.let { q -> "?$q" } ?: "") }

    private fun get(href: String, accept: String = HAL, profile: String? = PROFILE): Request =
        Request(Method.GET, path(href)).header("Accept", accept)
            .let { r -> profile?.let { r.header("Accept-Profile", it) } ?: r }

    private fun hal(request: Request): Request = request.header("Accept", HAL).header("Accept-Profile", PROFILE)

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
            "Authorization", "Accept", "Accept-Profile", "If-Match", "Content-Type",
            "Content-Digest", "Signature-Input", "Signature",
        )
        val SHOWN_ON_ANSWER = listOf(
            "Location", "ETag", "Content-Type", "Content-Profile", "WWW-Authenticate", "Accept-Signature",
            "Accept-Patch",
        )

        fun basic(card: String, pin: String) = "Basic " + Base64.getEncoder().encodeToString("$card:$pin".toByteArray())
    }
}
