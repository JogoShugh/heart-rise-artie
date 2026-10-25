package heart.script

import kotlinx.serialization.json.JsonPrimitive
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


    // Whenevers side by side look on threads of their own; each decides only its own steps' parts.
    val decided = Decisions()

    /**
     * [steps], Whenevers side by side, at [where]: each one's list reached as decided, each look, until the goal's
     * Until holds. One Whenever is one watch. Several are a watch with listeners (spec/PLAN-LISTENERS.md, slice 1):
     * each one's first look is made in turn, as the goal lists them, so its parts are decided in the goal's order;
     * after it, each looks on a thread of its own, replaying what was decided.
     */
    fun whenever(steps: List<Step.Whenever>, where: Here) {
        val until = goal.steps.filterIsInstance<Step.Until>().firstOrNull()
        // The watch as the script its decisions amount to, written when the capture is (spec/PLAN-LISTENERS.md,
        // slice 8), from the step it began at; its Until reads the resource it names by a variable named for it.
        val entry = where.held.address
        val name = until?.let { u -> BRACE.findAll(u.condition).map { it.groupValues[1] }.firstOrNull { it in named } }
        run.watchFrom = run.trace.size + 1
        run.watchScript = { WatchScript(decided, entry, name?.let { "${it}At" }, host::fill).text(steps) }
        val done: Here.() -> Boolean = { until != null && over(until, this) }
        val single = steps.singleOrNull()
        if (single != null) {
            val watched = Watched(single, where)
            watched.scope.watch(until = done) { watched.look(this) }
        } else {
            run.watching.active = true
            val watched = try {
                steps.map { step ->
                    // Each from where the watch stands, read again, as the look before may have left it.
                    where.held = arrived(++run.step, "\"watch\"", where.navigator.recall(where.held.address))
                    // Its first look from where its list is reached: there, or the start.
                    Watched(step, where).also { first ->
                        first.scope.attempt(first.scope.held.address, "A look failed") { first.look(this) }
                    }
                }
            } finally {
                run.watching.active = false
            }
            where.listeners(until = done) {
                watched.forEach { listen(host.fill(it.step.match), from = it.scope.held.address) { it.look(this) } }
            }
        }
        val ended = until?.let { host.fill(it.condition) } ?: "its time ran out"
        steps.forEach { moves += "watched: ${host.fill(StepText.said(it))}, until $ended" }
    }

    /**
     * Whenever [step]'s list, reached from [where] as decided once. When the list isn't reached from where the
     * step stands, the planner may go back to the start, and the watch stands there, its [scope].
     */
    private inner class Watched(val step: Step.Whenever, where: Here) {
        var scope = where
            private set
        private val list: Chosen = decided.lists.getOrPut(step) { reach() }

        init {
            // Where the list is reached from, as decided: where the step stands, or the start.
            decided.scopes.putIfAbsent(step, scope.held.address)
        }

        private fun reach(): Chosen {
            val told = "whenever: ${host.fill(step.match)}: from here, reach the list to watch, by a form or a " +
                "link, or go back to the start if it isn't reached from here; when a form can narrow the list to " +
                "what the Whenever says (a filter), narrow it, so each look reads only those; it is read again at " +
                "every look"
            var back = false
            var decided: Chosen? = null
            while (decided == null) {
                val move = host.ask(step, scope, LIST, told)
                when {
                    move is Move.Back && !back -> {
                        scope = host.start()
                        back = true
                        moves += "back to the start, at ${scope.held.address}"
                    }
                    move is Move.Act -> decided = acted(scope, move, null, step.match)
                    move is Move.Stop -> run.goalStop(move.why)
                    else -> moves += "${StepText.kind(move)}: refused, here only ${LIST.joinToString(", ")}"
                }
            }
            return decided
        }

        /** One look, at [here]: the list reached again, and each member new or changed that matches visited. */
        fun look(here: Here) = here.replay(list, sources(here, null)) {
            // Who made a member and what it says are read as they are; a match they say all of needs no check.
            whenever(by = step.by?.let(host::fill), says = step.says?.let(host::fill)) {
                if (step.saidByWords || matches(this)) {
                    step.name?.let { named[it] = held }
                    decided.answered(step, held.address)
                    // An If that leaves the member leaves its other steps undone, this look.
                    step.children.all { child(it, this) }
                }
            }
        }

        /** Whether the member [here] matches: the check the planner made at the first member, decided once. */
        private fun matches(here: Here): Boolean {
            val match = decided.matches.getOrPut(step) {
                val told = "whenever: ${host.fill(step.match)}: the check a member that matches passes"
                bound(decide(step, here, CHECK, told) { (it as? Move.Check)?.facts }, here, step.match)
            }
            return match.passes(sources(here, null), here.held)
        }
    }

    /**
     * One step under a Whenever, at the member [here]: an If acts, or leaves the member, when its check holds; a
     * Once waits, then acts; any other step acts. Whether the member's other steps are done after it.
     */
    private fun child(step: Step, here: Here): Boolean {
        val intent = step as? Step.Intent
            ?: run.goalStop("line ${step.line}: only steps that act go under a Whenever yet")
        if (intent.children.isNotEmpty()) {
            run.goalStop("line ${step.line}: a step under a Whenever has no steps under it yet")
        }
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
        val facts = decided.ifs.getOrPut(intent) {
            val told = "if: ${host.fill(condition)}: the check that holds exactly when that is true of the member, " +
                "and fails on a member it isn't true of; made on this member, then on every member after"
            bound(decide(intent, here, CHECK, told) { (it as? Move.Check)?.facts }, here, condition)
        }
        val holds = facts.passes(sources(here, null), here.held)
        if (holds && intent.leaves) {
            // Said once for each member it leaves.
            val first = here.run.watching.left.add(here.held.address)
            if (first) here.note("Left ${here.held.address}: ${host.fill(intent.words)}")
        } else if (holds) {
            perform(intent, ACT, here, null)
        }
        return !(holds && intent.leaves)
    }

    /** A Once at the member [here]: the act its wait is for, decided once; then the wait, its act or otherwise. */
    private fun waited(intent: Step.Intent, once: Once, here: Here) {
        val relation = decided.waits.getOrPut(intent) {
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
     * The act [part] of [step] at [here], a chain of acts: replayed as far as it is decided, then decided further,
     * live, until the planner says done.
     */
    private fun perform(step: Step.Intent, part: String, here: Here, why: String?) {
        val chain = decided.acts.getOrPut(step to part) { Chain() }
        Walk(step, part, chain, why).from(0, here, here.held)
    }

    /**
     * Where values come from at [here], with the server's reason a wait gave up, [why]; the inputs the step's words
     * [meant] name first, as a value more than one input holds is the one the words say.
     */
    private fun sources(here: Here, why: String?, meant: String = "") = Bound.sources(
        here.held, synchronized(named) { named.toMap() }, here.run, why,
        BRACE.findAll(meant).map { it.groupValues[1] }.toSet(),
    )

    /**
     * [move], an act, made at [here] by the reasoner, and its choice kept, each value bound to its source; [then] on
     * what it reached, with the choice. Refused, the planner is told, and nothing is kept: it decides again.
     */
    private fun acted(
        here: Here,
        move: Move.Act,
        why: String?,
        meant: String,
        then: Here.(Chosen?) -> Unit = {},
    ): Chosen? = synchronized(host) {
        // One decision at a time, as the planner is asked: the reasoner is the run's, whichever thread acts.
        // An act of an Otherwise is told the wait gave up, and why, in the server's words, unless its words say why
        // already: told twice, the reasoner says it twice.
        val said = host.fill(move.words)
        val told = gaveUp(why)?.takeUnless { why != null && why.trim().trimEnd('.') in said }
        val words = said
        val sources = sources(here, why, meant)
        val traced = here.run.trace.size
        var chosen: Chosen? = null
        var entered = false
        // The reasoner is told why the wait gave up besides the words; the trace keeps the words alone.
        here.run.besides = told?.let { "${it.replaceFirstChar(Char::uppercase)}." }
        val refused = try {
            here.act(intent = words) {
                here.run.besides = null
                entered = true
                chosen = here.run.trace.getOrNull(traced)?.let { Chosen.of(it, sources) }
                moves += "$words: decided ${chosen?.relation}, at ${here.held.address}"
                then(chosen)
            }
            null
        } catch (stopped: ScriptStopped) {
            // A refusal of an act inside this one's block is that act's, already told; this one was made.
            stopped.refused?.takeUnless { entered } ?: throw stopped
        } finally {
            here.run.besides = null
        }
        refused?.let { moves += "$words: refused, ${it.kind.words}: ${it.detail}" }
        chosen
    }

    /** A chain of [step]'s [part], walked at a resource, with the server's reason a wait gave up, [why]. */
    private inner class Walk(val step: Step.Intent, val part: String, val chain: Chain, val why: String?) {
        /** What the last link followed, a read, reached, as this chain is decided. */
        private var read: Representation? = null

        /** The words of the act made last, as this chain is decided, until a check is made after it. */
        private var made: String? = null

        /** The parts from [index] on, at [here]; [reached] is what the last act reached, where a check is made. */
        fun from(index: Int, here: Here, reached: Representation) {
            var last = reached
            for (at in index until chain.parts.size) {
                when (val part = chain.parts[at]) {
                    is Chain.Check -> if (part.passes(sources(here, why), last)) return
                    is Chain.Act -> if (part.opens) {
                        here.replay(part.chosen, sources(here, why)) { from(at + 1, this, held) }
                        return
                    } else {
                        here.replay(part.chosen, sources(here, why)) { last = held }
                    }
                }
            }
            if (!chain.complete) further(here, last)
        }

        /** The chain decided further at [here], each of the planner's moves kept in order, until it says done. */
        /** The words of the part being decided: the step's, or its Otherwise's. */
        private fun said() = if (part == OTHERWISE) checkNotNull(step.otherwise).words else step.words

        private fun further(here: Here, reached: Representation) {
            val words = said()
            val told = toldActs(part, host.fill(words), why)
            var last = reached
            var over = false
            // Whether the last check kept fails here with nothing done after it: what it keeps undone is to be done.
            var failing = false
            while (!over) {
                when (val move = host.ask(step, here, ACTS, told)) {
                    is Move.Stop -> run.goalStop(move.why)
                    is Move.Done -> unfinished(words, chain, failing)?.let { moves += it }
                        ?: run { over = true.also { chain.done() } }
                    is Move.Check -> {
                        over = checked(move, here, last)
                        failing = !over && chain.parts.lastOrNull() is Chain.Check
                    }
                    is Move.Act -> over = act(move, here) { last = it }.also { failing = false }
                    else -> moves += "${StepText.kind(move)}: refused, here only ${ACTS.joinToString(", ")}"
                }
            }
        }

        /** A check kept; whether it holds on [reached], which leaves the rest undone here. */
        private fun checked(move: Move.Check, here: Here, reached: Representation): Boolean {
            made = null
            // Its values from the run's inputs, or as the planner said them: one read again from the member, as the
            // member's own count that happened to equal the planner's, would hold on every member after.
            val inputs = sources(here, why, said()).filterKeys { it.startsWith("input:") }
            val check = Chain.Check(move.facts.mapValues { (_, value) -> Bound.of(value, inputs) })
            // A check after acts that write, made on what reads among them reached, can keep none of them from being
            // done: it is kept before the first act that writes since the last check, with those reads, so a replay
            // reads, checks, and only then writes.
            val since = chain.parts.indexOfLast { it is Chain.Check } + 1
            val segment = chain.parts.subList(since, chain.parts.size).toList()
            val (reads, writes) = segment.partition { it is Chain.Act && it.chosen.kind == LINK }
            val plain = segment.none { it is Chain.Act && it.opens }
            val moved = writes.isNotEmpty() && plain
            // Kept after the reads, it is made on what the last of them reached, as a replay makes it.
            val on = if (moved) read ?: reached else reached
            val holds = holds(move.facts, on)
            refusal(move.facts, on, moved && !holds)?.let { refused ->
                moves += refused
                return false
            }
            if (moved) {
                chain.parts.subList(since, chain.parts.size).clear()
                chain.parts += reads + check + writes
                moves += "check: kept before the acts that write, with the reads it is made on"
            } else {
                chain.parts += check
            }
            // Its facts and where it was made, so a wrong check can be read back from the capture.
            moves += "check: kept ${element(move.facts)}, at ${on.address}; " +
                if (holds) "it holds here, so the acts after it are left undone" else "it fails"
            return holds
        }

        /** An act made and kept; one that opens is followed by the chain decided inside it, which ends the chain. */
        private fun act(move: Move.Act, here: Here, reached: (Representation) -> Unit): Boolean {
            // Asked again in the words of the act just made, it is not made again: the server answered it.
            val said = host.fill(move.words)
            if (said == made) {
                moves += "$said: refused, it was just made, and the server answered it: what it did is done, though " +
                    "a read may not show it yet; make the next act, or say done"
                return false
            }
            var inside = false
            acted(here, move, why, said()) { chosen ->
                if (chosen == null) return@acted
                // Only an act the server answered is one just made: one refused before it was sent may be asked again.
                made = said
                // A link followed is a read, though the planner says it opens: the check after it is made on what
                // it reached, and the acts after that on the member, where the step's forms are.
                val opens = move.opens && chosen.kind != LINK
                // Made again just as the act before it, it is kept once: a replay makes it once.
                val before = (chain.parts.lastOrNull() as? Chain.Act)?.takeIf { it.opens == opens }
                if (before != null && before.chosen.same(chosen)) {
                    moves += "${host.fill(move.words)}: made again just as the act before it, kept once"
                } else {
                    chain.parts += Chain.Act(chosen, opens)
                }
                if (opens) {
                    inside = true
                    further(this, held)
                } else {
                    // A read leaves the planner on the member: it is told what the read reached, to check it.
                    if (chosen.kind == LINK) {
                        read = held
                        moves += "${host.fill(move.words)}: read ${held.address}, which holds ${readable(held)}"
                    }
                    reached(held)
                }
            }
            return inside
        }
    }

    /** Whether the goal's [until] holds, read at the resource it names, else at [scope]. */
    private fun over(until: Step.Until, scope: Here): Boolean {
        val name = BRACE.findAll(until.condition).map { it.groupValues[1] }.firstOrNull { it in named }
        val at = name?.let { named.getValue(it).address }?.let {
            Here(scope.navigator, run, arrived(++run.step, "\"until\"", scope.navigator.recall(it)))
        } ?: scope
        val told = "until: ${host.fill(until.condition)}: the check that holds once the watch is to end, not " +
            "what holds now; it is made after every look, and the watch ends when it holds"
        // One naming what the resource doesn't hold can never hold, and the watch would look until its time ran out.
        val possible: (Move) -> Map<String, Any?>? = { move ->
            (move as? Move.Check)?.facts?.takeIf { facts ->
                refusal(facts, at.held, failsAfter = false)?.also { moves += it } == null
            }
        }
        val ends = decided.ends
            ?: bound(decide(until, at, CHECK, told, possible), at, until.condition).also { decided.ends = it }
        return ends.passes(sources(at, null), at.held)
    }

    /**
     * [facts] a check was decided as, at [here], kept with each value bound to the run's input that holds it, those
     * the step's words [meant] name first, or as the planner said it: so the script made from the run checks what
     * it is given (spec/PLAN-LISTENERS.md, slice 8).
     */
    private fun bound(facts: Map<String, Any?>, here: Here, meant: String): Chain.Check {
        val inputs = sources(here, null, meant).filterKeys { it.startsWith("input:") }
        return Chain.Check(facts.mapValues { (_, value) -> Bound.of(value, inputs) })
    }

    /**
     * The planner's moves at [here] for [step], told as [current] and allowed [allowed], until [pick] makes
     * something of one; a stop stops the run, and any other move is refused and the planner told.
     */
    private fun <T : Any> decide(step: Step, here: Here, allowed: Set<String>, current: String, pick: (Move) -> T?): T {
        while (true) {
            val move = host.ask(step, here, allowed, current)
            if (move is Move.Stop) run.goalStop(move.why)
            val made = pick(move)
            // A check is kept in the capture as decided, so a wrong one can be read back, and written as a script.
            if (made != null && move is Move.Check) {
                moves += "${current.substringBefore(": the check")}: decided the check ${element(move.facts)}, " +
                    "at ${here.held.address}"
            }
            if (made != null) return made
            val kind = StepText.kind(move)
            if (kind !in allowed) moves += "$kind: refused, here only ${allowed.joinToString(", ")}"
        }
    }

    private companion object {
        const val ACT = "act"

        /** A choice that follows a link: a read, never a write. */
        const val LINK = "link"

        const val OTHERWISE = "otherwise"
        val ACTS = setOf("act", "check", "done", "stop")
        val LIST = setOf("act", "back", "stop")
        val WAIT = setOf("wait", "stop")
        val CHECK = setOf("check", "stop")
        val BRACE = Regex("""\{(\w+)""")
    }
}

/**
 * What an Otherwise is told when its wait gave up, with the server's reason, [why]: so its words say that reason,
 * not one made up from the state, and promise nothing the watch won't do. Null outside an Otherwise.
 */
private fun gaveUp(why: String?): String? = why?.let {
    "the wait gave up, and the server says why: $it Say that reason in the server's words, and claim nothing the " +
        "watch won't do"
}

/** What the planner is told as it decides [part] of a step, its [words] filled, with the reason a wait gave up. */
private fun toldActs(part: String, words: String, why: String?): String =
    "$part: $words: ${gaveUp(why)?.let { "$it; " }.orEmpty()}" +
        "one act or more, in order, then done; an act the server answered is done, even where a read just " +
        "after it doesn't show it yet; the acts after an act " +
        "that opens are done on what it reached; a check is made on what the act before it reached, and " +
        "where it holds, the acts after it are left undone: so a check that is to keep an act from being " +
        "done, such as whether it was said already, comes before that act; whether something was said is " +
        "checked on the words said, among the titles of what is listed, not on how many there are; a fact names a " +
        "member of the state, or a relation it links to, its value words a link's title says"

/**
 * Why a chain of a step's [words] isn't done yet, as the planner is told; null when it is. A step that says unless
 * isn't done until the check that keeps its act from being done is made; nor where its last check fails here, with
 * nothing done after it ([failing]): what that check keeps undone is to be done.
 */
private fun unfinished(words: String, chain: Chain, failing: Boolean): String? = when {
    UNLESS.containsMatchIn(words) && chain.parts.none { it is Chain.Check } ->
        "done: refused, the step says unless, and no check keeps its act from being done yet: read what it is to be " +
            "checked on, then check it"
    failing -> "done: refused, the last check fails here, and nothing was done after it: do what it keeps from " +
        "being done, or make the check that holds where it is done already"
    else -> null
}

/** A step's words that keep its act from being done somewhere: "…, unless it's said already". */
private val UNLESS = Regex("""\bunless\b""", RegexOption.IGNORE_CASE)

/**
 * Why a check of [facts], made [on] a resource, is refused, as the planner is told; null when it is kept. A fact names
 * a member, or a relation embedded or linked to: one naming none of them never holds. A check made right after the
 * acts it is to keep from being done again must hold there ([failsAfter] when it doesn't): else a replay does them
 * again.
 */
private fun refusal(facts: Map<String, Any?>, on: Representation, failsAfter: Boolean): String? {
    val unheld = facts.keys.filter { name ->
        !State(on.state).find(name).first && name !in on.embedded && name !in on.links
    }
    return when {
        unheld.isNotEmpty() -> "check: refused, ${unheld.joinToString(", ")} is neither a member of ${on.address} " +
            "nor a relation it embeds or links to: it holds ${readable(on)}" +
            example(on)?.let { "; a fact names one of those, as $it does" }.orEmpty()
        failsAfter -> "check: refused, it fails here, right after the acts it is to keep from being done again, so " +
            "a replay would do them again: it is made on ${on.address}, which holds ${readable(on)}; a fact names a " +
            "member of that state, or a relation it links to, its value words a link's title says"
        else -> null
    }
}

/**
 * A fact that holds on [held], made from what it holds, to show the planner the form a fact takes: its first
 * relation linked to with a title, that title its value; else its first member, with its value. Null for nothing.
 */
private fun example(held: Representation): String? {
    val linked = held.links.entries
        .firstOrNull { (relation, titles) -> relation != "self" && titles.any { it != null } }
    val fact = linked?.let { (relation, titles) -> relation to JsonPrimitive(titles.first { it != null }) }
        ?: held.state.entries.firstOrNull { it.value is JsonPrimitive }?.toPair()
    return fact?.let { (name, value) -> "{${JsonPrimitive(name)}: $value}" }
}

/** What a read reached, in words for the planner: its state, then each relation's link titles, but its own. */
private fun readable(held: Representation): String {
    val state = held.state.toString().let { if (it.length > READ_SHOWN) it.take(READ_SHOWN) + "…" else it }
    val links = held.links.filterKeys { it != "self" }.entries.joinToString("; ") { (relation, titles) ->
        "$relation: " + titles.joinToString(" | ") { it ?: "(untitled)" }
    }
    return state + if (links.isEmpty()) "" else ", and links $links"
}

/** How much of a read's state the planner is told, in characters. */
private const val READ_SHOWN = 1500

/** The goal run stopped here, saying [why]. */
private fun Run.goalStop(why: String): Nothing = throw GoalStopped(step, "\"goal\"", "\"$why\"")
