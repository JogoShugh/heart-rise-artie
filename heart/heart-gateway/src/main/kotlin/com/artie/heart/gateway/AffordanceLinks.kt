package com.artie.heart.gateway

import com.artie.heart.HEART_RISE_PROFILE
import com.artie.heart.Layers
import java.util.concurrent.ConcurrentHashMap

/** What an upstream affordance set holds: the action paths its forms target, and reasons by relation id. */
private class ReadSet(val paths: List<String>, val reasons: Map<String, Reason>)

/**
 * The best per-state signal an upstream can send: each plain JSON answer
 * links its state's HEART affordance set (rel `.../rels/forms`). A set is
 * immutable and named by its content, so each is read once, with the
 * explanation of the acts it leaves out, and kept.
 */
class AffordanceLinks(private val upstream: Upstream) {
    private val read = ConcurrentHashMap<String, ReadSet>()

    /**
     * The signal for one resource type: a form's target names an operation by
     * its action path ([byPath]), and a reason names it by its relation id ([byRelation]).
     */
    fun signal(byPath: Map<String, OperationId>, byRelation: Map<String, OperationId>) = AllowedNow { _, links ->
        val href = checkNotNull(links[FORMS]) {
            "the upstream's answer links no affordance set, and the gateway has no other signal of what it allows"
        }
        val set = read.computeIfAbsent(href.toString()) { setAt(it) }
        Allowed(
            set.paths.mapNotNull { byPath[it] }.toSet(),
            set.reasons.mapNotNull { (relation, reason) -> byRelation[relation]?.let { it to reason } }.toMap(),
        )
    }

    private fun setAt(href: String): ReadSet {
        val set = upstream.get(href).body
        val paths = set.path("_forms").map { form ->
            form.path("_links").path("target").path("href").asText().substringAfterLast(ACTIONS)
        }
        val explanation = set.path("_links").path(Layers.UNAVAILABLE_REL).path("href").asText(null)
        val reasons = explanation?.let { upstream.get(it).body.path("unavailable").toList() }.orEmpty()
            .associate { it.path("rel").asText().substringAfter('#') to it.path("reason").asText() }
        return ReadSet(paths, reasons)
    }

    private companion object {
        const val FORMS = "$HEART_RISE_PROFILE/rels/forms"
        const val ACTIONS = "/actions/"
    }
}
