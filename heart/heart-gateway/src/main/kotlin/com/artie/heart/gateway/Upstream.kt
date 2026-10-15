package com.artie.heart.gateway

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
}

/** One answer from the upstream: its JSON body, and the links in its Link header by relation. */
class Answer(val body: JsonNode, val links: Map<String, Uri>)

/**
 * The upstream, as the gateway calls it: plain JSON only, with the gateway's
 * own credential. A refusal the upstream answers with an RFC 9457 problem is
 * raised as the same refusal in heart-core, so a client of the gateway is
 * told what a client of the upstream would be.
 */
class Upstream(private val http: HttpHandler, private val auth: UpstreamAuth, private val mapper: ObjectMapper) {

    fun get(path: String): Answer = send(Request(Method.GET, path))

    fun post(path: String, body: String): Answer =
        send(Request(Method.POST, path).header("Content-Type", JSON).body(body))

    private fun send(request: Request): Answer {
        val signed = auth.authorization()?.let { request.header("Authorization", it) } ?: request
        val response = http(signed.header("Accept", JSON))
        if (!response.status.successful) throw refusal(request, response)
        return Answer(mapper.readTree(response.bodyString()), Header.LINK(response))
    }

    private fun refusal(request: Request, response: Response): RuntimeException {
        val problem = runCatching { mapper.readTree(response.bodyString()) }.getOrNull()
        val detail = problem?.path("detail")?.asText(null) ?: "the upstream answered ${response.status}"
        val violations = problem?.path("violations")?.toList().orEmpty()
            .map { Violation(it.path("pointer").asText(), it.path("message").asText()) }
        return when {
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
    }
}
