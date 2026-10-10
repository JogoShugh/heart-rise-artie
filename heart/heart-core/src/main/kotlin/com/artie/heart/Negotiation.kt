package com.artie.heart

/** SPEC §6.1: the parent profile. Never negotiated alone; listed beside the concrete one. */
const val HEART_RISE_PROFILE = "https://github.com/jogoshugh/heart-rise"

/**
 * The two base dialects (SPEC §6.1). Each pairs a media type, which
 * `Accept` selects, with the atomic composite profile URI that
 * `Link: rel="profile"` announces. [etagTag] is the dialect's part of the
 * Layer 1 ETag (see StateETag).
 */
enum class Dialect(val mediaType: String, val profile: String, val etagTag: String) {
    HALFORMS("application/prs.hal-forms+json", "$HEART_RISE_PROFILE/hal-forms", "hf"),
    JSONSCHEMA("application/hal+json", "$HEART_RISE_PROFILE/hal-schema-forms", "hsf"),
}

/** 406: Accept names only media types this server cannot produce. */
class NotAcceptableException(message: String) : RuntimeException(message)

/**
 * Picks the dialect for a Layer 1 response, from the values a client sent.
 * Every binding carries them its own way (HTTP headers, CoAP options), and
 * every binding decides the same way.
 *
 * 1. `Accept` decides when it names one of the two dialect media types:
 *    highest q wins, ties go to the earlier entry.
 * 2. When `Accept` is absent or only wildcards, `Accept-Profile` decides:
 *    the highest-q profile URI this server serves.
 * 3. Otherwise the default dialect. A profile the server cannot serve
 *    falls back to it rather than failing (connegp §7.3.2).
 *
 * Only an `Accept` with neither a dialect nor a wildcard is a 406 (§6.4).
 */
object Negotiation {
    val DEFAULT_DIALECT = Dialect.HALFORMS
    const val ACCEPT_PROFILE = "Accept-Profile"
    const val JSON_HOME = "application/json-home"

    private data class Ranked(val value: String, val q: Double, val index: Int)

    fun dialect(accept: String?, acceptProfile: String?): Dialect {
        // Media types compare case-insensitively (RFC 9110 §8.3.1); profile URIs do not.
        val accepted = ranked(accept.orEmpty().lowercase()).filter { it.q > 0 }
        val named = accepted.mapNotNull { r -> Dialect.entries.find { it.mediaType == r.value }?.let { it to r } }
        val acceptLeavesItOpen = accepted.isEmpty() || accepted.any { it.value == "*/*" || it.value == "application/*" }
        return when {
            named.isNotEmpty() -> named.best().first
            acceptLeavesItOpen -> byProfile(acceptProfile) ?: DEFAULT_DIALECT
            else -> throw NotAcceptableException("Accept names no dialect this server produces: ${Dialect.entries}")
        }
    }

    /**
     * SPEC §6.11.1: true when `Accept` ranks the JSON Home media type above
     * both dialects, so the service index answers as a home document.
     */
    fun wantsJsonHome(accept: String?): Boolean {
        val accepted = ranked(accept.orEmpty().lowercase()).filter { it.q > 0 }
        val home = accepted.firstOrNull { it.value == JSON_HOME } ?: return false
        return accepted.none { r ->
            Dialect.entries.any { it.mediaType == r.value } && (r.q > home.q || r.q == home.q && r.index < home.index)
        }
    }

    /** The profile links of a Layer 1 response in [dialect]: the concrete
     *  profile, then the parent for hierarchy discovery (§6.1), as a Link header value. */
    fun profileLinks(dialect: Dialect): String =
        "<${dialect.profile}>; rel=\"profile\", <$HEART_RISE_PROFILE>; rel=\"profile\""

    private fun byProfile(acceptProfile: String?): Dialect? =
        ranked(acceptProfile.orEmpty())
            .filter { it.q > 0 }
            .mapNotNull { r -> Dialect.entries.find { "<${it.profile}>" == r.value }?.let { it to r } }
            .takeIf { it.isNotEmpty() }
            ?.best()?.first

    private fun List<Pair<Dialect, Ranked>>.best(): Pair<Dialect, Ranked> =
        sortedWith(compareByDescending<Pair<Dialect, Ranked>> { it.second.q }.thenBy { it.second.index }).first()

    /** `value;q=0.5, value2` -> values with their q (default 1). Other parameters are dropped. */
    private fun ranked(header: String): List<Ranked> =
        header.split(',').map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { index, entry ->
            val parts = entry.split(';').map { it.trim() }
            val q = parts.drop(1).firstOrNull { it.startsWith("q=") }?.removePrefix("q=")?.toDoubleOrNull() ?: 1.0
            Ranked(parts.first(), q, index)
        }
}
