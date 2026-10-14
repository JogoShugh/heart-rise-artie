package heart

/** One HTTP request, as ARTIE builds it. Header names are matched case-insensitively. */
data class HeartRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    /** The Subresource Integrity value of the link being followed, if it had one (never sent on the wire). */
    val integrity: String? = null,
) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** As the data class would print it, with any Authorization value hidden. */
    override fun toString(): String {
        val shown = headers.mapValues { (name, value) ->
            if (name.equals(AUTHORIZATION, ignoreCase = true)) "****" else value
        }
        return "HeartRequest(method=$method, url=$url, headers=$shown, body=$body, integrity=$integrity)"
    }

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}

/**
 * One HTTP response. A header may repeat, so each name maps to all its values.
 * [bytes] are the body exactly as received; integrity checks hash these, never
 * the decoded text.
 */
class HeartResponse(
    val status: Int,
    val headers: Map<String, List<String>>,
    val bytes: ByteArray,
    /** The scheme of the credential ARTIE sent for this response, if any (never received on the wire). */
    val credentialSent: String? = null,
) {
    constructor(status: Int, headers: Map<String, List<String>> = emptyMap(), body: String = "") :
        this(status, headers, body.toByteArray(Charsets.UTF_8))

    /** The body as text. JSON is UTF-8 (RFC 8259). */
    val body: String get() = String(bytes, Charsets.UTF_8)

    /** This response, recording that a credential of [scheme] was sent for it. */
    fun sentWith(scheme: String) = HeartResponse(status, headers, bytes, scheme)

    fun header(name: String): String? =
        headers.entries.filter { it.key.equals(name, ignoreCase = true) }
            .flatMap { it.value }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
}

/** ARTIE's only way to the network (guarded by ArchitectureBoundaryTest). */
interface Transport {
    fun send(request: HeartRequest): HeartResponse
}
