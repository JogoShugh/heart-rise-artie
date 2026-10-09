package heart

import kotlinx.serialization.json.JsonObject

interface HeartRiseClient {
    /** The last synced resource, including after a refusal's re-sync. Null before enter(). */
    val current: HeartRepresentation?

    /** The rung [current] arrived at, from its Preference-Applied header (SPEC §6.11.1). Null before enter(). */
    val appliedRung: Rung?

    /**
     * Reads the service index at [url] as a JSON Home document (SPEC §6.11.1)
     * and from then on asks for the best dialect and rung it offers, up to
     * the client's own. Without a home document the client's settings stand.
     */
    fun discover(url: String)

    fun enter(url: String): HeartRepresentation
    fun act(rel: String, payload: JsonObject): HeartRepresentation

    /** Follows the [rel] link in the current representation whose title is [title]. */
    fun follow(rel: String, title: String): HeartRepresentation
}
