package com.artie.heart

/**
 * The refusals of an act, in SPEC §6.2's evaluation order: no validator
 * (428), a stale validator (412), an act not offered (409), then a body that
 * does not satisfy the schema (422). Each binding maps them to its own codes.
 */

/** 428: a state-changing request arrived without a validator. */
class PreconditionRequiredException(message: String) : RuntimeException(message)

/** 412: the validator does not match the current one, or another write landed first. */
class StaleWriteConflict(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** One failing field: an RFC 6901 JSON Pointer into the request body, and why. */
data class Violation(val pointer: String, val message: String)

/** 422: the body is JSON but does not satisfy the affordance's schema. */
class UnprocessableContentException(val violations: List<Violation>, cause: Throwable? = null) :
    RuntimeException("request body does not satisfy the affordance schema", cause)

/** 400: the body is not JSON at all. */
class MalformedActBodyException(message: String, cause: Throwable) : RuntimeException(message, cause)

/** Path segments as a JSON Pointer, escaping `~` and `/` (RFC 6901 §3). */
fun pointerOf(segments: List<String>): String =
    segments.joinToString("") { "/" + it.replace("~", "~0").replace("/", "~1") }
