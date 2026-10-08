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
) {
    constructor(status: Int, headers: Map<String, List<String>> = emptyMap(), body: String = "") :
        this(status, headers, body.toByteArray(Charsets.UTF_8))

    /** The body as text. JSON is UTF-8 (RFC 8259). */
    val body: String get() = String(bytes, Charsets.UTF_8)

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
