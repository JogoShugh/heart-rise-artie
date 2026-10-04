package heart

/** What became of one intent. Every rejection is a recorded event (SPEC §10.3), not a bad request. */
sealed interface Decision {
    /** The selection passed every check and was not sent (decide only). */
    data class Selected(val selection: Selection.Act) : Decision

    /** The selection passed every check and the act was accepted. */
    data class Acted(val selection: Selection.Act, val result: HeartRepresentation) : Decision

    /** The reasoner asked the user a question instead of guessing. Nothing was sent. */
    data class Asked(val question: String) : Decision

    /** The selection failed a deterministic check. Nothing was sent. */
    data class Rejected(val selection: Selection.Act, val reasons: List<String>) : Decision

    /** The selection was sent and the server refused it (412, 409, 422, ...). */
    data class Refused(val selection: Selection.Act, val refusal: ActRefused) : Decision
}

/**
 * The Sync-Reason-Act cycle (SPEC §6.8) with reasoning confined to one step.
 * Sync and Act are ArtieClient's deterministic code; the reasoner only
 * chooses among the offers it is shown, and its choice is checked against
 * the manifest and the schema before anything reaches the wire.
 */
class ArtieAgent(private val client: HeartRiseClient, private val reasoner: Reasoner) {

    /** Sync, reason, check. Never sends an act. */
    fun decide(intent: String, url: String): Decision {
        val synced = client.enter(url)
        val offers = synced.affordances.values.map { Offer(it.rel, it.schema) }
        return when (val selection = reasoner.reason(intent, synced.state, offers)) {
            is Selection.Clarify -> Decision.Asked(selection.question)
            is Selection.Act -> check(selection, synced)
        }
    }

    /** Sync, reason, check, then act. */
    fun handle(intent: String, url: String): Decision =
        when (val decided = decide(intent, url)) {
            is Decision.Selected -> act(decided.selection)
            else -> decided
        }

    private fun check(selection: Selection.Act, synced: HeartRepresentation): Decision {
        val form = synced.affordance(selection.affordance)
            ?: return Decision.Rejected(selection, listOf("${selection.affordance} is not in the current manifest"))
        val violations = SchemaCheck.violations(selection.fields, form.schema)
        return if (violations.isEmpty()) Decision.Selected(selection) else Decision.Rejected(selection, violations)
    }

    private fun act(selection: Selection.Act): Decision =
        try {
            Decision.Acted(selection, client.act(selection.affordance, selection.fields))
        } catch (refusal: ActRefused) {
            Decision.Refused(selection, refusal)
        }
}
