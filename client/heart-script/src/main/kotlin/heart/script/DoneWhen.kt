package heart.script

/**
 * A goal's Done when facts about what a Whenever answered ("Every alert is assigned to the grower", the Whenever
 * named `as alert`): decided once, by the planner, on the first member that watch answered, and checked on every one
 * of them. It holds on all, or the run stops saying on which not. [members] are the watch's members by Whenever;
 * [ask] asks the planner for a move at a member, the moves it may make and the fact as told; [fill] puts the inputs
 * and names into words; [moves] is what the planner is told was done.
 */
internal class DoneWhen(
    private val members: Map<Step.Whenever, Set<String>>,
    private val ask: (Here, Set<String>, String) -> Move,
    private val fill: (String) -> String,
    private val moves: MutableList<String>,
) {
    /** Whether [fact] is about what a Whenever answered, and so checked here, from [start]. */
    fun every(fact: Step.Verify, start: Here): Boolean {
        val said = fill(fact.facts.joinToString("; "))
        val watched = members.entries.firstOrNull { (step, _) -> step.name?.let { names(it, said) } == true }
        watched?.let { (step, answered) ->
            val name = checkNotNull(step.name)
            val each = synchronized(answered) { answered.toList() }
            if (each.isEmpty()) {
                start.note("Not checked: $said: the watch answered no $name")
            } else {
                decide(said, name, each, start)
            }
        }
        return watched != null
    }

    /** The planner's check of [said], made on the first of [answered], then on each; or done, saying why not. */
    private fun decide(said: String, name: String, answered: List<String>, start: Here) {
        val first = at(start, answered.first())
        val told = "done when: $said: the check every $name the watch answered passes, made on this one, then on " +
            "each of them; done, saying why, if no member of one $name says it"
        var over = false
        while (!over) {
            over = when (val move = ask(first, ALLOWED, told)) {
                is Move.Check -> true.also { checked(said, name, move.facts, answered, first) }
                is Move.Done -> true.also { first.note("Not checked: $said" + (move.reason?.let { ": $it" } ?: "")) }
                is Move.Stop -> throw GoalStopped(first.run.step, "\"goal\"", "\"${move.why}\"")
                else -> false.also { moves += "${StepText.kind(move)}: refused, here only check, done, stop" }
            }
        }
    }

    /** [facts] checked on each of [answered]; the run stops, saying on which not, unless they hold on all. */
    private fun checked(said: String, name: String, facts: Map<String, Any?>, answered: List<String>, here: Here) {
        val failed = answered.filterNot { holds(facts, at(here, it).held) }
        val held = "holds on ${answered.size - failed.size} of ${answered.size}"
        moves += "done when: $said: checked ${element(facts)} on every $name the watch answered: $held"
        here.check(failed.isEmpty()) { "$said: $held; not on ${failed.joinToString(", ")}" }
    }

    /** The resource at [address], read again. */
    private fun at(here: Here, address: String) =
        Here(here.navigator, here.run, arrived(++here.run.step, "\"done when\"", here.navigator.recall(address)))

    private companion object {
        val ALLOWED = setOf("check", "done", "stop")

        /** Whether [said] names [name], or many of it, as a word. */
        fun names(name: String, said: String) =
            Regex("""\b${Regex.escape(name)}s?\b""", RegexOption.IGNORE_CASE).containsMatchIn(said)
    }
}
