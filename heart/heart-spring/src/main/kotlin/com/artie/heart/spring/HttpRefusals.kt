package com.artie.heart.spring

import com.artie.heart.BadRequestException
import com.artie.heart.CallerForbiddenException
import com.artie.heart.CallerUnauthorizedException
import com.artie.heart.HtmlProblem
import com.artie.heart.MalformedActBodyException
import com.artie.heart.NotAcceptableException
import com.artie.heart.NotOfferedException
import com.artie.heart.PROBLEM_TYPES
import com.artie.heart.PreconditionRequiredException
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.RuleRefusedException
import com.artie.heart.StaleWriteConflict
import com.artie.heart.UnprocessableContentException
import com.artie.heart.Violation
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import java.net.URI

/**
 * SPEC §6.2: every refusal of an act is an RFC 9457 problem details object.
 * The `type` URI names the refusal, so a client tells refusals apart without
 * reading `title` or `detail`. Not found, not acceptable and not JSON are
 * plain errors, as before. For a browser, the same refusal is HTML
 * (spec/PLAN-HTML-DIALECT.md), at the same status.
 */
object HttpRefusals {
    /** The problem for [type] at [status], as a body any binding can send. */
    fun problem(status: HttpStatus, type: String, title: String, detail: String?, members: Map<String, Any>) =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.type = URI.create(PROBLEM_TYPES + type)
            this.title = title
            members.forEach { (name, value) -> setProperty(name, value) }
        }

    /**
     * The answer for [failure], or null when it is not a refusal heart-spring knows; a 401 offers [challenges].
     * When [html], a refusal is HTML for the browser that asked.
     */
    suspend fun of(failure: Throwable, challenges: List<String>, html: Boolean = false): ServerResponse? {
        val refusal = refusal(failure)
        val plain = plainError(failure)
        return when {
            refusal != null -> respond(refusal, failure, challenges, html)
            plain != null -> ServerResponse.status(plain).contentType(MediaType.APPLICATION_JSON)
                .bodyValueAndAwait(mapOf("error" to (failure.message ?: plain.reasonPhrase)))
            else -> null
        }
    }

    private class Refusal(
        val status: HttpStatus,
        val type: String,
        val title: String,
        val members: Map<String, Any> = emptyMap(),
    )

    private fun refusal(failure: Throwable): Refusal? = when (failure) {
        is CallerUnauthorizedException -> Refusal(HttpStatus.UNAUTHORIZED, "not-authenticated", NEEDED)
        is CallerForbiddenException ->
            Refusal(HttpStatus.FORBIDDEN, "forbidden", "The credential is not enough for this resource")
        is PreconditionRequiredException ->
            Refusal(HttpStatus.PRECONDITION_REQUIRED, "precondition-required", "If-Match is required")
        is StaleWriteConflict ->
            Refusal(HttpStatus.PRECONDITION_FAILED, "stale", "The act is stale: the state has changed")
        is NotOfferedException -> Refusal(
            HttpStatus.CONFLICT, "not-offered", "The act is not offered in the resource's current state",
            listOfNotNull(failure.rel?.let { "rel" to it }).toMap(),
        )
        is UnprocessableContentException -> Refusal(
            HttpStatus.UNPROCESSABLE_ENTITY, "invalid-input", "The body does not match the form's schema",
            mapOf("violations" to failure.violations),
        )
        is RuleRefusedException -> Refusal(
            HttpStatus.UNPROCESSABLE_ENTITY, "rule-refused", "A rule of the resource refuses the act",
            mapOf("violations" to failure.violations),
        )
        else -> null
    }

    private fun plainError(failure: Throwable): HttpStatus? = when (failure) {
        is ResourceNotFoundException -> HttpStatus.NOT_FOUND
        is NotAcceptableException -> HttpStatus.NOT_ACCEPTABLE
        is MalformedActBodyException, is BadRequestException -> HttpStatus.BAD_REQUEST
        else -> null
    }

    private suspend fun respond(
        refusal: Refusal,
        failure: Throwable,
        challenges: List<String>,
        html: Boolean,
    ): ServerResponse {
        // RFC 9110 §15.5.2: a 401 says how to authenticate, as the service's own refusal does.
        val builder = ServerResponse.status(refusal.status).headers {
            if (refusal.status == HttpStatus.UNAUTHORIZED) it[HttpHeaders.WWW_AUTHENTICATE] = challenges
        }
        return if (html) {
            val violations = (refusal.members["violations"] as? List<*>).orEmpty().filterIsInstance<Violation>()
            val page = mapOf(
                "title" to refusal.title,
                "detail" to failure.message,
                "violations" to violations.map { mapOf("pointer" to it.pointer, "message" to it.message) },
            )
            builder.contentType(MediaType.parseMediaType(HtmlProblem.MEDIA_TYPE))
                .bodyValueAndAwait(HtmlProblem.of(page))
        } else {
            val problem = problem(refusal.status, refusal.type, refusal.title, failure.message, refusal.members)
            builder.contentType(MediaType.APPLICATION_PROBLEM_JSON).bodyValueAndAwait(problem)
        }
    }

    private const val NEEDED = "A credential is needed"
}
