package heart.script

import kotlin.time.Duration.Companion.minutes

/**
 * A goal's Whenever, run as a watch (spec/PLAN-WATCH-GOALS.md): each part of it decided by the planner, and an act's
 * form and values by the reasoner, the first time the run needs it, live; replayed every later time with no model.
 * [ask] asks the planner for a move at a step, the moves it may make and the step as told; [named] holds what the
 * goal's steps named; [moves] is what the planner is told was done; [fill] puts the inputs and names into words.
 */
internal class StepWatch(
    private val goal: Goal,
    private val run: Run,
    private val named: MutableMap<String, Representation>,
    private val moves: MutableList<String>,
    private val host: Host,
) {
    /** What the goal run gives a watch: the planner, where the run began, and words with names put in. */
    interface Host {
        /** The planner's move for [step] at [here]: the moves it may make, [allowed], and the step as told. */
        fun ask(step: Step, here: Here, allowed: Set<String>, current: String): Move

        /** Where the run began, read again. */
        fun start(): Here

        /** [text] with the inputs and names put in. */
        fun fill(text: String): String
    }


    private val lists = mutableMapOf<Step.Whenever, Chosen>()
    private val matches = mutableMapOf<Step.Whenever, Map<String, Any?>>()
    private val waits = mutableMapOf<Step.Intent, String>()
    private val ifs = mutableMapOf<Step.Intent, Map<String, Any?>>()
    private val acts = mutableMapOf<Pair<Step.Intent, String>, Chosen?>()
    private var ends: Map<String, Any?>? = null

    /**
     * [step], at [where]: its list reached as decided, each look, until the goal's Until holds. When the list isn't
     * reached from where the step stands, the planner may go back to the start, and the watch stands there.
     */
    fun whenever(step: Step.Whenever, where: Here) {
        var scope = where
        val told = "whenever: ${host.fill(step.match)}: from here, reach the list to watch, by a form or a link, " +
            "or go back to the start if it isn't reached from here; when a form can narrow the list to what the " +
            "Whenever says (a filter), narrow it, so each look reads only those; it is read again at every look"
        var back = false
        val list = lists.getOrPut(step) {
            var decided: Chosen? = null
            while (decided == null) {
                val move = host.ask(step, scope, LIST, told)
                when {
                    move is Move.Back && !back -> {
                        scope = host.start()
                        back = true
                        moves += "back to the start, at ${scope.held.address}"
                    }
                    move is Move.Act -> decided = acted(scope, move, null)
                    move is Move.Stop -> stop(move.why)
                    else -> moves += "${StepText.kind(move)}: refused, here only ${LIST.joinToString(", ")}"
                }
            }
            decided
        }
        val until = goal.steps.filterIsInstance<Step.Until>().firstOrNull()
        scope.watch(until = { until != null && over(until, this) }) {
            replay(list, this, null) {
                whenever {
                    // Whether the member matches: the check the planner made at the first member, decided once.
                    val match = matches.getOrPut(step) {
                        val told = "whenever: ${host.fill(step.match)}: the check a member that matches passes"
                        decide(step, this, CHECK, told) { (it as? Move.Check)?.facts }
                    }
                    if (holds(match, held)) {
                        step.name?.let { named[it] = held }
                        // An If that leaves the member leaves its other steps undone, this look.
                        step.children.all { child(it, this) }
                    }
                }
            }
        }
        val ended = until?.let { host.fill(it.condition) } ?: "its time ran out"
        moves += "watched: ${host.fill(StepText.said(step))}, until $ended"
    }

    /**
     * One step under a Whenever, at the member [here]: an If acts, or leaves the member, when its check holds; a
     * Once waits, then acts; any other step acts. Whether the member's other steps are done after it.
     */
    private fun child(step: Step, here: Here): Boolean {
        val intent = step as? Step.Intent ?: stop("line ${step.line}: only steps that act go under a Whenever yet")
        if (intent.children.isNotEmpty()) stop("line ${step.line}: a step under a Whenever has no steps under it yet")
        val condition = intent.condition
        val once = intent.once
        when {
            condition != null -> return given(intent, condition, here)
            once != null -> waited(intent, once, here)
            else -> perform(intent, ACT, here, null)
        }
        return true
    }

    /** An If at the member [here]: its check, decided once; when it holds, its words, or leaving the member. */
    private fun given(intent: Step.Intent, condition: String, here: Here): Boolean {
        val facts = ifs.getOrPut(intent) {
            decide(intent, here, CHECK, "if: ${host.fill(condition)}: the check that tells") {
                (it as? Move.Check)?.facts
            }
        }
        val holds = holds(facts, here.held)
        if (holds && intent.leaves) {
            // Said once for each member it leaves.
            val first = run.watching.left.add(here.held.address)
            if (first) here.note("Left ${here.held.address}: ${host.fill(intent.words)}")
        } else if (holds) {
            perform(intent, ACT, here, null)
        }
        return !(holds && intent.leaves)
    }

    /** A Once at the member [here]: the act its wait is for, decided once; then the wait, its act or otherwise. */
    private fun waited(intent: Step.Intent, once: Once, here: Here) {
        val relation = waits.getOrPut(intent) {
            decide(intent, here, WAIT, "once: ${host.fill(once.condition)}") { move ->
                (move as? Move.Wait)?.relation?.takeIf { it.isNotBlank() }?.also { moves += "once: waits for $it" }
            }
        }
        here.once(
            relation,
            giveUp = once.giveUp?.after ?: 10.minutes,
            otherwise = { why -> intent.otherwise?.let { perform(intent, OTHERWISE, this, why) } },
        ) { perform(intent, ACT, this, null) }
    }

    /**
     * The act [part] of [step] at [here]: decided now, live, the first time (the planner's act, the reasoner's
     * choice, then the planner's done); replayed after.
     */
    private fun perform(step: Step.Intent, part: String, here: Here, why: String?) {
        val key = step to part
        if (key in acts) {
            acts[key]?.let { replay(it, here, why, NOTHING) }
            return
        }
        val words = if (part == OTHERWISE) checkNotNull(step.otherwise).words else step.words
        var chosen: Chosen? = null
        decide(step, here, ACTS, "$part: ${host.fill(words)}") { move ->
            when {
                move is Move.Act && chosen == null -> null.also { chosen = acted(here, move, why) }
                move is Move.Done -> true
                else -> null
            }
        }
        acts[key] = chosen
    }

    /**
     * [move], an act, made at [here] by the reasoner, and its choice kept, each value bound to its source. Refused,
     * the planner is told, and nothing is kept: it decides again.
     */
    private fun acted(here: Here, move: Move.Act, why: String?): Chosen? {
        val words = host.fill(move.words)
        val sources = Bound.sources(here.held, named, run, why)
        val traced = run.trace.size
        val refused = try {
            here.act(intent = words, block = NOTHING)
            null
        } catch (stopped: ScriptStopped) {
            stopped.refused ?: throw stopped
        }
        val chosen = if (refused == null) run.trace.getOrNull(traced)?.let { Chosen.of(it, sources) } else null
        moves += refused?.let { "$words: refused, ${it.kind.words}: ${it.detail}" }
            ?: "$words: decided ${chosen?.relation}, at ${here.held.address}"
        return chosen
    }

    /** Whether the goal's [until] holds, read at the resource it names, else at [scope]. */
    private fun over(until: Step.Until, scope: Here): Boolean {
        val name = BRACE.findAll(until.condition).map { it.groupValues[1] }.firstOrNull { it in named }
        val at = name?.let { named.getValue(it).address }?.let {
            Here(scope.navigator, run, arrived(++run.step, "\"until\"", scope.navigator.recall(it)))
        } ?: scope
        val told = "until: ${host.fill(until.condition)}: the check that holds once the watch is to end, not " +
            "what holds now; it is made after every look, and the watch ends when it holds"
        val facts = ends ?: decide(until, at, CHECK, told) {
            (it as? Move.Check)?.facts
        }.also { ends = it }
        return holds(facts, at.held)
    }

    /**
     * The planner's moves at [here] for [step], told as [current] and allowed [allowed], until [pick] makes
     * something of one; a stop stops the run, and any other move is refused and the planner told.
     */
    private fun <T : Any> decide(step: Step, here: Here, allowed: Set<String>, current: String, pick: (Move) -> T?): T {
        while (true) {
            val move = host.ask(step, here, allowed, current)
            if (move is Move.Stop) stop(move.why)
            val made = pick(move)
            if (made != null) return made
            val kind = StepText.kind(move)
            if (kind !in allowed) moves += "$kind: refused, here only ${allowed.joinToString(", ")}"
        }
    }

    /** [chosen], made again at [here], each value read again from where it came from; [block] where it led. */
    private fun replay(chosen: Chosen, here: Here, why: String?, block: Here.() -> Unit) {
        val sources = Bound.sources(here.held, named, run, why)
        if (chosen.kind == "link") {
            here.follow(chosen.relation, chosen.title, block = block)
        } else {
            here.act(chosen.relation, fields = {
                chosen.values.forEach { (field, bound) -> field to bound.filled(sources) }
            }, block = block)
        }
    }

    private fun stop(why: String): Nothing = throw GoalStopped(run.step, "\"goal\"", "\"$why\"")

    private companion object {
        const val ACT = "act"
        const val OTHERWISE = "otherwise"
        val ACTS = setOf("act", "done", "stop")
        val LIST = setOf("act", "back", "stop")
        val WAIT = setOf("wait", "stop")
        val CHECK = setOf("check", "stop")
        val BRACE = Regex("""\{(\w+)""")
        val NOTHING: Here.() -> Unit = {}
    }
}
