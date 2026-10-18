package heart

import kotlinx.serialization.json.JsonObject

/**
 * One synced Layer 1 resource: domain state, the manifest of acts legal
 * on it right now, the ETag the next act must carry in If-Match, and its
 * links and embedded resources. [forms] is the link to the Layer 2
 * affordance set when the manifest arrived by link (L1, L2) rather than
 * inline (L0).
 */
class HeartRepresentation(
    val url: String,
    val state: JsonObject,
    /** The acts legal now, with the manifest document's explanation link and CURIEs. */
    val manifest: Manifest,
    val etag: String?,
    val forms: HeartLink? = null,
    /** Its links and the resources it embeds. */
    val related: Related = Related(),
) {
    val affordances: Map<String, AffordanceForm> get() = manifest.forms

    /** Every link but `curies`, by the relation the server wrote. */
    val linksByRel: Map<String, List<HeartLink>> get() = related.links

    /** The resources it embeds (SPEC §6.1), by the relation the server wrote. */
    val embeddedByRel: Map<String, List<HeartEmbedded>> get() = related.embedded

    /** The Layer 2 address, if the manifest arrived by link. */
    val formsLink: String? get() = forms?.href

    /** That link's Subresource Integrity value, if it had one. */
    val formsIntegrity: String? get() = forms?.integrity

    fun containsAffordance(rel: String): Boolean = affordance(rel) != null

    /** The form named [rel], as the server wrote its key or by the relation it expands to (SPEC §6.1). */
    fun affordance(rel: String): AffordanceForm? =
        affordances[rel] ?: affordances.values.firstOrNull { it.relation == rel }

    /** The links under [rel], as the server named it (a CURIE is matched as written). */
    fun links(rel: String): List<HeartLink> = linksByRel[rel].orEmpty()

    /** The resources embedded under [rel], as the server named it. */
    fun embedded(rel: String): List<HeartEmbedded> = embeddedByRel[rel].orEmpty()

    /** This representation with the manifest that came by link (L1, L2). */
    fun withManifest(fetched: Manifest): HeartRepresentation =
        HeartRepresentation(url, state, fetched, etag, forms, related)
}

/**
 * What a resource points at: its [links] (every one but `curies`) and the
 * resources it [embedded] (SPEC §6.1), each by the relation the server wrote.
 */
data class Related(
    val links: Map<String, List<HeartLink>> = emptyMap(),
    val embedded: Map<String, List<HeartEmbedded>> = emptyMap(),
)

/**
 * One embedded resource (SPEC §6.1): its address and title, from its self
 * link, its state, and what it points at. It carries no forms and no ETag:
 * to act on it, a client follows [href], which fetches it.
 */
data class HeartEmbedded(
    val href: String,
    val title: String?,
    val state: JsonObject,
    val related: Related = Related(),
) {
    /** The link a client follows to fetch it. */
    val self: HeartLink get() = HeartLink(href, title)
}

/**
 * One HAL link: [href] resolved against the representation's URL, its
 * title, and its Subresource Integrity value (HAL-SCHEMA-FORMS-HR Rule 2).
 */
data class HeartLink(val href: String, val title: String? = null, val integrity: String? = null)
