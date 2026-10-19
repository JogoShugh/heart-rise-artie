package heart

import heart.script.Answer
import heart.script.Pins
import heart.script.Refusal
import heart.script.Side

/**
 * A script's lock file, held to before ARTIE sends an act (spec/PLAN-SCRIPT-GENERATOR.md, "Guards";
 * spec/ideas/replay-guards-lenient-strict.md). Lenient: the form about to be used must have its pinned integrity,
 * in whatever set holds it now; a relation the lock doesn't pin goes on, marked unpinned. [strict]: the set must
 * also be one recorded for it, and an unpinned relation is refused. Bytes are checked against their links'
 * integrity as always (Rule 2), whatever the mode.
 */
class ScriptGuard(private val pins: Pins, val strict: Boolean) {
    val mode: String get() = if (strict) "strict" else "lenient"

    /** Why the form [rel] of [held] may not be used, by the lock file; null when it may. */
    fun refusal(held: HeartRepresentation?, rel: String): Answer.Refused? {
        // A form not offered at all is the client's own refusal, as without a lock file.
        val form = held?.affordance(rel) ?: return null
        val pin = pins.forms[rel]
        val set = held.formsIntegrity
        return when {
            pin == null -> refused("$rel is not in the script's lock file").takeIf { strict }
            pin.integrity != null && pin.integrity != form.integrity ->
                refused("$rel changed: pinned ${pin.integrity}, offered ${form.integrity}")
            strict && pin.sets.isNotEmpty() && set !in pin.sets ->
                refused("$rel is offered in another set: pinned ${pin.sets.joinToString(" or ")}, offered $set")
            else -> null
        }
    }

    /** Whether the lock file pins nothing for [rel]: a lenient run goes on, and says so. */
    fun unpinned(rel: String): Boolean = rel !in pins.forms

    private fun refused(why: String) = Answer.Refused(Refusal.Guard, Side.CLIENT, why)
}
