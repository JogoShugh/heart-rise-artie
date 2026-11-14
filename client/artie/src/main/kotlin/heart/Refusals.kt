package heart

/**
 * The server refused an act. Each kind asks for different handling
 * (SPEC §6.0, §6.2, §6.3), so each is its own type rather than a code of any
 * one protocol. The HTTP binding's codes are given in brackets.
 */
sealed class ActRefused(message: String) : RuntimeException(message)

/*
 * A refusal the server sent is said as every client says it: "<rel> was refused (<code>): <what the
 * problem said>", the problem's detail, else its title ([said]).
 */

/** 412: someone else wrote first. The client has re-read; the act is dropped, never resent. */
class StaleAct(said: String) : ActRefused("$said; re-read, not resent")

/** 428: a blind write, sent with no validator, to a server that takes none (spec/ideas/off-the-rails.md). */
class ValidatorRequired(said: String) : ActRefused(said)

/** 409: the act is not legal in the resource's current state. The client has re-read. */
class IllegalTransition(said: String) : ActRefused(said)

/** 422: the body does not satisfy the affordance's schema. Not retried. */
class InvalidPayload(said: String, val violations: List<String>) : ActRefused(said)

/**
 * Not sent: a value is not among its property's choices (HAL-SCHEMA-FORMS-HR
 * Rule 8). The client checks before sending, as the server would refuse it.
 */
class NotAChoice(rel: String, val violations: List<String>) :
    ActRefused("$rel: ${violations.joinToString("; ")}; nothing was sent")

/**
 * Not sent: the fields break the form's declared JSON Schema (SPEC §6.3). The client checks before
 * sending; [violations] each name where in the body and the keyword broken, as every client says them.
 */
class OutsideSchema(rel: String, val violations: List<String>) :
    ActRefused("$rel: ${violations.joinToString("; ")}; nothing was sent")

/**
 * 422 rule-refused: the body satisfied the schema, but a rule of the resource
 * refused it. The client's form handling was right; [violations] give the
 * rule's own words. Not retried.
 */
class RuleRefused(said: String, val violations: List<String>) : ActRefused(said)

/**
 * The service refused access to a resource, for a read or an act. Each
 * message names the origin and the scheme ARTIE sent, never the credential.
 */
sealed class AccessRefused(message: String) : RuntimeException(message)

/**
 * 401: the service wants a credential ([schemes]), or refused the one ARTIE sent ([sent]). For an
 * act, [said] says it as every client does.
 */
class NotAuthenticated(val origin: String, val schemes: List<String>, val sent: String?, said: String? = null) :
    AccessRefused(
        said ?: if (sent == null) {
            "$origin asks for a credential (${schemes.joinToString()}); ARTIE sent none"
        } else {
            "$origin refused the $sent credential ARTIE sent"
        },
    )

/** 403: the service knows who is asking, and does not allow this. */
class Forbidden(val origin: String, val sent: String?) : AccessRefused(
    if (sent == null) "$origin does not allow this, and ARTIE sent no credential"
    else "$origin does not allow this with the $sent credential ARTIE sent",
)

/**
 * A read the server answered with an error ([code], the binding's own word for it): the resource at [url]
 * could not be read. A refusal, said as an act's is, so a run stops at its step instead of crashing.
 */
class ReadRefused(val url: String, val code: String) :
    RuntimeException("$url: read was refused ($code)") {
    /** Not found (HTTP 404 or 410, CoAP 4.04), as against any other error. */
    val notFound: Boolean get() = code in NOT_FOUND

    private companion object {
        val NOT_FOUND = setOf("404", "410", "4.04")
    }
}

/** Any other outcome; [code] is the binding's own word for it (HTTP: the status code). */
class UnexpectedResponse(said: String, val code: String) : ActRefused(said)
