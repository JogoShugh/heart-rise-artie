package heart.script

/**
 * A `Verify:` step whose every fact is `MEMBER is [not] VALUE` (goal-scenarios.feature): checked as written, with no
 * model, each name read where the step names it ([fill]); and kept as written on the check's line of the [script],
 * so the script made from the run reads each name again on every run, whatever its value was on this one.
 */
internal class NamedFacts(
    private val script: MoveLines,
    private val marks: StepMarks,
    private val fill: (String) -> String,
    private val moves: MutableList<String>,
) {
    /** Whether [step]'s facts were all said so, and checked here; false: the planner checks them. */
    fun verified(step: Step.Verify, here: Here, opener: Int?): Boolean {
        // Only facts that each read a name: anything else said in words is the planner's to check.
        val facts = step.facts.map { said ->
            FACT.matchEntire(said.trim())?.takeIf { SLOT in it.value } ?: return false
        }
        val line = script.written("check(${step.facts.joinToString("; ")})", opener)
        marks.facts(line, facts.map { fact ->
            val (member, not, value) = fact.destructured
            mapOf("field" to pointer(member), (if (not.isBlank()) "is" else "is-not") to slots(value.trim()))
        })
        here.checkFacts(facts.map { fact ->
            val (member, not, value) = fact.destructured
            val filled = fill(value.trim()).removeSurrounding("\"")
            Fact(pointer(member), if (not.isBlank()) equalTo(filled) else notEqualTo(filled))
        })
        moves += "checked: ${fill(step.facts.joinToString("; "))}"
        return true
    }

    internal companion object {
        /** `body is not {read.body}`, `author is {author}`, `reported.problem is missing-pages`. */
        val FACT = Regex("""^([A-Za-z_][\w.:-]*)\s+is\s+(not\s+)?(.+)$""")

        /** A name in braces, `{read.body}`, `{author}`. */
        val SLOT = Regex("""\{([A-Za-z_][\w.:-]*)}""")

        /** `reported.problem`, a member by its path, as a JSON Pointer; a plain member as itself. */
        fun pointer(member: String) = if ('.' in member) "/" + member.replace('.', '/') else member

        /** A value as written, each name in braces the reference that reads it again: `${read.body}`. */
        fun slots(value: String) = SLOT.replace(value.removeSurrounding("\"")) { "\${${it.groupValues[1]}}" }
    }
}
