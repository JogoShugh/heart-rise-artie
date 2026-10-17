package com.artie.heart.spring

import com.artie.heart.BadRequestException
import com.artie.heart.CallerForbiddenException
import com.artie.heart.CallerUnauthorizedException
import com.artie.heart.MalformedActBodyException
import com.artie.heart.NotAcceptableException
import com.artie.heart.NotOfferedException
import com.artie.heart.PROBLEM_TYPES
import com.artie.heart.PreconditionRequiredException
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.RuleRefusedException
import com.artie.heart.StaleWriteConflict
import com.artie.heart.UnprocessableContentException
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
 * plain errors, as before.
 */
object HttpRefusals {
    /** The problem for [type] at [status], as a body any binding can send. */
    fun problem(status: HttpStatus, type: String, title: String, detail: String?, members: Map<String, Any>) =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.type = URI.create(PROBLEM_TYPES + type)
            this.title = title
            members.forEach { (name, value) -> setProperty(name, value) }
        }

    /** The answer for [failure], or null when it is not a refusal heart-spring knows; a 401 offers [challenges]. */
    suspend fun of(failure: Throwable, challenges: List<String>): ServerResponse? = when (failure) {
        // RFC 9110 §15.5.2: a 401 says how to authenticate, as the service's own refusal does.
        is CallerUnauthorizedException -> ServerResponse.status(HttpStatus.UNAUTHORIZED)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .headers { it[HttpHeaders.WWW_AUTHENTICATE] = challenges }
            .bodyValueAndAwait(
                problem(HttpStatus.UNAUTHORIZED, "not-authenticated", NEEDED, failure.message, emptyMap()),
            )
        is CallerForbiddenException -> problem(
            HttpStatus.FORBIDDEN, "forbidden", "The credential is not enough for this resource", failure,
        )
        is PreconditionRequiredException ->
            problem(HttpStatus.PRECONDITION_REQUIRED, "precondition-required", "If-Match is required", failure)
        is StaleWriteConflict ->
            problem(HttpStatus.PRECONDITION_FAILED, "stale", "The act is stale: the state has changed", failure)
        is NotOfferedException -> problem(
            HttpStatus.CONFLICT, "not-offered", "The act is not offered in the resource's current state", failure,
            listOfNotNull(failure.rel?.let { "rel" to it }).toMap(),
        )
        is UnprocessableContentException -> problem(
            HttpStatus.UNPROCESSABLE_ENTITY, "invalid-input", "The body does not match the form's schema", failure,
            mapOf("violations" to failure.violations),
        )
        is RuleRefusedException -> problem(
            HttpStatus.UNPROCESSABLE_ENTITY, "rule-refused", "A rule of the resource refuses the act", failure,
            mapOf("violations" to failure.violations),
        )
        is ResourceNotFoundException -> error(HttpStatus.NOT_FOUND, failure)
        is NotAcceptableException -> error(HttpStatus.NOT_ACCEPTABLE, failure)
        is MalformedActBodyException, is BadRequestException -> error(HttpStatus.BAD_REQUEST, failure)
        else -> null
    }

    private suspend fun problem(
        status: HttpStatus,
        type: String,
        title: String,
        failure: Throwable,
        members: Map<String, Any> = emptyMap(),
    ): ServerResponse =
        ServerResponse.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyValueAndAwait(problem(status, type, title, failure.message, members))

    private suspend fun error(status: HttpStatus, failure: Throwable): ServerResponse =
        ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON)
            .bodyValueAndAwait(mapOf("error" to (failure.message ?: status.reasonPhrase)))

    private const val NEEDED = "A credential is needed"
}
