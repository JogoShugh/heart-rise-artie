package heart

import kotlinx.serialization.json.JsonObject

/**
 * One synced Layer 1 resource: domain state, the manifest of acts legal
 * on it right now, and the ETag the next act must carry in If-Match.
 * [formsLink] is the Layer 2 address when the manifest arrived by link
 * (L1, L2) rather than inline (L0).
 */
class HeartRepresentation(
    val url: String,
    val state: JsonObject,
    val affordances: Map<String, AffordanceForm>,
    val etag: String?,
    val formsLink: String? = null,
) {
    fun containsAffordance(rel: String): Boolean = affordances.containsKey(rel)
    fun affordance(rel: String): AffordanceForm? = affordances[rel]

    fun withAffordances(resolved: Map<String, AffordanceForm>): HeartRepresentation =
        HeartRepresentation(url, state, resolved, etag, formsLink)
}
