package heart.cli.goal

/**
 * The backticks at a step's end (spec/PLAN-GOAL-MARKDOWN.md): `as NAME`, the resource it led to named; `moves N`,
 * its own limit; `reasoned`, the model deciding it on every run of the script made from the run. Each at most once;
 * the keywords ignore case, the name keeps it.
 */
internal class Marks(val name: String?, val moves: Int?, val reasoned: Boolean) {
    companion object {
        /** The marks [marks] give; anything else, or one given twice, is told to [refuse]. */
        fun of(marks: List<String>, refuse: (String) -> Nothing): Marks {
            var name: String? = null
            var moves: Int? = null
            var reasoned = false
            marks.map { it.trim() }.forEach { mark ->
                val named = AS.matchEntire(mark)
                val limit = MOVES.matchEntire(mark)
                when {
                    named != null -> name = once(name, "as", refuse) { named.groupValues[1] }
                    limit != null -> moves = once(moves, "moves", refuse) {
                        limit.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
                            ?: refuse("moves is a whole number above 0")
                    }
                    mark.equals("reasoned", ignoreCase = true) ->
                        reasoned = once(true.takeIf { reasoned }, "reasoned", refuse) { true }
                    else -> refuse("`$mark` is not as NAME, moves N or reasoned")
                }
            }
            return Marks(name, moves, reasoned)
        }

        private fun <T> once(was: T?, mark: String, refuse: (String) -> Nothing, value: () -> T): T =
            if (was != null) refuse("$mark is given twice") else value()

        private val AS = Regex("""(?i)as\s+([A-Za-z_][A-Za-z0-9_]*)""")
        private val MOVES = Regex("""(?i)moves\s+(\S+)""")
    }
}
