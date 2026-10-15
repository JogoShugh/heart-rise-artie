package com.artie.heart.http4k

import com.artie.heart.BadRequestException
import com.artie.heart.MalformedActBodyException
import com.artie.heart.NotAcceptableException
import com.artie.heart.NotOfferedException
import com.artie.heart.PROBLEM_TYPES
import com.artie.heart.PreconditionRequiredException
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.RuleRefusedException
import com.artie.heart.StaleWriteConflict
import com.artie.heart.UnprocessableContentException
import com.fasterxml.jackson.databind.ObjectMapper
import org.http4k.core.Filter
import org.http4k.core.Response
import org.http4k.core.Status

/**
 * SPEC §6.2: every refusal of an act is an RFC 9457 problem details object.
 * The `type` URI names the refusal, so a client tells refusals apart without
 * reading `title` or `detail`. Not found, not acceptable and not JSON are
 * plain errors, as heart-spring answers them.
 */
object Refusals {
    const val PROBLEM_JSON = "application/problem+json"

    /**
     * Answers each refusal heart-core raises; anything else is thrown again.
     * heart-core's refusals share no supertype but [RuntimeException].
     */
    @Suppress("TooGenericExceptionCaught")
    fun filter(mapper: ObjectMapper) = Filter { next ->
        { request ->
            try {
                next(request)
            } catch (failure: RuntimeException) {
                of(failure, mapper) ?: throw failure
            }
        }
    }

    /** The answer for [failure], or null when it is not a refusal heart-core raises. */
    fun of(failure: Throwable, mapper: ObjectMapper): Response? = when (failure) {
        is PreconditionRequiredException ->
            problem(Status.PRECONDITION_REQUIRED, "precondition-required", "If-Match is required", failure, mapper)
        is StaleWriteConflict ->
            problem(Status.PRECONDITION_FAILED, "stale", "The act is stale: the state has changed", failure, mapper)
        is NotOfferedException -> problem(
            Status.CONFLICT, "not-offered", "The act is not offered in the resource's current state", failure, mapper,
            listOfNotNull(failure.rel?.let { "rel" to it }).toMap(),
        )
        is UnprocessableContentException -> problem(
            Status.UNPROCESSABLE_ENTITY, "invalid-input", "The body does not match the form's schema", failure, mapper,
            mapOf("violations" to failure.violations),
        )
        is RuleRefusedException -> problem(
            Status.UNPROCESSABLE_ENTITY, "rule-refused", "A rule of the resource refuses the act", failure, mapper,
            mapOf("violations" to failure.violations),
        )
        is ResourceNotFoundException -> error(Status.NOT_FOUND, failure, mapper)
        is NotAcceptableException -> error(Status.NOT_ACCEPTABLE, failure, mapper)
        is MalformedActBodyException, is BadRequestException -> error(Status.BAD_REQUEST, failure, mapper)
        else -> null
    }

    /** The problem for [type] at [status], with its members after the standard ones. */
    fun problem(status: Status, type: String, title: String, detail: String?, members: Map<String, Any>) =
        linkedMapOf<String, Any?>("type" to PROBLEM_TYPES + type, "title" to title, "status" to status.code)
            .apply { if (detail != null) put("detail", detail) }
            .apply { putAll(members) }

    @Suppress("LongParameterList")
    private fun problem(
        status: Status,
        type: String,
        title: String,
        failure: Throwable,
        mapper: ObjectMapper,
        members: Map<String, Any> = emptyMap(),
    ): Response = Response(status).header("Content-Type", PROBLEM_JSON)
        .body(mapper.writeValueAsString(problem(status, type, title, failure.message, members)))

    private fun error(status: Status, failure: Throwable, mapper: ObjectMapper): Response =
        Response(status).header("Content-Type", "application/json")
            .body(mapper.writeValueAsString(mapOf("error" to (failure.message ?: status.description))))
}
