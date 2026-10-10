package heart

/**
 * The server refused an act. Each kind asks for different handling
 * (SPEC §6.0, §6.2, §6.3), so each is its own type rather than a code of any
 * one protocol. The HTTP binding's codes are given in brackets.
 */
sealed class ActRefused(message: String) : RuntimeException(message)

/** 412: someone else wrote first. The client has re-synced; the act is dropped, never resent. */
class StaleAct(rel: String) : ActRefused("$rel was refused as stale (412); re-synced, act dropped")

/** 409: the act is not legal in the resource's current state. The client has re-synced. */
class IllegalTransition(rel: String) : ActRefused("$rel is not legal in the current state (409); re-synced")

/** 422: the body does not satisfy the affordance's schema. Not retried. */
class InvalidPayload(rel: String, val violations: List<String>) :
    ActRefused("$rel body was refused (422): ${violations.joinToString("; ")}")

/** Any other outcome; [code] is the binding's own word for it (HTTP: the status code). */
class UnexpectedResponse(rel: String, code: String) : ActRefused("$rel got the unexpected answer $code")
