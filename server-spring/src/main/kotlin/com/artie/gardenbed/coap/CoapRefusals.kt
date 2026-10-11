package com.artie.gardenbed.coap

import com.artie.gardenbed.service.BedNotFoundException
import com.artie.gardenbed.service.InvalidTransitionException
import com.artie.heart.MalformedActBodyException
import com.artie.heart.NotOfferedException
import com.artie.heart.PreconditionRequiredException
import com.artie.heart.StaleWriteConflict
import com.artie.heart.UnprocessableContentException
import org.eclipse.californium.core.coap.CoAP.ResponseCode
import org.eclipse.californium.core.coap.Response

/**
 * SPEC §6.0.1: the refusals of an act as CoAP codes, each with its RFC 9457
 * problem type. CoAP has no 4.28, so precondition required is 4.00 and the
 * problem type tells it apart.
 */
internal class CoapRefusals(private val replies: CoapReplies) {

    /** The response for [failure], or null when it is not a refusal this binding knows. */
    fun of(failure: Throwable): Response? = when (failure) {
        is PreconditionRequiredException ->
            replies.problem(ResponseCode.BAD_REQUEST, "precondition-required", "If-Match is required", failure.message)
        is StaleWriteConflict -> replies.problem(
            ResponseCode.PRECONDITION_FAILED, "stale", "The act is stale: the state has changed", failure.message,
        )
        is InvalidTransitionException -> replies.problem(
            ResponseCode.CONFLICT, "not-offered", "The act is not offered in the resource's current state",
            failure.message, listOfNotNull(failure.rel?.let { "rel" to it }).toMap(),
        )
        is NotOfferedException -> replies.problem(
            ResponseCode.CONFLICT, "not-offered", "The act is not offered in the resource's current state",
            failure.message, mapOf("rel" to failure.rel),
        )
        is UnprocessableContentException -> replies.problem(
            ResponseCode.UNPROCESSABLE_ENTITY, "invalid-input", "The body does not match the form's schema",
            failure.message, mapOf("violations" to failure.violations),
        )
        is MalformedActBodyException -> replies.error(ResponseCode.BAD_REQUEST, failure.message)
        is BedNotFoundException, is NumberFormatException -> replies.error(ResponseCode.NOT_FOUND, failure.message)
        else -> null
    }
}
