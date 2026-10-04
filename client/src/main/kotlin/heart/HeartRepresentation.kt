package heart

import kotlinx.serialization.json.JsonObject

/**
 * One synced Layer 1 resource: domain state, the manifest of acts legal
 * on it right now, and the ETag the next act must carry in If-Match.
 */
class HeartRepresentation(
    val url: String,
    val state: JsonObject,
    val affordances: Map<String, AffordanceForm>,
    val etag: String?,
) {
    fun containsAffordance(rel: String): Boolean = affordances.containsKey(rel)
    fun affordance(rel: String): AffordanceForm? = affordances[rel]
}
