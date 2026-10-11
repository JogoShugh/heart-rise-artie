package com.artie.heart

/**
 * The refusals of an act, in SPEC §6.2's evaluation order: no validator
 * (428), a stale validator (412), an act not offered (409), then a body that
 * does not satisfy the schema (422). Each binding maps them to its own codes.
 */

/**
 * SPEC §6.2: every refusal of an act is an RFC 9457 problem details object,
 * whose `type` is this base followed by the refusal's name, so a client tells
 * refusals apart without reading `title` or `detail`.
 */
const val PROBLEM_TYPES = "https://github.com/jogoshugh/heart-rise/problems/"

/** 428: a state-changing request arrived without a validator. */
class PreconditionRequiredException(message: String) : RuntimeException(message)

/** 412: the validator does not match the current one, or another write landed first. */
class StaleWriteConflict(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown by a store or a domain when another write landed first, while an
 * act was running. heart-core turns it into a stale refusal.
 */
open class ConcurrentWriteException(message: String) : RuntimeException(message)

/**
 * 409: the act is not in the resource's current affordance set. [rel] names
 * its relation, when known. A domain's own guard may extend this, so the
 * refusal is the same whichever layer finds it.
 */
open class NotOfferedException(message: String, val rel: String? = null) : RuntimeException(message)

/** 400: a request a binding cannot read, such as an instant that is not ISO 8601. */
class BadRequestException(message: String) : RuntimeException(message)

/** 404: no resource with that id. A domain's own "not found" extends this. */
open class ResourceNotFoundException(message: String) : RuntimeException(message)

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
