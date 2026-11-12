package heart.script

/**
 * `If {name.member}, …` on what a step named, checked with no model (goal-rise.feature). A capture walks one branch:
 * the one taken is the planner's, kept as the run made it; the other, `Otherwise, …`, is written so ARTIE can do it
 * as it stands, `at {resource}, act REL with FIELD = "value", …`, and is kept as written, so a run that takes it needs
 * no model either.
 */
internal class Branch(
    private val run: Run,
    private val script: MoveLines,
    private val marks: StepMarks,
    private val fill: (String) -> String,
    /** What a step named, by its name; null for an input or nothing. */
    private val named: (String) -> Representation?,
) {
    /** Whether [step] is an If on a name's member: then it is done here, by [intent] or as its Otherwise says. */
    fun done(step: Step.Intent, here: Here, opener: Int?, intent: (Step.Intent, Here, Int?) -> Unit): Boolean {
        val condition = step.condition?.let { CONDITION.matchEntire(it.trim()) } ?: return false
        val (name, member) = condition.destructured
        val line = script.written("given(${step.condition}) {", opener)
        marks.put(line, "given", listOf(mapOf("at" to name, "field" to member, "is" to true)))
        val otherwise = step.otherwise?.let { said(it.words) }.orEmpty()
        marks.put(line, "otherwise", otherwise)
        val holds = fill("{$name.$member}") == "true"
        val asked = TraceStep.Asked("given", mapOf("condition" to step.condition))
        run.record(asked, if (holds) "held" else "not", here.held)
        run.trace.last().opens = true
        if (holds) {
            intent(step, here, line)
        } else {
            // The names its steps read are kept by those names, as the script made from the run keeps them.
            NAMES.findAll(otherwise.toString()).map { it.groupValues[1] }.forEach { kept ->
                named(kept)?.let { run.kept[kept] = Run.Kept(it.address, State(it.state), it.validator) }
            }
            here.made(otherwise, null)
        }
        return true
    }

    /** `at {x}, act REL with FIELD = value, …` as a script's steps: `enter` a party, or `at` a resource named. */
    private fun said(words: String): List<Map<String, Any?>> {
        val act = ACT.matchEntire(words.trim().removeSuffix(".")) ?: stop("Otherwise says at {resource}, act REL")
        val (at, rel, with) = act.destructured
        val fields = FIELD.findAll(with).associate { it.groupValues[1] to slots(it.groupValues[2]) }
        val acting = linkedMapOf<String, Any?>("act" to rel).also { if (fields.isNotEmpty()) it["fields"] = fields }
        if (at.isBlank()) return listOf(acting)
        val where = if (named(at) != null) "at" to "\${$at.@self}" else "enter" to "\${$at}"
        return listOf(linkedMapOf(where, "steps" to listOf(acting)))
    }

    /** A value as written, quotes off, each name read again: `{book}` an input, `{smoke.failed}` a member. */
    private fun slots(value: String) = SLOT.replace(value.trim().removeSurrounding("\"")) { slot ->
        val (name, member) = slot.destructured
        if (member.isEmpty() && named(name) != null) "\${$name.@self}" else "\${$name$member}"
    }

    private companion object {
        val CONDITION = Regex("""^\{(\w+)\.([\w.:-]+)}$""")
        val ACT = Regex("""(?i)^(?:at\s+\{(\w+)}\s*,\s*)?act\s+(\S+?)(?:\s+with\s+(.+))?$""")
        val FIELD = Regex("""(\w+)\s*=\s*("[^"]*"|\{[^}]+}|[^,]+)""")
        val SLOT = Regex("""\{(\w+)((?:\.[\w:-]+)*)}""")
        val NAMES = Regex("""\$\{(\w+)\.@self}""")
    }
}
