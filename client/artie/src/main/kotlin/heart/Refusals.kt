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

/**
 * Not sent: a value is not among its property's choices (HAL-SCHEMA-FORMS-HR
 * Rule 8). The client checks before sending, as the server would refuse it.
 */
class NotAChoice(rel: String, val violations: List<String>) :
    ActRefused("$rel was not sent: ${violations.joinToString("; ")}")

/**
 * 422 rule-refused: the body satisfied the schema, but a rule of the resource
 * refused it. The client's form handling was right; [violations] give the
 * rule's own words. Not retried.
 */
class RuleRefused(rel: String, val violations: List<String>) :
    ActRefused("$rel was refused by a rule of the resource (422): ${violations.joinToString("; ")}")

/**
 * The service refused access to a resource, for a read or an act. Each
 * message names the origin and the scheme ARTIE sent, never the credential.
 */
sealed class AccessRefused(message: String) : RuntimeException(message)

/** 401: the service wants a credential ([schemes]), or refused the one ARTIE sent ([sent]). */
class NotAuthenticated(val origin: String, val schemes: List<String>, val sent: String?) : AccessRefused(
    if (sent == null) "$origin asks for a credential (${schemes.joinToString()}); ARTIE sent none"
    else "$origin refused the $sent credential ARTIE sent",
)

/** 403: the service knows who is asking, and does not allow this. */
class Forbidden(val origin: String, val sent: String?) : AccessRefused(
    if (sent == null) "$origin does not allow this, and ARTIE sent no credential"
    else "$origin does not allow this with the $sent credential ARTIE sent",
)

/** Any other outcome; [code] is the binding's own word for it (HTTP: the status code). */
class UnexpectedResponse(rel: String, code: String) : ActRefused("$rel got the unexpected answer $code")
