package heart.script

/** The most moves a goal run makes before it stops, as a loop. */
const val MAX_MOVES = 40

/**
 * Does [goal] in the current run, move by move as [planner] chooses (spec/PLAN-PLAIN-TEXT-RUN.md). Each act is a
 * reason step: the client's reasoner chooses its form and values from what is held, as for `act(intent = …)`. The
 * run keeps, as its [Run.source], the script the moves amount to, one line a move, so its capture reads as a
 * reasoned script's, nested as the planner nested it, and `artie generate` writes the script from it.
 */
fun runGoal(goal: Goal, planner: Planner, maxMoves: Int = MAX_MOVES) {
    val run = Run.current
    goal.inputs.forEach { run.declare(it) }
    GoalRun(goal, planner, run, maxMoves).start()
}

private class GoalRun(val goal: Goal, val planner: Planner, val run: Run, val maxMoves: Int) {
    private val lines = mutableListOf<String>()
    private var place: Pair<Int?, Int?> = null to null
    private val done = sortedSetOf<Int>()
    private val moves = mutableListOf<String>()
    private val path = mutableListOf<String>()
    private val answers = mutableListOf<Pair<String, String>>()
    private var count = 0
    private var filled = ""
    private var sentences = emptyList<String>()

    fun start() {
        run.reasoning = true
        run.source = ""
        run.place = { place }
        heart(goal.start) {
            filled = GoalText.fill(goal.text, goal.inputs.map { it.name }.toSet(), run::value)
            sentences = goal.sentences(filled)
            path += held.address
            plan(this, null)
        }
    }

    /**
     * Moves at [here], in the block opened by the act on line [opener] (null at the top), until the planner leaves
     * it. What it returns is how many blocks to leave, this one first; [FINISHED] when the goal is done.
     */
    private fun plan(here: Here, opener: Int?): Int {
        while (true) {
            if (++count > maxMoves) stop("more than $maxMoves moves")
            val exits = made(planner.next(view(here)), here, opener)
            if (exits > 0) return exits
        }
    }

    /** [move], made at [here]: how many blocks it leaves. */
    private fun made(move: Move, here: Here, opener: Int?): Int = when (move) {
        is Move.Act -> act(here, move, opener)
        is Move.Back -> back(move.to)
        is Move.Wait -> 0.also {
            written(GoalText.until(move.relation, move.tries), opener)
            here.until(move.tries, condition = { offers(move.relation) })
            moves += "waited until ${move.relation} was offered"
        }
        is Move.Check -> 0.also {
            written(GoalText.check(move.facts), opener)
            here.check(move.facts)
            moves += "checked ${move.facts}"
        }
        is Move.Done -> 0.also { done += move.sentence }
        is Move.Finish -> finish()
        is Move.Stop -> stop(move.why)
        is Move.Clarify -> 0.also { answers += move.question to ask(move.question) }
    }

    /** An act as a reason step; with a block, the moves after it are made where it led, until the planner leaves. */
    private fun act(here: Here, move: Move.Act, opener: Int?): Int {
        val line = written(GoalText.act(move.words), opener)
        val words = GoalText.fill(move.words, goal.inputs.map { it.name }.toSet(), run::value)
        var exits = 0
        if (move.opens) {
            here.act(intent = words) {
                path += held.address
                moves += "$words: at ${held.address}"
                exits = plan(this, line)
                path.removeAt(path.lastIndex)
            }
        } else {
            here.act(intent = words)
            moves += "$words: now at ${here.held.address}"
        }
        // The block just left was one of the blocks to leave.
        return if (exits == FINISHED) FINISHED else maxOf(exits - 1, 0)
    }

    /** How many blocks to leave to be back at [to]: an address on the path, or a word naming one ("the issue"). */
    private fun back(to: String): Int {
        // A word names the resource whose own address ends in that noun's collection and an id: the issue is
        // .../issues/10, never .../issues/10/comments/1.
        val noun = Regex.escape(to.removePrefix("the ").trim().lowercase())
        val named = Regex("/(${noun}s|$noun)/[^/]+/?$")
        val at = path.indexOfLast { it == to }.takeIf { it >= 0 } ?: path.indexOfLast { named.containsMatchIn(it) }
        moves += "back to $to"
        return if (at < 0) 1 else path.lastIndex - at
    }

    private fun finish(): Int {
        val undone = (1..sentences.size).filter { it !in done }
        if (undone.isNotEmpty()) stop("not every sentence is done: ${undone.joinToString()}")
        return FINISHED
    }

    private fun ask(question: String): String =
        if (run.interactive) run.prompt(question, null) else stop("the planner asks: $question")

    private fun stop(why: String): Nothing = throw ScriptStopped(run.step, "\"goal\"", "\"$why\"")

    /** [text], a line of the script the moves amount to, written down; the next step stands for it. */
    private fun written(text: String, opener: Int?): Int {
        lines += text
        run.source = lines.joinToString("\n")
        place = lines.size to opener
        return lines.size
    }

    private fun view(here: Here) = PlanView(
        filled, sentences, done.toSet(), moves.toList(), path.toList(), here.held, answers.toList(),
        goal.inputs.associate { it.name to run.value(it.name) },
    )

    companion object {
        const val FINISHED = Int.MAX_VALUE
    }
}

/** The lines of the script a goal run's moves amount to, and its inputs filled in its words. */
internal object GoalText {
    private val SLOT = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}|\$([A-Za-z_][A-Za-z0-9_]*)""")

    fun act(words: String) = "act(intent = \"${escaped(words)}\")"

    fun until(relation: String, tries: Int) = "until(tries = $tries) { offers(\"${escaped(relation)}\") }"

    fun check(facts: Map<String, Any?>) =
        "check(" + facts.entries.joinToString { (member, value) -> "\"${escaped(member)}\" to ${literal(value)}" } + ")"

    /** [text] with each `$name` or `${name}` of one of [inputs] put as its [value]. */
    fun fill(text: String, inputs: Set<String>, value: (String) -> String): String = SLOT.replace(text) { slot ->
        val name = slot.groupValues[1].ifEmpty { slot.groupValues[2] }
        if (name in inputs) value(name) else slot.value
    }

    private fun escaped(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun literal(value: Any?): String = when (value) {
        null -> "null"
        is String -> "\"${escaped(value)}\""
        else -> value.toString()
    }
}
