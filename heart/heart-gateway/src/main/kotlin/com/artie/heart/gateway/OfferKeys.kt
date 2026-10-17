package com.artie.heart.gateway

import com.artie.heart.Act
import com.artie.heart.ContentAddress
import com.artie.heart.ContentAddress.canonical
import com.artie.heart.Offers
import java.util.concurrent.ConcurrentHashMap

/**
 * An upstream resource type's offer keys: a key is the set of acts the
 * upstream allows a member now, met at run time, so the offers are open.
 * Where the upstream's signal says why an act is absent, the reasons are
 * part of the key, and the first answer with that key gives them.
 */
internal class OfferKeys(private val acts: List<Act>, private val read: (Answer) -> Reading) {
    /** Why the acts a key leaves out are absent, as the upstream's signal said the first time it sent that key. */
    private val reasons = ConcurrentHashMap<String, AbsenceReasons>()

    val offers = Offers<String>(
        keys = emptyList(),
        offered = { key -> key.substringBefore(REASONS).split(',').filter { it.isNotEmpty() } },
        whyAbsent = { key, act -> reasons[key]?.get(act) ?: NOT_NOW },
        open = true,
    )

    /** [answer] as a state, keyed by the acts its signal allows, in the order they are declared. */
    fun state(answer: Answer): UpstreamState {
        val reading = read(answer)
        val offered = acts.filter { it.name in reading.allowed.operations }.joinToString(",") { it.name }
        // Two answers that allow the same acts for different reasons explain their absences differently,
        // so the reasons are part of the key; equal reasons still share one affordance set.
        val said = reading.allowed.reasons
        val key = if (said.isEmpty()) offered else offered + REASONS + ContentAddress.address(canonical(said))
        reasons.putIfAbsent(key, said)
        return UpstreamState(reading.state.path("id").asLong(), reading.state, key)
    }

    private companion object {
        const val NOT_NOW = "The upstream doesn't allow it in the resource's current state."

        /** Separates the acts a key offers from the digest of the reasons it gives for the rest. */
        const val REASONS = "|"
    }
}
