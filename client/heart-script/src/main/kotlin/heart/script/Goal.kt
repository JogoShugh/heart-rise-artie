package heart.script

/**
 * A goal in words (spec/PLAN-PLAIN-TEXT-RUN.md): where to [start], the [inputs] a run takes, and the [text] of what
 * to do, `$name` naming an input. Read from a goal file by the runner that has one; a planner does it move by move.
 */
class Goal(val start: String, val inputs: List<Declared>, val text: String) {
    /** The goal's sentences, in order: each line, then each sentence in a line, a quotation never split. */
    fun sentences(filled: String): List<String> = filled.lines().map { it.trim() }.filter { it.isNotEmpty() }
        .flatMap(::split)

    private fun split(line: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        line.forEachIndexed { i, c ->
            current.append(c)
            if (c == '"') quoted = !quoted
            val ends = !quoted && c in ".!?" && (i + 1 == line.length || line[i + 1] == ' ') &&
                line.getOrNull(i + 2)?.isUpperCase() != false
            if (ends) {
                out += current.toString().trim()
                current.clear()
            }
        }
        current.toString().trim().takeIf { it.isNotEmpty() }?.let { out += it }
        return out
    }
}

/**
 * What a planner sees before each move: the [goal] with its inputs filled, its [sentences] and which are [done], the
 * [moves] made so far in words, the addresses of the blocks it is in, outermost first ([path], the bookmark first),
 * the representation [held], and the [answers] a person gave to its questions.
 */
// A record names each of its parts once, by name; grouping them would only rename them.
@Suppress("LongParameterList")
class PlanView(
    val goal: String,
    val sentences: List<String>,
    val done: Set<Int>,
    val moves: List<String>,
    val path: List<String>,
    val held: Representation,
    val answers: List<Pair<String, String>>,
    /** The goal's inputs and their values: a move's words name each as `$name`, so a script made keeps it one. */
    val inputs: Map<String, String> = emptyMap(),
)

/** One move of a goal run. Sentences are numbered from 1. */
sealed interface Move {
    /**
     * An act, in [words] (`$name` an input), for sentence [sentence]: the client's reasoner chooses the form and
     * values from what is held. With [opens], the moves after it are made at what it led to, until a [Back].
     */
    class Act(val words: String, val sentence: Int?, val opens: Boolean = false) : Move

    /** Back to [to], an address on the path or a word naming one ("the issue"), closing the blocks since. */
    class Back(val to: String) : Move

    /** Read the held resource again until it offers [relation]. */
    class Wait(val relation: String, val tries: Int = 20) : Move

    /** Check [facts] of the held state: each member and the value it must have. */
    class Check(val facts: Map<String, Any?>) : Move

    /** Sentence [sentence] is done. */
    class Done(val sentence: Int) : Move

    /** The goal is done: every sentence must be. */
    object Finish : Move

    /** The goal can't be done, for [why]. */
    class Stop(val why: String) : Move

    /** A question for the person: the goal and what is offered leave the next move open. */
    class Clarify(val question: String) : Move
}

/** Chooses each move of a goal run (spec/PLAN-PLAIN-TEXT-RUN.md): a model, or a test's script of moves. */
fun interface Planner {
    fun next(view: PlanView): Move
}
