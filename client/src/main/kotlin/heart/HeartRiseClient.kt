package heart

import kotlinx.serialization.json.JsonObject

interface HeartRiseClient {
    /** The last synced resource, including after a refusal's re-sync. Null before enter(). */
    val current: HeartRepresentation?

    /** The rung [current] arrived at, from its Preference-Applied header (SPEC §6.11.1). Null before enter(). */
    val appliedRung: Rung?

    fun enter(url: String): HeartRepresentation
    fun act(rel: String, payload: JsonObject): HeartRepresentation

    /** Follows the [rel] link in the current representation whose title is [title]. */
    fun follow(rel: String, title: String): HeartRepresentation
}
