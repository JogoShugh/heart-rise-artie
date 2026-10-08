package heart

import kotlinx.serialization.json.JsonObject

/**
 * One synced Layer 1 resource: domain state, the manifest of acts legal
 * on it right now, the ETag the next act must carry in If-Match, and its
 * links. [forms] is the link to the Layer 2 affordance set when the
 * manifest arrived by link (L1, L2) rather than inline (L0).
 */
class HeartRepresentation(
    val url: String,
    val state: JsonObject,
    val affordances: Map<String, AffordanceForm>,
    val etag: String?,
    val forms: HeartLink? = null,
    private val linksByRel: Map<String, List<HeartLink>> = emptyMap(),
) {
    /** The Layer 2 address, if the manifest arrived by link. */
    val formsLink: String? get() = forms?.href

    /** That link's Subresource Integrity value, if it had one. */
    val formsIntegrity: String? get() = forms?.integrity

    fun containsAffordance(rel: String): Boolean = affordances.containsKey(rel)
    fun affordance(rel: String): AffordanceForm? = affordances[rel]

    /** The links under [rel], as the server named it (a CURIE is matched as written). */
    fun links(rel: String): List<HeartLink> = linksByRel[rel].orEmpty()

    fun withAffordances(resolved: Map<String, AffordanceForm>): HeartRepresentation =
        HeartRepresentation(url, state, resolved, etag, forms, linksByRel)
}

/**
 * One HAL link: [href] resolved against the representation's URL, its
 * title, and its Subresource Integrity value (HAL-SCHEMA-FORMS-HR Rule 2).
 */
data class HeartLink(val href: String, val title: String? = null, val integrity: String? = null)
