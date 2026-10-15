package com.artie.heart.gateway

import com.artie.heart.HEART_RISE_PROFILE
import com.artie.heart.Layers
import java.util.concurrent.ConcurrentHashMap

/** What the upstream allows a resource now: the paths of its acts, and why each absent relation is absent. */
class Allowed(val paths: List<String>, val reasons: Map<String, String>)

/**
 * The best per-state signal an upstream can send: each plain JSON answer
 * links its state's HEART affordance set (rel `.../rels/forms`). A set is
 * immutable and named by its content, so each is read once, with the
 * explanation of the acts it leaves out, and kept.
 */
class AffordanceLinks(private val upstream: Upstream) {
    private val read = ConcurrentHashMap<String, Allowed>()

    /** What [answer]'s state allows, or a refusal to guess when the upstream sends no link. */
    fun of(answer: Answer): Allowed {
        val href = checkNotNull(answer.links[FORMS]) {
            "the upstream's answer links no affordance set, and the gateway has no other signal of what it allows"
        }
        return read.computeIfAbsent(href.toString()) { allowedBy(it) }
    }

    private fun allowedBy(href: String): Allowed {
        val set = upstream.get(href).body
        val paths = set.path("_forms").map { form ->
            form.path("_links").path("target").path("href").asText().substringAfterLast(ACTIONS)
        }
        val explanation = set.path("_links").path(Layers.UNAVAILABLE_REL).path("href").asText(null)
        val reasons = explanation?.let { upstream.get(it).body.path("unavailable").toList() }.orEmpty()
            .associate { it.path("rel").asText().substringAfter('#') to it.path("reason").asText() }
        return Allowed(paths, reasons)
    }

    private companion object {
        const val FORMS = "$HEART_RISE_PROFILE/rels/forms"
        const val ACTIONS = "/actions/"
    }
}
