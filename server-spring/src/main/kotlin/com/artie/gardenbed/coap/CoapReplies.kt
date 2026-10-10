package com.artie.gardenbed.coap

import com.artie.gardenbed.web.Problems
import com.artie.heart.Dialect
import com.artie.heart.HEART_RISE_PROFILE
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
    fun immutable(body: String, contentFormat: Int): Response =
        Response(ResponseCode.CONTENT).apply {
            payload = body.toByteArray(Charsets.UTF_8)
            options.contentFormat = contentFormat
            options.maxAge = YEAR
        }

    /** Any other document, cached as long as [maxAge] says. */
    fun document(body: String, contentFormat: Int, maxAge: Long): Response =
        Response(ResponseCode.CONTENT).apply {
            payload = body.toByteArray(Charsets.UTF_8)
            options.contentFormat = contentFormat
            options.maxAge = maxAge
        }

    /** An RFC 9457 problem details object (SPEC §6.2). CoAP has no HTTP status, so none is given. */
    fun problem(
        code: ResponseCode,
        type: String,
        title: String,
        detail: String?,
        members: Map<String, Any> = emptyMap(),
    ): Response =
        Response(code).apply {
            val problem = mapOf("type" to Problems.BASE + type, "title" to title) +
                listOfNotNull(detail?.let { "detail" to it }) + members
            payload = bytes(problem)
            options.contentFormat = CoapOptions.PROBLEM_JSON
        }

    /** An error that is not a refusal of an act. */
    fun error(code: ResponseCode, message: String?): Response =
        Response(code).apply {
            payload = bytes(mapOf("error" to (message ?: code.name)))
            options.contentFormat = CoapOptions.JSON
        }

    private fun bytes(body: Any): ByteArray = when (body) {
        is String -> body.toByteArray(Charsets.UTF_8)
        else -> objectMapper.writeValueAsBytes(body)
    }

    private companion object {
        const val YEAR = 31_536_000L
    }
}
