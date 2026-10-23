package heart.script

/**
 * A goal (spec/PLAN-PLAIN-TEXT-RUN.md, spec/PLAN-GOAL-MARKDOWN.md): where to [start], the [inputs] a run takes, and
 * what to do: the [text] of a goal in words, or [steps], with the [context] the person wrote above them. A name in
 * braces, `{name}` (or `$name`), is an input or a resource a step named. Read from a goal file by the runner that
 * has one; a planner does it move by move, within its [limits].
 */
// A record names each of its parts once, by name.
@Suppress("LongParameterList")
class Goal(
    val start: String,
    val inputs: List<Declared>,
    val text: String,
    val context: String = "",
    val steps: List<Step> = emptyList(),
    val limits: Limits = Limits(),
    /** The run's manners by name (`alongside-others`), as the front matter gives them; null for none. */
    val manners: String? = null,
    /** `## Working alongside others`: the manners in words; null when the goal has no such section. */
    val alongside: Alongside? = null,
    /** `## Done when`: the outcomes the goal ends with, a fact each, each at its line. */
    val done: List<Step.Verify> = emptyList(),
) {
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

/** A goal's manners in words, its section's paragraphs then each manner, the section's heading at [line]. */
class Alongside(val words: List<String>, val line: Int)

/** What a `Once` step waits for, its [condition], and when it gives up: [giveUp], or null for no limit. */
class Once(val condition: String, val giveUp: GiveUp? = null)

/** `Or give up after …`, on its own [line] under a `Once` step: the wait ends [after] that long. */
class GiveUp(val after: kotlin.time.Duration, val line: Int)

/** `Otherwise, …`, on its own [line] under a step: what to do, in [words], when the step's words can't be done. */
class Otherwise(val words: String, val line: Int)

/** How far a goal run may go: [moves] in all, and [step] for one step of a goal in steps. */
class Limits(val moves: Int = 50, val step: Int = 10)

/** One step of a goal in steps, at its [line] of the goal file. */
sealed interface Step {
    val line: Int

    /**
     * An intent: one or more acts that do its [words]; then its [children], inside what it led to. [name], given
     * by `as name`, names the resource it led to; [moves] is its own limit; [reasoned], that the model decides it
     * on every run of the script made from the run.
     */
    // A record names each of its parts once, by name.
    @Suppress("LongParameterList")
    class Intent(
        val words: String,
        override val line: Int,
        val name: String? = null,
        val moves: Int? = null,
        val reasoned: Boolean = false,
        val children: List<Step> = emptyList(),
        /** `Once …,`: what it waits for before its words, and when it gives up; null when it waits for nothing. */
        val once: Once? = null,
        /** `Otherwise, …`: what to do instead when the step's words can't be done. */
        val otherwise: Otherwise? = null,
    ) : Step

    /**
     * `Whenever …`: the steps under it, its [children], done again on each change that matches [match], the
     * changed resource named [name]. A goal with one keeps watching, until its [Until].
     */
    class Whenever(
        val match: String,
        override val line: Int,
        val name: String? = null,
        val children: List<Step> = emptyList(),
    ) : Step

    /** `Until …`: when the watch ends: once [condition] holds. */
    class Until(val condition: String, override val line: Int) : Step

    /** Read the held resource again until [condition] holds. */
    class Wait(val condition: String, override val line: Int) : Step

    /** Each of [facts] must hold of the held resource now. */
    class Verify(val facts: List<String>, override val line: Int) : Step
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
    /** What each form [held] offers is for, as its relation's profile says; null where it says nothing. */
    val described: Map<String, String?> = emptyMap(),
    /** A goal in steps: the step now, its names filled, and its kind's marker if it has one (`Wait: …`). */
    val current: String = "",
    /** A goal in steps: every step, a line each, indented as nested, done ones and the one now marked. */
    val steps: List<String> = emptyList(),
    /** A goal in steps: the moves the step now allows, by kind: act, done, wait, check, clarify, stop. */
    val allowed: Set<String> = emptySet(),
    /** A goal in steps: each resource a step named, by its name, at its address. */
    val named: Map<String, String> = emptyMap(),
)

/** One move of a goal run. Sentences are numbered from 1. */
sealed interface Move {
    /** Why the planner made it, in its own words, when it said; kept with the move in the run's capture. */
    val reason: String? get() = null

    /**
     * An act, in [words] (`$name` an input), for sentence [sentence]: the client's reasoner chooses the form and
     * values from what is held. With [opens], the moves after it are made at what it led to, until a [Back].
     */
    class Act(
        val words: String,
        val sentence: Int?,
        val opens: Boolean = false,
        override val reason: String? = null,
    ) : Move

    /** Back to [to], an address on the path or a word naming one ("the issue"), closing the blocks since. */
    class Back(val to: String, override val reason: String? = null) : Move

    /** Read the held resource again until it offers [relation]. */
    class Wait(val relation: String, val tries: Int = 20, override val reason: String? = null) : Move

    /** Check [facts] of the held state: each member and the value it must have. */
    class Check(val facts: Map<String, Any?>, override val reason: String? = null) : Move

    /** Sentence [sentence] is done. */
    class Done(val sentence: Int, override val reason: String? = null) : Move

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
