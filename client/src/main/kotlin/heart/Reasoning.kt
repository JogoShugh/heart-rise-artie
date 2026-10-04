package heart

import kotlinx.serialization.json.JsonObject

/**
 * What the Reason step is shown of one affordance: its name and its
 * schema. Deliberately no target and no method (SPEC §6.8): a reasoner
 * that is never shown a URI cannot hallucinate one.
 */
data class Offer(val name: String, val schema: JsonObject)

/** The Reason step's entire output space (SPEC §6.8). It has no field for a URI, method, header or precondition. */
sealed interface Selection {
    data class Act(val affordance: String, val fields: JsonObject) : Selection
    data class Clarify(val question: String) : Selection
}

/** L6 (SPEC §6.10.7): intent plus manifest in, one selection out. Optional; ARTIE works without one. */
fun interface Reasoner {
    fun reason(intent: String, state: JsonObject, offers: List<Offer>): Selection
}
