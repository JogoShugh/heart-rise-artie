package com.artie.heart.coap

import com.artie.heart.Dialect
import com.artie.heart.HEART_RISE_PROFILE
import com.artie.heart.MalformedActBodyException
import com.artie.heart.NotOfferedException
import com.artie.heart.PROBLEM_TYPES
import com.artie.heart.PreconditionRequiredException
import com.artie.heart.ResourceNotFoundException
import com.artie.heart.StaleWriteConflict
import com.artie.heart.UnprocessableContentException
import com.fasterxml.jackson.databind.ObjectMapper
import org.eclipse.californium.core.coap.CoAP.ResponseCode
import org.eclipse.californium.core.coap.Option
import org.eclipse.californium.core.coap.Response

/** Builds the CoAP responses of SPEC §6.0.1. */
internal class CoapReplies(private val objectMapper: ObjectMapper) {

    /**
     * A Layer 1 representation in hal-schema-forms: never fresh (Max-Age 0), with
     * its 8-byte validator, its profiles and, above L0, the rung it is at.
     */
    fun layer1(code: ResponseCode, body: Any, validator: ByteArray?, preferenceApplied: String?): Response =
        Response(code).apply {
            payload = bytes(body)
            options.contentFormat = CoapOptions.HAL_JSON
            options.maxAge = 0
            validator?.let { options.addETag(it) }
            options.addOption(Option(CoapOptions.PROFILE, Dialect.JSONSCHEMA.profile))
            options.addOption(Option(CoapOptions.PROFILE, HEART_RISE_PROFILE))
            preferenceApplied?.let { options.addOption(Option(CoapOptions.PREFERENCE_APPLIED, it)) }
        }

    /** 2.03 Valid: the representation the client holds is current. */
    fun valid(validator: ByteArray): Response =
        Response(ResponseCode.VALID).apply {
            options.addETag(validator)
            options.maxAge = 0
        }

    /** A resource that never changes at its URI (SPEC §6.5). */
    fun immutable(body: String, contentFormat: Int): Response = document(body, contentFormat, YEAR)

    /** Any other document, cached as long as [maxAge] says. */
    fun document(body: String, contentFormat: Int, maxAge: Long): Response =
        Response(ResponseCode.CONTENT).apply {
            payload = body.toByteArray(Charsets.UTF_8)
            options.contentFormat = contentFormat
            options.maxAge = maxAge
        }

    /**
     * The answer for [failure], or null when it is not a refusal this binding
     * knows. CoAP has no 4.28, so precondition required is 4.00 and the problem
     * type tells it apart (SPEC §6.0.1).
     */
    fun refusal(failure: Throwable): Response? = when (failure) {
        is PreconditionRequiredException ->
            problem(ResponseCode.BAD_REQUEST, "precondition-required", "If-Match is required", failure.message)
        is StaleWriteConflict -> problem(
            ResponseCode.PRECONDITION_FAILED, "stale", "The act is stale: the state has changed", failure.message,
        )
        is NotOfferedException -> problem(
            ResponseCode.CONFLICT, "not-offered", "The act is not offered in the resource's current state",
            failure.message, listOfNotNull(failure.rel?.let { "rel" to it }).toMap(),
        )
        is UnprocessableContentException -> problem(
            ResponseCode.UNPROCESSABLE_ENTITY, "invalid-input", "The body does not match the form's schema",
            failure.message, mapOf("violations" to failure.violations),
        )
        is MalformedActBodyException -> error(ResponseCode.BAD_REQUEST, failure.message)
        is ResourceNotFoundException -> error(ResponseCode.NOT_FOUND, failure.message)
        else -> null
    }

    /** An error that is not a refusal of an act. */
    fun error(code: ResponseCode, message: String?): Response =
        Response(code).apply {
            payload = bytes(mapOf("error" to (message ?: code.name)))
            options.contentFormat = CoapOptions.JSON
        }

    /** An RFC 9457 problem details object (SPEC §6.2). CoAP has no HTTP status, so none is given. */
    private fun problem(
        code: ResponseCode,
        type: String,
        title: String,
        detail: String?,
        members: Map<String, Any> = emptyMap(),
    ): Response =
        Response(code).apply {
            val problem = mapOf("type" to PROBLEM_TYPES + type, "title" to title) +
                listOfNotNull(detail?.let { "detail" to it }) + members
            payload = bytes(problem)
            options.contentFormat = CoapOptions.PROBLEM_JSON
        }

    private fun bytes(body: Any): ByteArray = when (body) {
        is String -> body.toByteArray(Charsets.UTF_8)
        else -> objectMapper.writeValueAsBytes(body)
    }

    private companion object {
        const val YEAR = 31_536_000L
    }
}
