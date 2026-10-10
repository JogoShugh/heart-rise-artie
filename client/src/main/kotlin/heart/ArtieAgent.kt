package heart

/** What became of one intent. Every rejection is a recorded event (SPEC §10.3), not a bad request. */
sealed interface Decision {
    /** The selection passed every check and was not sent (decide only). */
    data class Selected(val selection: Selection) : Decision

    /** The selection passed every check and the act was accepted. */
    data class Acted(val selection: Selection.Act, val result: HeartRepresentation) : Decision

    /** The selection named an offered link, and ARTIE followed it. */
    data class Followed(val selection: Selection.Follow, val result: HeartRepresentation) : Decision

    /** The reasoner asked the user a question instead of guessing. Nothing was sent. */
    data class Asked(val question: String) : Decision

    /** The selection failed a deterministic check. Nothing was sent. */
    data class Rejected(val selection: Selection, val reasons: List<String>) : Decision

    /** The selection was sent and the server refused it (412, 409, 422, ...). */
    data class Refused(val selection: Selection.Act, val refusal: ActRefused) : Decision
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
    fun decide(intent: String, url: String): Decision {
        val synced = client.enter(url)
        var offers = synced.affordances.values.map { Offer(it.rel, it.schema, it.title, it.description) }
        while (true) {
            val selection = reasoner.reason(intent, synced.state, offers, linkOffers(synced))
            if (selection !is Selection.Describe) return checked(selection, synced)
            val offer = offers.firstOrNull { it.name == selection.affordance && it.detail == null }
                ?: return describeRejected(selection, synced)
            val detail = client.describe(offer.name) ?: NO_DETAIL
            offers = offers.map { if (it === offer) it.copy(detail = detail) else it }
        }
    }

    private fun checked(selection: Selection, synced: HeartRepresentation): Decision = when (selection) {
        is Selection.Clarify -> Decision.Asked(selection.question)
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

    /** Sync, reason, check, then act or follow. */
    fun handle(intent: String, url: String): Decision {
        val decided = decide(intent, url)
        return when (val selection = (decided as? Decision.Selected)?.selection) {
            is Selection.Act -> act(selection)
            is Selection.Follow -> Decision.Followed(selection, client.follow(selection.rel, selection.title))
            else -> decided
        }
    }

    /** Every link but `self` and the affordance-set link, which are ARTIE's plumbing, not choices. */
    private fun linkOffers(synced: HeartRepresentation): List<LinkOffer> =
        synced.linksByRel.filterKeys { it != "self" }.flatMap { (rel, links) ->
            links.filter { it != synced.forms }.map { LinkOffer(rel, it.title) }
        }

    private fun check(selection: Selection.Act, synced: HeartRepresentation): Decision {
        val form = synced.affordance(selection.affordance)
            ?: return Decision.Rejected(selection, listOf("${selection.affordance} is not in the current manifest"))
        val violations = SchemaCheck.violations(selection.fields, form.schema)
        return if (violations.isEmpty()) Decision.Selected(selection) else Decision.Rejected(selection, violations)
    }

    private fun check(selection: Selection.Follow, synced: HeartRepresentation): Decision =
        if (LinkOffer(selection.rel, selection.title) in linkOffers(synced)) {
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
