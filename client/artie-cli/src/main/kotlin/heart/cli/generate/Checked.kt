package heart.cli.generate

/** One member checked after an act: one the act [sent], read as the act sends it; else the [value] it changed to. */
internal class Checked(val field: String, val sent: Boolean, val value: Any?)

/**
 * What a script checks after [step], an act, as the run saw it. An act works on the resource the run holds: one whose
 * form's method changes it, and that made no new resource, answers with that resource's next state, with the
 * members it had, so each member the answer changed from the state held before (the step before's) is checked. One
 * the act sent (among [sent]) is checked by the same value or reference the act sends; one it changed to text or a
 * truth, by that value. A number or a time it changed to may differ every run, so it isn't checked unless the act
 * sent it. A form whose method only reads (a search) leads elsewhere, and has nothing to compare: no address is.
 * Both writers check the same members: the Kotlin script and the script held as data.
 */
internal fun checked(capture: Capture, step: Step, sent: Set<String>): List<Checked> {
    val changes = step.chose["method"]?.toString()?.uppercase() in CHANGING && step.outcome != "new resource"
    // The held resource's next state has the members it had: an answer with others is another resource's.
    val before = capture.step(step.number - 1)?.state
        ?.takeIf { held -> changes && step.state.keys.all { it in held.keys } } ?: return emptyList()
    return step.state.entries.mapNotNull { (member, after) ->
        val field = member.toString()
        val repeats = (after is String && !TIME.containsMatchIn(after) && !HASH.matches(after)) || after is Boolean
        when {
            before[member] == after -> null
            field in sent -> Checked(field, true, null)
            repeats -> Checked(field, false, after)
            else -> null
        }
    }
}

/** The methods a form changes the resource it is on with. */
private val CHANGING = setOf("POST", "PUT", "PATCH", "DELETE")

/** A value that begins as a date does: a time, which a later run won't repeat. */
private val TIME = Regex("""^\d{4}-\d{2}-\d{2}""")

/** A value made of hex digits, a digit among them, such as a commit's hash: made anew each run. */
private val HASH = Regex("""^(?=[0-9a-f]*\d)[0-9a-f]{7,}$""")
