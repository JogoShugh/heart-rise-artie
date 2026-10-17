package com.artie.heart.gateway

import com.fasterxml.jackson.databind.JsonNode
import org.http4k.core.Uri

/** An operation of the upstream's OpenAPI description, by its `operationId`. */
typealias OperationId = String

/** Why an operation is not allowed now, in words a person or a model reads. */
typealias Reason = String

/** A link relation, as an upstream's Link header names it. */
typealias Rel = String

/** One upstream answer's JSON body. */
typealias UpstreamBody = JsonNode

/** One upstream answer's links, by relation. */
typealias UpstreamLinks = Map<Rel, Uri>

/** The operations an upstream allows a resource now. */
typealias AllowedOperations = Set<OperationId>

/** Why some operations are not allowed now, where the signal says. */
typealias AbsenceReasons = Map<OperationId, Reason>

/** A media type without its parameters, lower case: `application/vnd.doors.vault+json`. */
typealias MediaType = String

/** A path template from the upstream's OpenAPI description: `/doors/{id}`. */
typealias PathTemplate = String

/** What one upstream answer allows now, and why others are absent where the signal says. */
class Allowed(val operations: AllowedOperations, val reasons: AbsenceReasons = emptyMap())

/** One upstream answer, read: what it allows now, and its body without its controls, the state alone. */
class Reading(val allowed: Allowed, val state: UpstreamBody)

/**
 * One way an upstream signals what is allowed now: HEART's affordance link,
 * or a connector's signal read from the answer itself.
 */
fun interface AllowedNow {
    fun of(body: UpstreamBody, links: UpstreamLinks): Allowed
}
