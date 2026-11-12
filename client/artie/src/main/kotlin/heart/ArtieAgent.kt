package heart

import kotlinx.serialization.json.JsonObject

/** What became of one intent. Every rejection is a recorded event (SPEC §10.3), not a bad request. */
sealed interface Decision {
    /** The selection passed every check and was not sent (decide only). */
    data class Selected(val selection: Selection) : Decision

    /** The selection passed every check and the act was accepted. */
    data class Acted(val selection: Selection.Act, val result: HeartRepresentation) : Decision

    /** The selection named an offered link, and ARTIE followed it. */
    data class Followed(val selection: Selection.Follow, val result: HeartRepresentation) : Decision

    /** The reasoner asked why an act is absent; [reason] is the server's, or null if it gave none. No act was sent. */
    data class Explained(val selection: Selection.Explain, val reason: String?) : Decision

    /** The reasoner asked the user a question instead of guessing. Nothing was sent. */
    data class Asked(val question: String) : Decision

    /** The selection failed a deterministic check. Nothing was sent. */
    data class Rejected(val selection: Selection, val reasons: List<String>) : Decision

    /** The selection was sent and the server refused it (412, 409, 422, ...). */
    data class Refused(val selection: Selection.Act, val refusal: ActRefused) : Decision

    /**
     * The selection was an act on a resource that changed since the step read it, the read its words were
     * decided from. Nothing was sent: the new ETag it would carry would never meet a 412.
     */
    data class Changed(val selection: Selection.Act) : Decision
}

/**
 * The Sync-Reason-Act cycle (SPEC §6.8) with reasoning confined to one step.
 * Sync, Act and Follow are ArtieClient's deterministic code; the reasoner
 * only chooses among the forms and links it is shown, and its choice is
 * checked against the representation (and a form's schema) before anything
 * reaches the wire.
 */
class ArtieAgent(private val client: HeartRiseClient, private val reasoner: Reasoner) {

    /**
     * Sync, reason, check. Never sends an act or follows a link. A reasoner
     * may first ask to describe offered forms; ARTIE fetches each ALPS
     * descriptor (SPEC §8.5) and asks again, with the detail shown, at most
     * once per form.
     */
    fun decide(intent: String, url: String, with: JsonObject = JsonObject(emptyMap())): Decision {
        val synced = client.enter(url)
        // HAL-SCHEMA-FORMS-HR Rule 8: the reasoner is shown each form's choices as a closed set.
        var offers = synced.affordances.values.map {
            Offer(it.rel, client.choices(it.rel)?.shownSchema() ?: it.schema, it.title, it.description)
        }
        while (true) {
            val chosen = reasoner.reason(intent, synced.state, offers, linkOffers(synced))
            // What the host adds to the act it chose, a reply's envelope (goal-rise.feature), checked with the rest.
            val selection = if (chosen is Selection.Act && with.isNotEmpty()) {
                chosen.copy(fields = JsonObject(chosen.fields + with))
            } else {
                chosen
            }
            if (selection !is Selection.Describe) return checked(selection, synced)
            val offer = offers.firstOrNull { it.name == selection.affordance && it.detail == null }
                ?: return describeRejected(selection, synced)
            val detail = client.describe(offer.name) ?: NO_DETAIL
            offers = offers.map { if (it === offer) it.copy(detail = detail) else it }
        }
    }

    private fun checked(selection: Selection, synced: HeartRepresentation): Decision = when (selection) {
        is Selection.Clarify -> Decision.Asked(selection.question)
        is Selection.Explain -> Decision.Explained(selection, client.explain(selection.affordance))
        is Selection.Act -> check(selection, synced)
        is Selection.Follow -> check(selection, synced)
        is Selection.Describe -> describeRejected(selection, synced)
    }

    private fun describeRejected(selection: Selection.Describe, synced: HeartRepresentation): Decision =
        if (synced.affordance(selection.affordance) == null) {
            Decision.Rejected(selection, listOf("${selection.affordance} is not in the current manifest"))
        } else {
            Decision.Rejected(selection, listOf("${selection.affordance} was already described"))
        }

    /**
     * Sync, reason, check, then act or follow. [readAs] is the ETag of the resource as the step read it, the read
     * its words were decided from: an act on it, changed since, is not sent ([Decision.Changed]).
     */
    fun handle(
        intent: String,
        url: String,
        readAs: String? = null,
        with: JsonObject = JsonObject(emptyMap()),
    ): Decision {
        val decided = decide(intent, url, with)
        return when (val selection = (decided as? Decision.Selected)?.selection) {
            is Selection.Act -> if (changedSince(readAs)) Decision.Changed(selection) else act(selection)
            is Selection.Follow -> Decision.Followed(selection, client.follow(selection.rel, selection.title))
            else -> decided
        }
    }

    /** Whether the resource synced is not the one read as [readAs]: someone changed it since. */
    private fun changedSince(readAs: String?): Boolean =
        readAs != null && client.current?.etag.let { it != null && it != readAs }

    /**
     * Every link but `self` and the affordance-set link, which are ARTIE's plumbing, not choices, and but
     * those ARTIE can't follow (a template, or a link out of HEART); then each embedded resource, a link the
     * reasoner may follow, with its state (SPEC §6.1).
     */
    private fun linkOffers(synced: HeartRepresentation): List<LinkOffer> =
        synced.linksByRel.filterKeys { it != "self" }.flatMap { (rel, links) ->
            links.filter { it != synced.forms && client.reaches(it) }.map { LinkOffer(rel, it.title) }
        } + synced.embeddedByRel.flatMap { (rel, resources) -> resources.map { LinkOffer(rel, it.title, it.state) } }

    private fun check(selection: Selection.Act, synced: HeartRepresentation): Decision {
        val form = synced.affordance(selection.affordance)
            ?: return Decision.Rejected(selection, listOf("${selection.affordance} is not in the current manifest"))
        val violations = SchemaCheck.violations(selection.fields, form.schema) +
            client.choices(selection.affordance)?.violations(selection.fields).orEmpty()
        return if (violations.isEmpty()) Decision.Selected(selection) else Decision.Rejected(selection, violations)
    }

    private fun check(selection: Selection.Follow, synced: HeartRepresentation): Decision =
        if (linkOffers(synced).any { it.rel == selection.rel && it.title == selection.title }) {
            Decision.Selected(selection)
        } else {
            Decision.Rejected(selection, listOf("no ${selection.rel} link titled ${selection.title} in ${synced.url}"))
        }

    private fun act(selection: Selection.Act): Decision =
        try {
            Decision.Acted(selection, client.act(selection.affordance, selection.fields))
        } catch (refusal: ActRefused) {
            Decision.Refused(selection, refusal)
        }

    private companion object {
        const val NO_DETAIL = "The server gives no further description of this action."
    }
}
