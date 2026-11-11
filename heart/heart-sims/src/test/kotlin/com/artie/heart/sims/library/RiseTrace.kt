package com.artie.heart.sims.library

import com.artie.heart.Signer
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * Writes the RISE library's exchanges as a run trace for the run report (heart-run-report): Ana's request, and the
 * library's answers on her reply, each request and answer as they went over the in-memory services, in order. It
 * writes only when RISE_TRACE_OUT names the file, so an ordinary test run writes nothing.
 */
class RiseTrace {
    private val mapper = ObjectMapper()
    private val yaml = ObjectMapper(YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER))
    private val steps = mutableListOf<Map<String, Any?>>()
    private val targets = mutableMapOf<String, String>()
    private val library = LibraryService()
    private val caller = CallerService(watchKeys = { mapOf(LibraryKey.KEY_ID to LibraryKey.pair.public) })

    /** What the library sends on a [relation] of Ana's reply: its [body], its [contentType], how it is signed. */
    private class Answer(
        val relation: String,
        val body: String,
        val contentType: String,
        val signer: Signer?,
        val expect: Int,
        val say: String,
    )

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
        caller.open("r-7f3")

        // Ana asks to be told; the library accepts with a watch.
        ask(3310, "r-7f3")

        // The hold is in line: progress, then a forgery and an unsigned result, both refused.
        answer("r-7f3", Answer(PROGRESS, json(mapOf("percent" to 0, "note" to "one ahead in the hold queue")), HAL,
            LibraryKey.signer, 200, "The library reports progress of 0 percent on reply r-7f3"))
        val nextReader = mapOf("percent" to 50, "note" to "the hold is with the next reader")
        answer("r-7f3", Answer(PROGRESS, json(nextReader), HAL, LibraryKey.signer, 200,
            "The library reports progress of 50 percent on reply r-7f3"))
        answer("r-7f3", Answer(COMPLETED, json(RESULT_2026_12_03), HAL, LibraryKey.otherSigner, 401,
            "A result signed with another key, claiming the library's keyid, is sent on reply r-7f3"))
        answer("r-7f3", Answer(COMPLETED, json(RESULT_2026_12_03), HAL, null, 401,
            "An unsigned result is sent on reply r-7f3"))

        // The hold clears: the library sends the result, signed; a repeat is replayed; a different result is refused.
        library.releaseHold(3310)
        answer("r-7f3", Answer(COMPLETED, json(RESULT_2026_12_03), HAL, LibraryKey.signer, 200,
            "The hold on loan 3310 is returned, and the library sends the result on reply r-7f3, signed"))
        answer("r-7f3", Answer(COMPLETED, json(RESULT_2026_12_03), HAL, LibraryKey.signer, 200,
            "The same result is sent again on reply r-7f3"))
        answer("r-7f3", Answer(COMPLETED, json(RESULT_2026_12_10), HAL, LibraryKey.signer, 409,
            "A different result is sent on reply r-7f3, after the answer"))

        // Another request, whose hold the library has no record of: a failure, as a problem document.
        caller.open("r-b04")
        ask(3310, "r-b04")
        answer("r-b04", Answer(FAILED, json(mapOf("title" to "No hold record for loan 3310")), PROBLEM,
            LibraryKey.signer, 200, "The library has no record of the hold on loan 3310, and sends the failure"))

        File(out).writeText(yaml.writeValueAsString(mapOf("steps" to steps)))
    }

    /** Ana reads the library's entry, then asks to be told when [loan] can be renewed, with [reply] as her reply. */
    private fun ask(loan: Long, reply: String) {
        val entry = exchange(
            "Ana reads the library's entry, as HAL Schema Forms", emptyMap(), 200, library.handler,
            hal(Request(Method.GET, "/"), basic(CARD, PIN)),
        )
        val form = mapper.readTree(entry.bodyString())["_forms"]["library:tell-me-when-renewable"]
        val sent = mapOf(
            "loan" to loan,
            "_links" to mapOf(
                "hr:reply-to" to mapOf(
                    "href" to "https://caller.example/replies/$reply",
                    "type" to HAL,
                    "profile" to "https://w3id.org/heart-rise/hal-schema-forms",
                ),
                "hr:keys" to mapOf("href" to "https://caller.example/keys", "type" to "application/jwk-set+json"),
            ),
        )
        val posted = exchange(
            "Ana asks to be told when loan $loan can be renewed, with reply $reply and her key",
            mapOf("loan" to loan, "reply" to reply),
            201,
            library.handler,
            hal(Request(Method.POST, form["_links"]["target"]["href"].asText()), basic(CARD, PIN))
                .header("If-Match", entry.header("ETag"))
                .header("Content-Type", HAL)
                .body(json(sent)),
        )
        val location = checkNotNull(posted.header("Location")) { "no Location: ${posted.bodyString()}" }
        exchange(
            "Ana reads her watch on loan $loan", mapOf("loan" to loan), 200, library.handler,
            hal(Request(Method.GET, location), basic(CARD, PIN)),
        )
    }

    /** The library reads Ana's [reply] for its ETag and form, then sends [sent] on its form, signed or not. */
    private fun answer(reply: String, sent: Answer) {
        val read = exchange(
            "The library reads Ana's reply $reply for its ETag and form", mapOf("reply" to reply), 200,
            caller.handler, hal(Request(Method.GET, "/replies/$reply"), REPLY_AUTH),
        )
        val ifMatch = checkNotNull(read.header("ETag")) { "no ETag: ${read.bodyString()}" }
        // A repeat is sent to the form the first answer found: a reply answered offers no form for it any more.
        val target = targets[sent.relation] ?: run {
            val forms = mapper.readTree(read.bodyString())["_forms"]
            val form = checkNotNull(forms?.get("hr:${sent.relation}")) {
                "no ${sent.relation} form: ${read.bodyString()}"
            }
            URI(form["_links"]["target"]["href"].asText()).path.also { targets[sent.relation] = it }
        }
        val signed = sent.signer?.sign("PUT", "https://caller.example$target", sent.body, ifMatch, SIGNED_AT)
        val request = Request(Method.PUT, target)
            .header("Authorization", REPLY_AUTH)
            .header("Accept", HAL)
            .header("Accept-Profile", PROFILE)
            .header("If-Match", ifMatch)
            .header("Content-Type", sent.contentType)
        val sending = if (signed == null) {
            request.body(sent.body)
        } else {
            request.header("Content-Digest", signed.contentDigest)
                .header("Signature-Input", "sig1=${signed.params}")
                .header("Signature", "sig1=:${signed.signature}:")
                .body(sent.body)
        }
        exchange(sent.say, mapOf("reply" to reply, "relation" to sent.relation), sent.expect, caller.handler, sending)
    }

    /** One exchange, timed and recorded in order: [say] is what it does, in plain words. */
    private fun exchange(
        say: String,
        params: Map<String, Any?>,
        expect: Int,
        handler: HttpHandler,
        request: Request,
    ): Response {
        val at = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val started = System.nanoTime()
        val response = handler(request)
        val ms = (System.nanoTime() - started) / NANOS_PER_MILLI
        check(response.status.code == expect) {
            "$say: expected $expect, got ${response.status.code}: ${response.bodyString()}"
        }
        steps += linkedMapOf(
            "at" to at,
            "mode" to "script",
            "say" to say,
            "params" to params,
            "request" to requestOf(request),
            "response" to responseOf(response),
            "expect" to expect,
            "ms" to ms,
        )
        return response
    }

    private fun requestOf(request: Request): Map<String, Any?> = linkedMapOf(
        "method" to request.method.name,
        "url" to request.uri.toString(),
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

    private fun json(value: Any): String = mapper.writeValueAsString(value)

    private fun hal(request: Request, authorization: String): Request =
        request.header("Authorization", authorization).header("Accept", HAL).header("Accept-Profile", PROFILE)

    private companion object {
        const val CARD = "21000451"
        const val PIN = "1234"
        const val HAL = "application/hal+json"
        const val PROBLEM = "application/problem+json"
        const val PROFILE = "<https://w3id.org/heart-rise/hal-schema-forms>"
        const val REPLY_AUTH = "Bearer ${CallerService.LIBRARY_TOKEN}"
        const val SIGNED_AT = 1794153600L
        const val NANOS_PER_MILLI = 1_000_000L
        const val PROGRESS = "progress"
        const val COMPLETED = "completed"
        const val FAILED = "failed"

        val RESULT_2026_12_03 = mapOf("result" to mapOf("loan" to 3310, "renewableFrom" to "2026-12-03"))
        val RESULT_2026_12_10 = mapOf("result" to mapOf("loan" to 3310, "renewableFrom" to "2026-12-10"))

        val SHOWN_ON_REQUEST = listOf(
            "Authorization", "Accept", "Accept-Profile", "If-Match", "Content-Type",
            "Content-Digest", "Signature-Input", "Signature",
        )
        val SHOWN_ON_ANSWER = listOf("Location", "ETag", "Content-Type")

        fun basic(card: String, pin: String) = "Basic " + Base64.getEncoder().encodeToString("$card:$pin".toByteArray())
    }
}
