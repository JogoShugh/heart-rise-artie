package heart.cli.generate

/**
 * What a script checks after [step], an act, as the run saw it: each member of the resource's state its answer
 * changed. One the act sent ([sent], by field) is checked by the same value or reference the act sends; one it
 * changed to text or a truth, by that value. A number or a time it changed to may differ every run, so it isn't
 * checked unless the act sent it. An act that made a new resource, or left the run elsewhere, has nothing to compare.
 */
internal fun checks(capture: Capture, step: Step, sent: Map<String, Any?>): List<Map<String, Any?>> {
    val before = capture.step(step.number - 1)
        ?.takeIf { it.address != null && it.address == step.address && step.outcome != "new resource" }?.state
        ?: return emptyList()
    val facts = step.state.entries.mapNotNull { (member, after) ->
        val field = member.toString()
        val repeats = (after is String && !TIME.containsMatchIn(after)) || after is Boolean
        when {
            before[member] == after -> null
            field in sent -> mapOf("field" to field, "is" to sent[field])
            repeats -> mapOf("field" to field, "is" to after)
            else -> null
        }
    }
    return if (facts.isEmpty()) emptyList() else listOf(mapOf("check" to facts))
}

/** A value that begins as a date does: a time, which a later run won't repeat. */
private val TIME = Regex("""^\d{4}-\d{2}-\d{2}""")
