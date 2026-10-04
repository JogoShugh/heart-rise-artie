package heart

/** One HTTP request, as ARTIE builds it. Header names are matched case-insensitively. */
data class HeartRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/** One HTTP response. A header may repeat, so each name maps to all its values. */
data class HeartResponse(
    val status: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: String = "",
) {
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
