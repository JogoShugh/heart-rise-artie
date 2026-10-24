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
    private val fill: (String) -> String,
    private val ask: (Step, Here, Set<String>, String) -> Move,
) {
    private val lists = mutableMapOf<Step.Whenever, Chosen>()
    private val matches = mutableMapOf<Step.Whenever, Map<String, Any?>>()
    private val waits = mutableMapOf<Step.Intent, String>()
    private val acts = mutableMapOf<Pair<Step.Intent, String>, Chosen?>()
    private var ends: Map<String, Any?>? = null

    /** [step], at [scope]: its list reached as decided, each look, until the goal's Until holds. */
    fun whenever(step: Step.Whenever, scope: Here) {
        val list = lists.getOrPut(step) {
            decide(step, scope, LIST, "whenever: ${fill(step.match)}: reach the list to watch") { move ->
                (move as? Move.Act)?.let { acted(scope, it, null) }
            }
        }
        val until = goal.steps.filterIsInstance<Step.Until>().firstOrNull()
        scope.watch(until = { until != null && over(until, this) }) {
            replay(list, this, null) {
                whenever {
                    if (matches(step, this)) {
                        step.name?.let { named[it] = held }
                        step.children.forEach { child(it, this) }
                    }
                }
            }
        }
        val ended = until?.let { fill(it.condition) } ?: "its time ran out"
        moves += "watched: ${fill(StepText.said(step))}, until $ended"
    }

    /** One step under a Whenever, at the member [here]: a Once waits, then acts; any other step acts. */
    private fun child(step: Step, here: Here) {
        val intent = step as? Step.Intent ?: stop("line ${step.line}: only steps that act go under a Whenever yet")
        if (intent.children.isNotEmpty()) stop("line ${step.line}: a step under a Whenever has no steps under it yet")
        val once = intent.once ?: return perform(intent, ACT, here, null)
        val relation = waits.getOrPut(intent) {
            decide(intent, here, WAIT, "once: ${fill(once.condition)}") { move ->
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
        decide(step, here, ACTS, "$part: ${fill(words)}") { move ->
            when {
                move is Move.Act && chosen == null -> null.also { chosen = acted(here, move, why) }
                move is Move.Done -> true
                else -> null
            }
        }
        acts[key] = chosen
    }

    /** [move], an act, made at [here] by the reasoner, and its choice kept, each value bound to its source. */
    private fun acted(here: Here, move: Move.Act, why: String?): Chosen? {
        val sources = Bound.sources(here.held, named, run, why)
        val traced = run.trace.size
        here.act(intent = fill(move.words), block = NOTHING)
        val chosen = run.trace.getOrNull(traced)?.let { Chosen.of(it, sources) }
        moves += "${fill(move.words)}: decided ${chosen?.relation}, at ${here.held.address}"
        return chosen
    }

    /** Whether the member [here] matches [step]: the check the planner made at the first member, decided once. */
    private fun matches(step: Step.Whenever, here: Here): Boolean {
        val facts = matches.getOrPut(step) {
            decide(step, here, CHECK, "whenever: ${fill(step.match)}: the check a member that matches passes") {
                (it as? Move.Check)?.facts
            }
        }
        return holds(facts, here.held)
    }

    /** Whether the goal's [until] holds, read at the resource it names, else at [scope]. */
    private fun over(until: Step.Until, scope: Here): Boolean {
        val name = BRACE.findAll(until.condition).map { it.groupValues[1] }.firstOrNull { it in named }
        val at = name?.let { named.getValue(it).address }?.let {
            Here(scope.navigator, run, arrived(++run.step, "\"until\"", scope.navigator.recall(it)))
        } ?: scope
        val facts = ends ?: decide(until, at, CHECK, "until: ${fill(until.condition)}") {
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
            val move = ask(step, here, allowed, current)
            if (move is Move.Stop) stop(move.why)
            val made = pick(move)
            if (made != null) return made
            if (move !is Move.Act) moves += StepText.refused(move, step)
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

    private fun stop(why: String): Nothing = throw ScriptStopped(run.step, "\"goal\"", "\"$why\"")

    private companion object {
        const val ACT = "act"
        const val OTHERWISE = "otherwise"
        val ACTS = setOf("act", "done", "stop")
        val LIST = setOf("act", "stop")
        val WAIT = setOf("wait", "stop")
        val CHECK = setOf("check", "stop")
        val BRACE = Regex("""\{(\w+)""")
        val NOTHING: Here.() -> Unit = {}
    }
}
