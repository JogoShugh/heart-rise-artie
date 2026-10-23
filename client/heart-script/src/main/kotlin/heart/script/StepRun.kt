package heart.script

/**
 * A goal in steps, done step by step (spec/PLAN-GOAL-MARKDOWN.md). The runner keeps the structure: one step at a
 * time, in order; a step's sub-steps inside where its last opening act led, and back where it began when they are
 * done; the end, with the last step. The planner makes the moves each step allows, within the step's limit and the
 * run's. As for a goal in words, the run keeps the script its moves amount to as its [Run.source].
 */
internal class StepRun(val goal: Goal, val planner: Planner, val run: Run, val maxMoves: Int) {
    private val script = MoveLines(run)
    private val marks = StepMarks(run)
    private val moves = mutableListOf<String>()
    private val path = mutableListOf<String>()
    private val answers = mutableListOf<Pair<String, String>>()
    private val named = linkedMapOf<String, Representation>()
    private val done = mutableSetOf<Step>()
    private val inputs = goal.inputs.map { it.name }.toSet()
    private var now: Step? = null
    private var count = 0
    private var stepCount = 0

    fun start() {
        run.reasoning = true
        run.source = ""
        run.place = { script.place }
        run.planned = moves
        heart(goal.start) {
            // The person's words: a value the run sent that they hold is theirs.
            run.goal = (listOf(goal.context) + StepText.all(goal.steps)).joinToString("\n")
            path += held.address
            goal.steps.forEach { step(it, this, null) }
        }
    }

    /** [step], done at [here], in the block the act on line [opener] opened (null at the top). */
    private fun step(step: Step, here: Here, opener: Int?) {
        now = step
        stepCount = 0
        began = path.lastIndex
        when (step) {
            is Step.Intent -> intent(step, here, opener)
            is Step.Wait, is Step.Verify -> single(step, here, opener)
            is Step.Whenever, is Step.Until ->
                throw ScriptStopped(run.step, "\"${StepText.said(step)}\"", "\"a watch isn't run yet\"")
        }
        done += step
    }

    /** Where on the path the step now began: back goes no further. */
    private var began = 0

    /**
     * The moves of intent [step] at [here], until it is done ([DONE], it and its sub-steps), or until a back move
     * leaves this block: how many blocks to leave, this one first.
     */
    private fun intent(step: Step.Intent, here: Here, opener: Int?): Int {
        while (true) {
            val exits = when (val move = next(here)) {
                is Move.Act -> act(step, here, move, opener)
                is Move.Back -> back(move.to)
                is Move.Done -> {
                    moves += "done: ${fill(step.words)}"
                    step.name?.let { named[it] = here.held }
                    marks.marked(step, opener)
                    step.children.forEach { step(it, here, opener) }
                    DONE
                }
                else -> 0.also { other(move, step) }
            }
            if (exits > 0) return exits
        }
    }

    /**
     * How many blocks to leave to be back at [to], an address on the path or a word naming one ("the issue"); no
     * further back than where the step now began, or the planner is told so and nothing is left.
     */
    private fun back(to: String): Int {
        // A word names the resource whose own address ends in that noun's collection and an id.
        val noun = Regex.escape(to.removePrefix("the ").trim().lowercase())
        val named = Regex("/(${noun}s|$noun)/[^/]+/?$")
        val at = path.indexOfLast { it == to }.takeIf { it >= 0 } ?: path.indexOfLast { named.containsMatchIn(it) }
        if (at < began) {
            moves += "back: refused, only as far back as where this step began, ${path[began]}"
            return 0
        }
        moves += "back to $to"
        return path.lastIndex - at
    }

    /**
     * An act of [step], as a reason step. With a block, the step's moves after it are made where it led, until
     * the step is done ([DONE]) or a back move leaves the block: how many blocks are left to leave after it.
     * Refused before its block was entered, it changed nothing: the planner is told, and the capture keeps none
     * of it.
     */
    private fun act(step: Step.Intent, here: Here, move: Move.Act, opener: Int?): Int {
        val line = script.written(GoalText.act(move.words), opener)
        val words = fill(move.words)
        var exits = 0
        var entered = false
        val traced = run.trace.size
        try {
            // Every act opens a block: what it led to is held until the step is done, and then the run is back
            // where the step began, whether the act made a resource (a comment) or not.
            here.act(intent = words) {
                entered = true
                marks.acted(step, line)
                path += held.address
                // What was done, not only where it led: a follow is no act, whatever the words asked.
                val taken = run.trace.getOrNull(traced)
                val did = taken?.used?.let { (if (it.kind == "link") "followed " else "acted ") + it.relation + ", " }
                moves += "$words: ${did.orEmpty()}${taken?.outcome?.let { "$it, " }.orEmpty()}at ${held.address}"
                exits = intent(step, this, line)
                path.removeAt(path.lastIndex)
            }
        } catch (stopped: ScriptStopped) {
            val refused = stopped.refused?.takeUnless { entered } ?: throw stopped
            while (run.trace.size > traced) run.trace.removeAt(run.trace.lastIndex)
            moves += "$words: refused, ${refused.kind.words}: ${refused.detail}"
        }
        // The block just left was one of the blocks to leave.
        return if (exits == DONE) DONE else maxOf(exits - 1, 0)
    }

    /** A `Wait:` step, which takes one wait; a `Verify:` step, which takes one check. */
    private fun single(step: Step, here: Here, opener: Int?) {
        while (true) {
            val move = next(here)
            when {
                move is Move.Wait && step is Step.Wait -> if (script.waited(here, move, opener, moves)) return
                move is Move.Check && step is Step.Verify -> {
                    script.written(GoalText.check(move.facts), opener)
                    here.check(move.facts)
                    moves += "checked ${move.facts}"
                    return
                }
                else -> other(move, step)
            }
        }
    }

    /** A move no step needs the runner for: a question for the person, a stop; else one [step] refuses. */
    private fun other(move: Move, step: Step) {
        when (move) {
            is Move.Clarify -> {
                val asked = move.question
                val answer = if (run.interactive) run.prompt(asked, null) else stop("the planner asks: $asked")
                answers += asked to answer
            }
            is Move.Stop -> stop(move.why)
            else -> moves += StepText.refused(move, step)
        }
    }

    /** The planner's next move at [here], within the run's limit and the step's. */
    private fun next(here: Here): Move {
        val step = checkNotNull(now)
        val limit = (step as? Step.Intent)?.moves ?: goal.limits.step
        if (++count > maxMoves) stop("more than $maxMoves moves")
        if (++stepCount > limit) stop("line ${step.line}: more than $limit moves for one step")
        return planner.next(
            PlanView(
                goal.context, emptyList(), emptySet(), moves.toList(), path.toList(), here.held, answers.toList(),
                inputs.associateWith { run.value(it) },
                here.held.offered.sorted().associateWith { here.describe(it) },
                current = fill(StepText.said(step)),
                steps = StepText.tree(goal.steps, done, step),
                allowed = StepText.allowed(step),
                named = named.mapValues { it.value.address },
            ),
        ).also { move -> move.reason?.let { moves += "why: $it" } }
    }

    private fun fill(text: String) = StepText.fill(text, named, inputs, run::value)

    private fun stop(why: String): Nothing = throw ScriptStopped(run.step, "\"goal\"", "\"$why\"")

    private companion object {
        /** A step and its sub-steps, done: every block it opened is left. */
        const val DONE = Int.MAX_VALUE
    }
}

/**
 * A goal in steps' marks on the lines of the script its moves amount to ([Run.marks]), for the script made from
 * the run: a step's name on the act that led to what it named, and each act of a reasoned step.
 */
internal class StepMarks(private val run: Run) {
    /** The lines of each step's acts that were done. */
    private val acts = mutableMapOf<Step, MutableList<Int>>()

    fun acted(step: Step, line: Int) {
        acts.getOrPut(step) { mutableListOf() } += line
    }

    /** [step], done in the block of the act on line [at]: its marks put on the lines of its acts. */
    fun marked(step: Step.Intent, at: Int?) {
        val lines = acts[step].orEmpty()
        // What the step named is what the act whose block it was done in led to.
        step.name?.let { name -> (at?.takeIf { it in lines } ?: lines.lastOrNull())?.let { mark(it)["as"] = name } }
        // A reasoned step is kept in the person's words: the planner's say how this run decided it.
        if (step.reasoned) lines.forEach { mark(it)["reasoned"] = step.words }
    }

    private fun mark(line: Int) = run.marks.getOrPut(line) { mutableMapOf() }
}

/** The lines of the script a goal run's moves amount to, written as they are made; the next step stands for each. */
internal class MoveLines(private val run: Run) {
    private val lines = mutableListOf<String>()

    /** The line the next step stands for, and the line of the act whose block it is in. */
    var place: Pair<Int?, Int?> = null to null
        private set

    /** [text], a line of the script, written down; the next step stands for it. */
    fun written(text: String, opener: Int?): Int {
        lines += text
        run.source = lines.joinToString("\n")
        place = lines.size to opener
        return lines.size
    }

    /**
     * A wait until [here] offers the form [move] names, told in [moves]; true when it waited. A wait for no form
     * would never end, so it is refused, and the planner is told.
     */
    fun waited(here: Here, move: Move.Wait, opener: Int?, moves: MutableList<String>): Boolean {
        if (move.relation.isBlank()) {
            moves += "wait: refused, a wait names a form"
            return false
        }
        written(GoalText.until(move.relation, move.tries), opener)
        here.until(move.tries, condition = { offers(move.relation) })
        moves += "waited until ${move.relation} was offered"
        return true
    }
}
