package heart

import heart.script.Answer
import heart.script.FormPin
import heart.script.Pins
import heart.script.Refusal
import heart.script.Side
import kotlinx.serialization.json.JsonObject

/**
 * A script's lock file, held to before ARTIE sends an act (spec/PLAN-SCRIPT-GENERATOR.md, "Guards";
 * spec/ideas/replay-guards-lenient-strict.md). Lenient: the form about to be used must have its pinned integrity,
 * in whatever set holds it now; a relation the lock doesn't pin goes on, marked unpinned. With [values]
 * (`on-change: values`), a lenient run also goes past a changed form that kept its pinned method, when what the
 * script sends fits its new schema. [strict]: the set must also be one recorded for it, and an unpinned relation
 * is refused. Bytes are checked against their links' integrity as always (Rule 2), whatever the mode.
 */
class ScriptGuard(
    private val pins: Pins,
    val strict: Boolean,
    val values: Boolean = false,
    /** Whether look-then-leap's one look compares every form the server declares, not only those pinned. */
    private val everything: Boolean = false,
) {
    val mode: String get() = if (strict) "strict" else "lenient"

    /**
     * Look-then-leap's one look (spec/ideas/off-the-rails.md), at the [entry]'s manifest of the server's forms:
     * whether the run leaves the rails, and what it says of it.
     */
    fun look(entry: HeartRepresentation?): Pair<Boolean, String> = LookThenLeap(pins, everything).look(entry)

    /** Why the form [rel] of [held] may not be used to send [sent], by the lock file; null when it may. */
    fun refusal(held: HeartRepresentation?, rel: String, sent: JsonObject = NOTHING): Answer.Refused? {
        // A form not offered at all is the client's own refusal, as without a lock file.
        val form = held?.affordance(rel) ?: return null
        val pin = pins.forms[rel]
        val set = held.formsIntegrity
        return when {
            pin == null -> refused("$rel is not in the script's lock file").takeIf { strict }
            pin.integrity != null && pin.integrity != form.integrity -> changed(rel, pin, form, sent)
            strict && pin.sets.isNotEmpty() && set !in pin.sets ->
                refused("$rel is offered in another set: pinned ${pin.sets.joinToString(" or ")}, offered $set")
            else -> null
        }
    }

    /** Why the changed [form] [rel], pinned as [pin], may not be used to send [sent]; null when its values hold it. */
    private fun changed(rel: String, pin: FormPin, form: AffordanceForm, sent: JsonObject): Answer.Refused? {
        val misfits = SchemaCheck.violations(sent, form.schema)
        return when {
            strict || !values -> refused("$rel changed: pinned ${pin.integrity}, offered ${form.integrity}")
            pin.method == null -> refused("$rel changed, and the lock file doesn't pin its method")
            pin.method != form.method ->
                refused("$rel changed its method: pinned ${pin.method}, offered ${form.method}")
            misfits.isNotEmpty() ->
                refused("$rel changed, and what the script sends doesn't fit it: ${misfits.joinToString("; ")}")
            else -> null
        }
    }

    /**
     * What holds the pinned form [rel] of [held] to the lock file, when it may be used to send [sent]: `set` when it
     * is offered in a set recorded for it, `form` when only its own integrity is (its set new, or none), `values`
     * when it changed and what is sent fits it; null otherwise.
     */
    fun heldBy(held: HeartRepresentation?, rel: String, sent: JsonObject = NOTHING): String? {
        val pin = pins.forms[rel]
        val form = held?.affordance(rel)
        val passed = pin != null && form != null && refusal(held, rel, sent) == null
        return when {
            !passed -> null
            pin?.integrity != null && pin.integrity != form?.integrity -> "values"
            held?.formsIntegrity in pin?.sets.orEmpty() -> "set"
            else -> "form"
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

    /**
     * What a lenient run goes past when it uses the form [rel] of [held] unchanged, but offered in a set the lock
     * file didn't record for it (spec/ideas/replay-guards-lenient-strict.md): both sets; null when nothing is.
     */
    fun warning(held: HeartRepresentation?, rel: String): String? {
        val form = held?.affordance(rel)
        val pin = pins.forms[rel]
        val set = held?.formsIntegrity
        val changed = form != null && pin != null && pin.sets.isNotEmpty() && set !in pin.sets
        return "the set holding $rel changed: pinned ${pin?.sets?.joinToString(" or ")}, offered $set"
            .takeIf { !strict && changed && pin?.integrity == form?.integrity }
    }

    /**
     * Whether the strict guard refuses [rel] of [held] only for its set: its form as pinned, offered in a set the
     * lock file didn't record. A server still catching up may yet offer it in a recorded one.
     */
    fun waiting(held: HeartRepresentation?, rel: String): Boolean {
        val pin = pins.forms[rel]
        val form = held?.affordance(rel)
        return strict && pin != null && form != null && pin.sets.isNotEmpty() &&
            held.formsIntegrity !in pin.sets && pin.integrity == form.integrity
    }

    /** Whether the lock file pins nothing for [rel]: a lenient run goes on, and says so. */
    fun unpinned(rel: String): Boolean = rel !in pins.forms

    /** Whether the lock file pins nothing for the link [rel]. */
    fun unpinnedLink(rel: String): Boolean = rel !in pins.links

    private fun refused(why: String) = Answer.Refused(Refusal.Guard, Side.CLIENT, why)

    companion object {
        private val NOTHING = JsonObject(emptyMap())

        /** The variables [target] leaves to fill, as its template writes them (`{/name}`); empty for a plain link. */
        fun variables(target: String): String = Regex("\\{[^}]*}").findAll(target).joinToString("") { it.value }
    }
}
