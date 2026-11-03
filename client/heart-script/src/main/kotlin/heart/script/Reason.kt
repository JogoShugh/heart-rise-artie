package heart.script

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * `act(rel, …)` as step [step]: the form [rel] with [fields], refused here, by the client, before anything is sent,
 * when the manifest doesn't offer it. The step keeps what the act is for, in words: [said].
 */
internal fun Here.acted(step: Int, rel: String, fields: Map<String, Any?>, said: Said, block: (Here.() -> Unit)?) {
    if (rel !in held.offered) {
        val reason = navigator.why(rel) ?: "no reason given"
        val refusal = Answer.Refused(Refusal.NotOffered, Side.CLIENT, reason)
        run.record(actAsked(rel, fields), "refused", held, refusal)
        run.trace.lastOrNull()?.said(said)
        if (fallsBack(said, refusal)) return reasoned(step, said, block)
        throw refused(step, "\"$rel\"", refusal)
    }
    val answer = navigator.act(rel, fields)
    // The step just traced is this act's: it keeps what the act is for.
    run.trace.lastOrNull()?.said(said)
    if (answer is Answer.Refused && fallsBack(said, answer)) return reasoned(step, said, block)
    go(step, "\"$rel\"", answer, block)
}

/**
 * Whether an act that carries words, [said], a person's or a model's inferred, refused as [refused], falls back to
 * them: the run is set to (`on-change: intent`) and may reason, and the client refused it before sending anything
 * because the form it was written for no longer holds: no longer offered, not the one pinned, or the values no
 * longer fit it.
 */
private fun Here.fallsBack(said: Said, refused: Answer.Refused): Boolean =
    run.fallsBack && run.reasoning && said.words != null && refused.by == Side.CLIENT && refused.kind in CHANGED_FORM

/** The client's refusals that say the form an act was written for no longer holds. */
private val CHANGED_FORM = setOf(Refusal.NotOffered, Refusal.Guard, Refusal.InvalidInput, Refusal.NotAChoice)

/**
 * What a step is for, in words: a person's, as a goal or a script wrote them ([intent]), or a model's, inferred while
 * it ran ([inferred]), with the person's words they serve, if any ([fromIntent]). Either an intent or inferred words
 * may be reasoned on; the trace keeps all three apart.
 */
class Said(val intent: String? = null, val inferred: String? = null, val fromIntent: String? = null) {
    /** The words to reason on: a person's, else a model's. */
    val words: String? get() = intent ?: inferred
}

/**
 * `act(intent = …)` with no relation, as step [step]: hand [intent] to the client's reasoner, which chooses one
 * selection from what is offered here: a form with its fields, or a link. The client checks the choice and
 * makes it; the script then holds what it led to, as after any act or follow. With a [block], visit it and
 * return. A run must allow it (`--reasoner`). The same words on the same resource, offering the same, are the
 * choice made before in this run, made again with no model.
 */
internal fun Here.reasoned(step: Int, said: Said, block: (Here.() -> Unit)?) {
    val intent = checkNotNull(said.words)
    if (!run.reasoning) {
        throw ScriptStopped(step, "\"intent\"", "\"act(intent = …) needs a reasoner, and this run has none\"")
    }
    // A goal's planner keeps its own decisions, and its capture each move as it was reasoned: only a script's.
    val key = Triple(intent, held.address, held.offered).takeIf { run.goal == null }
    key?.let { run.reasoned[it] }?.let { chosen -> return again(step, chosen, said, block) }
    val before = run.trace.size
    val decided = decided(step, intent)
    val reasoned = decided.reasoned
    // A step that took acts toward its words is no one choice to make again: it is decided again each time.
    val kept = key?.takeIf { decided.toward == 0 && reasoned.answer !is Answer.Refused }
    if (kept != null) reasoned.chosen?.let { run.reasoned[kept] = it }
    val answer = reasoned.answer
    // A model's words are kept as inferred on the reason step the client traced, so a script made from the run
    // says whose they were; a client that traces none is left as it is.
    if (said.intent == null && run.trace.size > before) run.trace[before].inferred = intent
    go(step, "\"intent\"", answer, block)
}

/** What an intent step came to: the reasoner's last decision, and how many acts it made toward the words first. */
private class Decided(val reasoned: Reasoned, val toward: Int)

/**
 * [intent] handed to the reasoner, as step [step]. Its act refused as stale, someone changed the resource since it
 * chose (SPEC §6.2: never sent again, the intent derived again against the new state): the held resource is read
 * again, and the reasoner asked again, told what it chose, why that was refused, and what changed since, at most
 * [STALE_DECISIONS] times in all. What the words ask not offered here, the reasoner is asked again, told the
 * server's reason, for an act offered now toward it; made, the step holds what it led to and asks the words again
 * there, at most [TOWARD_ACTS] acts toward them. A reasoner that makes no act toward them, asking or explaining
 * again, ends the step with that.
 */
private fun Here.decided(step: Int, intent: String): Decided {
    var reasoned = navigator.reason(intent)
    var stale = 1
    var toward = 0
    while (true) {
        val refused = reasoned.answer as? Answer.Refused
        when {
            refused?.kind == Refusal.Stale && stale < STALE_DECISIONS -> {
                val chose = held.state
                sync()
                reasoned = told(intent, toldAgain(reasoned.chosen, refused, chose, held.state))
                stale++
            }
            // A goal's planner is told a refusal and takes the steps toward it itself: only a script's.
            refused?.kind == Refusal.NotOffered && toward < TOWARD_ACTS && run.goal == null -> {
                val first = told(intent, towardIt(refused))
                if (first.answer is Answer.Refused) return Decided(first, toward)
                held = arrived(step, "\"intent\"", first.answer)
                toward++
                reasoned = navigator.reason(intent)
            }
            else -> return Decided(reasoned, toward)
        }
    }
}

/** [intent] handed to the reasoner, told [more] besides the words, after anything the run tells it already. */
private fun Here.told(intent: String, more: String): Reasoned {
    val earlier = run.besides
    run.besides = listOfNotNull(earlier, more).joinToString(" ")
    return try {
        navigator.reason(intent)
    } finally {
        run.besides = earlier
    }
}

/** What the reasoner is told when what the words ask is not offered here, [refused] with the server's reason. */
private fun towardIt(refused: Answer.Refused): String =
    "What these words ask is not offered here now: ${refused.detail} If an act offered here now is a step toward " +
        "it, choose that act; these words are asked again after it. If none is, ask."

/** What the reasoner is told, deciding again: its [chosen] refused as [refused], and each member changed since. */
private fun toldAgain(chosen: Choice?, refused: Answer.Refused, was: JsonObject, now: JsonObject): String {
    val choice = chosen?.let { c ->
        "Your choice here, ${c.relation}" + if (c.fields.isEmpty()) "" else " with ${fieldsText(c.fields)}"
    } ?: "Your choice here"
    val changed = (was.keys + now.keys).filter { was[it] != now[it] }
        .joinToString("; ") { "$it was ${was[it] ?: "absent"}, now ${now[it] ?: "absent"}" }
        .ifEmpty { "nothing in its state changed" }
    return "$choice, was refused: ${refused.detail} Since then: $changed. Decide again from what is there now. " +
        "If these words would undo or write over that change, do not act: ask, naming it."
}

/** [fields] as JSON, as the reasoner chose them. */
private fun fieldsText(fields: Map<String, Any?>): String = JsonObject(
    fields.mapValues { (_, v) ->
        when (v) {
            null -> JsonNull
            is Number -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            else -> JsonPrimitive(v.toString())
        }
    },
).toString()

/** How many times an intent step decides, its act refused as stale each time, before the refusal stops it. */
private const val STALE_DECISIONS = 3

/** How many acts an intent step makes toward what its words ask, when that is not offered, before it stops. */
private const val TOWARD_ACTS = 3

/** [chosen], what the reasoner chose before on these words here, made again as step [step], with no model. */
private fun Here.again(step: Int, chosen: Choice, said: Said, block: (Here.() -> Unit)?) {
    if (!chosen.follow) return acted(step, chosen.relation, chosen.fields, said, block)
    val answer = navigator.follow(chosen.relation, chosen.title)
    run.trace.lastOrNull()?.said(said)
    go(step, "\"${chosen.relation}\"", answer, block)
}

/** What a reason step asked, for its trace: the words as said, and the choice made as its values. */
internal fun reasonAsked(intent: String, chosen: Choice?): TraceStep.Asked {
    val title = chosen?.title?.let { mapOf("title" to it) }.orEmpty()
    val values = chosen?.let { mapOf("relation" to it.relation) + it.fields + title }.orEmpty()
    return TraceStep.Asked(intent, values, mode = "intent")
}
