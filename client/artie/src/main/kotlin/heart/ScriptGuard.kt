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

    /**
     * Why the link [rel] titled [title] of [held] may not be followed, by the lock file; null when it may. A link's
     * target names one resource, and differs from one resource to the next: what is pinned is what a script relies
     * on, the variables the target leaves to fill (none for a plain link), checked in either mode.
     */
    fun linkRefusal(held: HeartRepresentation?, rel: String, title: String?): Answer.Refused? {
        val link = held?.links(rel)?.firstOrNull { it.title == title }
            ?: held?.embedded(rel)?.firstOrNull { it.title == title }?.self
            ?: return null
        val pinned = pins.links[rel]
        val offered = variables(link.href)
        return when {
            pinned == null -> refused("$rel is not in the script's lock file").takeIf { strict }
            pinned != offered -> refused("$rel changed: pinned '$pinned' to fill, offered '$offered'")
            else -> null
        }
    }

    /** Whether the lock file pins nothing for [rel]: a lenient run goes on, and says so. */
    fun unpinned(rel: String): Boolean = rel !in pins.forms

    /** Whether the lock file pins nothing for the link [rel]. */
    fun unpinnedLink(rel: String): Boolean = rel !in pins.links

    private fun refused(why: String) = Answer.Refused(Refusal.Guard, Side.CLIENT, why)

    companion object {
        /** The variables [target] leaves to fill, as its template writes them (`{/name}`); empty for a plain link. */
        fun variables(target: String): String = Regex("\\{[^}]*}").findAll(target).joinToString("") { it.value }
    }
}
