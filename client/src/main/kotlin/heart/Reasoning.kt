package heart

import kotlinx.serialization.json.JsonObject

/**
 * What the Reason step is shown of one affordance: its name (a link
 * relation), its title and description, its schema, and, once asked for,
 * the [detail] of its ALPS descriptor. Deliberately no target and no method
 * (SPEC §6.8): a reasoner that is never shown a URI cannot hallucinate one.
 */
data class Offer(
    val name: String,
    val schema: JsonObject,
    val title: String? = null,
    val description: String? = null,
    val detail: String? = null,
)

/**
 * What the Reason step is shown of one link: its relation and title.
 * Deliberately no href, for the same reason as [Offer]: ARTIE follows the
 * link the reasoner names; the reasoner never sees or builds a URI.
 */
data class LinkOffer(val rel: String, val title: String?)

/** The Reason step's entire output space (SPEC §6.8). It has no field for a URI, method, header or precondition. */
sealed interface Selection {
    data class Act(val affordance: String, val fields: JsonObject) : Selection
    data class Follow(val rel: String, val title: String?) : Selection

    /** Ask for an offered form's ALPS descriptor before choosing (SPEC §6.8). Deterministic code fetches it. */
    data class Describe(val affordance: String) : Selection

    /** Ask why an act is not offered (SPEC §6.12). ARTIE answers from the server's explanation; nothing is sent. */
    data class Explain(val affordance: String) : Selection
    data class Clarify(val question: String) : Selection
}

/** L6 (SPEC §6.10.7): intent plus manifest and links in, one selection out. Optional; ARTIE works without one. */
fun interface Reasoner {
    fun reason(intent: String, state: JsonObject, offers: List<Offer>, links: List<LinkOffer>): Selection
}
