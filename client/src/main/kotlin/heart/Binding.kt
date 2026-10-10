package heart

import java.net.URI

/** The requested rendering (SPEC §6.0): the dialect, with its profile, and the rung asked for. */
data class Rendering(val dialect: Dialect, val rung: Rung)

/**
 * A representation as received, in the core's terms (SPEC §6.0). The binding
 * fills it from its own protocol; the core never sees a status code or a header.
 */
class Received(
    /** The body exactly as received; integrity checks hash these bytes. */
    val bytes: ByteArray,
    val mediaType: String?,
    /** The profiles the representation says it conforms to. */
    val profiles: List<String>,
    /** The validator naming this representation of this state, if any. */
    val validator: String?,
    /** The rung the representation says it is at. Saying nothing means L0. */
    val applied: Rung,
    /** How the binding said it, for the event log (HTTP: the Preference-Applied value). */
    val appliedAs: String?,
) {
    /** The body as text. JSON is UTF-8 (RFC 8259). */
    val body: String get() = String(bytes, Charsets.UTF_8)
}

/**
 * What came of a request, in the core's terms (SPEC §6.0). Each refusal asks for
 * different handling (SPEC §6.3). [code] is the binding's own word for it (HTTP:
 * the status code), and is only reported, never acted on.
 */
sealed class Outcome(val code: String, val problemType: String?) {
    class Done(val received: Received, code: String) : Outcome(code, null)
    class PreconditionRequired(code: String, problemType: String?) : Outcome(code, problemType)
    class Stale(code: String, problemType: String?) : Outcome(code, problemType)
    class NotOffered(code: String, problemType: String?) : Outcome(code, problemType)
    class Invalid(val violations: List<String>, code: String, problemType: String?) : Outcome(code, problemType)
    class Failed(code: String, problemType: String?) : Outcome(code, problemType)

    /** The representation, or an error naming what the binding answered instead. */
    fun received(url: String): Received =
        (this as? Done)?.received ?: error("$url could not be read: the binding answered $code")
}

/**
 * One protocol's way to interact with the resources its URIs identify (SPEC §6.0).
 * The core asks for a read, a fetch or an act; the binding carries it in its own
 * protocol and answers in core terms.
 */
interface Binding {
    /** Reads a Layer 1 representation, which is never fresh (SPEC §6.3). */
    fun read(url: String, rendering: Rendering): Outcome

    /**
     * Reads any other resource in one of [accept]'s media types. [integrity] is
     * the value the link carried, for a binding that checks it below its cache.
     */
    fun fetch(url: String, accept: List<String>, integrity: String? = null): Outcome

    /** A state-changing act, conditional on its validator (SPEC §6.2). */
    fun act(act: Act, rendering: Rendering): Outcome
}

/** An act as the core builds it from a form (SPEC §6.0): the form's method, its target and content type. */
data class Act(val url: String, val method: String, val contentType: String, val body: String, val validator: String)

/** A URI whose scheme the client has no binding for (SPEC §6.0 rule 4). */
class NoBindingException(url: String, scheme: String?) :
    IllegalArgumentException("no binding for the scheme $scheme: $url was refused, and nothing was sent")

/** The bindings a client has, by URI scheme (SPEC §6.0 rule 2). Schemes compare case-insensitively. */
class Bindings(byScheme: Map<String, Binding>) {
    private val byScheme = byScheme.mapKeys { it.key.lowercase() }

    fun forUri(url: String): Binding {
        val scheme = runCatching { URI(url).scheme }.getOrNull()?.lowercase()
        return byScheme[scheme] ?: throw NoBindingException(url, scheme)
    }
}
