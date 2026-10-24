package com.artie.heart.gateway

import com.artie.heart.Caller
import com.artie.heart.CallerForbiddenException
import com.artie.heart.CallerUnauthorizedException
import com.artie.heart.NotOfferedException
import com.artie.heart.PROBLEM_TYPES
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.RuleRefusedException
import com.artie.heart.StaleWriteConflict
import com.artie.heart.UnprocessableContentException
import com.artie.heart.Violation
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.lens.Header
import java.util.Base64

/** How the gateway proves itself to the upstream: no credential, a Bearer token, or a Basic user. */
sealed interface UpstreamAuth {
    fun authorization(): String?

    data object None : UpstreamAuth {
        override fun authorization(): String? = null
    }

    class Bearer(private val token: String) : UpstreamAuth {
        override fun authorization() = "Bearer $token"
    }

    class Basic(private val user: String, private val password: String) : UpstreamAuth {
        override fun authorization() =
            "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
    }

    /** Each caller's own credential, forwarded: what a caller may see and do is the upstream's to say. */
    data object Forwarded : UpstreamAuth {
        override fun authorization(): String? = Caller.authorization()
    }
}

/** What an upstream's statuses mean for one act: [stale], the client's view is stale; [refused], not now. */
data class RefusalStatuses(val stale: Int? = null, val refused: Int? = null)

/**
 * One answer from the upstream: its JSON body, the links in its Link header
 * by relation, the path it was asked at, and its media type, without parameters.
 */
class Answer(
    val body: UpstreamBody,
    val links: UpstreamLinks,
    val path: String,
    val mediaType: MediaType?,
    /** Other answers a reader's rules may read, by the name the resource file gives them: `repository`. */
    val facts: Map<String, UpstreamBody> = emptyMap(),
) {
    fun with(facts: Map<String, UpstreamBody>) = Answer(body, links, path, mediaType, this.facts + facts)
}

/**
 * The upstream, as the gateway calls it: plain JSON only, with the gateway's
 * own credential. A refusal the upstream answers with an RFC 9457 problem is
 * raised as the same refusal in heart-core, so a client of the gateway is
 * told what a client of the upstream would be.
 */
class Upstream(
    private val http: HttpHandler,
    private val auth: UpstreamAuth,
    private val mapper: ObjectMapper,
    /** Headers every call carries, as the connector names them. */
    private val headers: Map<String, String> = emptyMap(),
) {

    /** What each caller last read, with its ETag, by caller and path, so a read again can be a 304. */
    private val copies = Copies()

    fun get(path: String): Answer = send(Request(Method.GET, path))

    fun post(path: String, body: String): Answer =
        send(Request(Method.POST, path).header("Content-Type", JSON).body(body))

    /**
     * An act's call, by its operation's [method]. The upstream's [statuses]
     * say what its own refusals mean, where it names them: GitHub answers a
     * merge whose `sha` no longer matches with 409, which is a stale view.
     */
    fun act(method: String, path: String, body: String, statuses: RefusalStatuses): Answer {
        // A DELETE with nothing to say sends no body, as GitHub's unlock and remove-label expect.
        val request = Request(Method.valueOf(method), path)
        val sent = if (method == "DELETE" && body == "{}") request else request.header("Content-Type", JSON).body(body)
        return send(sent, statuses)
    }

    private fun send(request: Request, statuses: RefusalStatuses = RefusalStatuses()): Answer {
        val authorization = auth.authorization()
        val signed = authorization?.let { request.header("Authorization", it) } ?: request
        val sent = headers.entries.fold(signed.header("Accept", JSON)) { r, (name, value) -> r.header(name, value) }
        // A trial that trusts copies uses one read recently enough without asking (GatewayCare).
        val trusted = GatewayCare.now().trustCopies?.takeIf { request.method == Method.GET }?.let { within ->
            copies.fresh(authorization, request.uri.toString(), within)
        }
        return trusted?.answer ?: asked(sent, request, authorization, statuses)
    }

    /** [sent] to the upstream, conditionally where a copy is held, as [request] asked by [authorization]. */
    private fun asked(sent: Request, request: Request, authorization: String?, statuses: RefusalStatuses): Answer {
        val reading = request.method == Method.GET
        val at = request.uri.toString()
        val held = if (reading) copies.held(authorization, at) else null
        val conditional = held?.let { sent.header("If-None-Match", it.etag) } ?: sent
        val response = if (reading) caughtUp(conditional, authorization, at) else http(conditional)
        if (held != null && response.status == Status.NOT_MODIFIED) {
            return held.answer.also { copies.keep(authorization, at, held.etag, it) }
        }
        if (!response.status.successful) throw refusal(request, response, statuses)
        val mediaType = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
        val answer = Answer(mapper.readTree(response.bodyString()), Header.LINK(response), request.uri.path, mediaType)
        if (reading) {
            response.header("ETag")?.let { copies.keep(authorization, at, it, answer) }
        } else {
            copies.drop(authorization, request.uri.path)
        }
        return answer
    }

    /**
     * The read [sent], asked again a few times, a moment apart, while its answer is a state [at] has been seen
     * replaced in: a copy the upstream hasn't caught up yet. After those tries the answer is taken as it is.
     */
    private fun caughtUp(sent: Request, authorization: String?, at: String): Response {
        var response = http(sent)
        var tries = 0
        val behind = { answer: Response -> answer.header("ETag")?.let { copies.stepsBack(authorization, at, it) } }
        while (tries < CATCH_UP_TRIES && behind(response) == true) {
            Thread.sleep(CATCH_UP_PAUSE_MS)
            response = http(sent)
            tries++
        }
        return response
    }

    private fun refusal(request: Request, response: Response, statuses: RefusalStatuses): RuntimeException {
        val problem = runCatching { mapper.readTree(response.bodyString()) }.getOrNull()
        // RFC 9457 says it in detail; GitHub, in message.
        val detail = problem?.path("detail")?.asText(null) ?: problem?.path("message")?.asText(null)
            ?: "the upstream answered ${response.status}"
        // An upstream that names no violation, as GitHub's 422s don't, still said why: the body as a whole.
        val violations = problem?.path("violations")?.toList().orEmpty()
            .map { Violation(it.path("pointer").asText(), it.path("message").asText()) }
            .ifEmpty { listOf(Violation("", detail)) }
        return when {
            response.status.code == statuses.stale -> StaleWriteConflict(detail)
            response.status.code == statuses.refused -> NotOfferedException(detail)
            response.status == Status.UNAUTHORIZED -> CallerUnauthorizedException(detail)
            response.status == Status.FORBIDDEN -> CallerForbiddenException(detail)
            response.status == Status.NOT_FOUND -> ResourceNotFoundException(detail)
            response.status == Status.CONFLICT -> NotOfferedException(detail)
            response.status == Status.PRECONDITION_FAILED -> StaleWriteConflict(detail)
            problem?.path("type")?.asText() == PROBLEM_TYPES + "rule-refused" -> RuleRefusedException(violations)
            response.status == Status.UNPROCESSABLE_ENTITY -> UnprocessableContentException(violations)
            else -> IllegalStateException("${request.method} ${request.uri}: $detail")
        }
    }

    private companion object {
        const val JSON = "application/json"

        /** How many times a read from a copy that is behind is asked again, and how long apart. */
        const val CATCH_UP_TRIES = 3
        const val CATCH_UP_PAUSE_MS = 250L
    }
}
