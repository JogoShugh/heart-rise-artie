package heart.script

import java.util.concurrent.ConcurrentHashMap

/**
 * What a watch decided, kept to be replayed (spec/PLAN-WATCH-GOALS.md), and written as a script
 * (spec/PLAN-LISTENERS.md, slice 8): each Whenever's list, where it is reached from, and its match; each If's
 * check, each Once's wait, each step's chains of acts; and the Until's check. Whenevers side by side decide on
 * threads of their own.
 */
internal class Decisions {
    val lists = ConcurrentHashMap<Step.Whenever, Chosen>()
    val scopes = ConcurrentHashMap<Step.Whenever, String>()
    val matches = ConcurrentHashMap<Step.Whenever, Chain.Check>()
    val waits = ConcurrentHashMap<Step.Intent, String>()
    val ifs = ConcurrentHashMap<Step.Intent, Chain.Check>()
    val acts = ConcurrentHashMap<Pair<Step.Intent, String>, Chain>()

    /** The members each Whenever answered, by address, in the order it first answered them. */
    val members = ConcurrentHashMap<Step.Whenever, MutableSet<String>>()

    @Volatile
    var ends: Chain.Check? = null

    /** A member [step] answered, at [address]. */
    fun answered(step: Step.Whenever, address: String) {
        members.getOrPut(step) { java.util.Collections.synchronizedSet(LinkedHashSet()) } += address
    }
}

/** Lines of Kotlin, written one at a time, each [INDENT] in for its depth. */
internal class ScriptLines {
    private val lines = mutableListOf<String>()

    /** [block] in braces after [head], one level in. */
    fun open(depth: Int, head: String, block: (Int) -> Unit) {
        line(depth, "$head {")
        block(depth + 1)
        line(depth, "}")
    }

    /** A comment saying [words]. */
    fun said(depth: Int, words: String) = line(depth, "// $words")

    fun line(depth: Int, text: String) {
        lines += INDENT.repeat(depth) + text
    }

    fun text() = lines.joinToString("\n")

    private companion object {
        const val INDENT = "    "
    }
}

/**
 * The script a watch's [decided] parts amount to, with no model in it (spec/PLAN-LISTENERS.md, slice 8): one
 * Whenever a `watch`, several a watch with `listeners`, each look its list reached as decided and each member that
 * matches answered as decided. Its Until reads again, by `at`, the address the variable [named] holds; [entry] is
 * where the script stands as it begins; [fill] puts the inputs and names into words.
 */
internal class WatchScript(
    private val decided: Decisions,
    private val entry: String,
    private val named: String?,
    private val fill: (String) -> String,
) {
    private val out = ScriptLines()
    private val chains = ChainScript(decided, out, fill)

    /** The script of [steps], Whenevers side by side. */
    fun text(steps: List<Step.Whenever>): String {
        val single = steps.singleOrNull()
        val scope = single?.let { decided.scopes[it] }?.takeIf { it != entry }
        when {
            single != null && scope != null -> out.open(0, "at(${quoted(scope)})") { watch(single, it) }
            single != null -> watch(single, 0)
            else -> out.open(0, "listeners(until = ${until()})") { depth -> steps.forEach { listen(it, depth) } }
        }
        return out.text()
    }

    private fun watch(step: Step.Whenever, depth: Int) =
        out.open(depth, "watch(until = ${until()})") { look(step, it) }

    private fun listen(step: Step.Whenever, depth: Int) {
        val from = decided.scopes[step]?.takeIf { it != entry }?.let { ", from = ${quoted(it)}" }.orEmpty()
        out.open(depth, "listen(${quoted(fill(step.match))}$from)") { look(step, it) }
    }

    /** The Until's check, read again where it names, else where the watch stands; none, the watch runs its time. */
    private fun until(): String {
        val ends = decided.ends ?: return "{ false }"
        val check = "holds(${bound(ends.facts)})"
        return if (named == null) "{ $check }" else "holdsAt(${quoted(named)}, ${bound(ends.facts)})"
    }

    /** One look of [step]: its list reached as decided, and each member that matches answered. */
    private fun look(step: Step.Whenever, depth: Int) {
        val list = decided.lists[step] ?: return out.said(depth, "${fill(step.match)}: no list decided in this run")
        // Which members it answers is the whenever's own: by whom, saying what, holding what the match decided.
        val match = decided.matches[step].takeUnless { step.saidByWords }
        out.open(depth, reach(list)) { inside ->
            out.open(inside, whenever(step, match)) { member ->
                if (step.saidByWords || match != null) {
                    children(step.children, member)
                } else {
                    out.said(member, "${fill(step.match)}: no member matched in this run")
                }
            }
        }
    }

    /** The steps under a Whenever, at a member: an If that leaves it holds the steps after it in its else. */
    private fun children(steps: List<Step>, depth: Int) {
        for ((at, step) in steps.withIndex()) {
            val intent = step as? Step.Intent ?: continue
            val condition = intent.condition
            val check = decided.ifs[intent]
            when {
                condition == null -> plain(intent, depth)
                check == null -> out.said(depth, "if ${fill(condition)}: not decided in this run")
                intent.leaves -> {
                    left(intent, check, steps.drop(at + 1), depth)
                    return
                }
                else -> out.open(depth, "onlyWhere(${bound(check.facts)})") { chains.chain(intent, ACT, it) }
            }
        }
    }

    /** An If that leaves the member where its [check] holds, saying so; else the [rest] of the member's steps. */
    private fun left(intent: Step.Intent, check: Chain.Check, rest: List<Step>, depth: Int) {
        val leaving = kotlinString(fill(intent.words), slots = true)
        val leaves = "unless(${bound(check.facts)}, leaving = $leaving)"
        if (rest.isEmpty()) return out.line(depth, "$leaves {}")
        out.open(depth, leaves) { children(rest, it) }
    }

    /** A step with no If: a Once, or a chain of acts. */
    private fun plain(intent: Step.Intent, depth: Int) {
        val once = intent.once
        if (once != null) waited(intent, once, depth) else chains.chain(intent, ACT, depth)
    }

    /** A Once: its wait for the act decided, its act's chain, and its Otherwise's, told why the wait gave up. */
    private fun waited(intent: Step.Intent, once: Once, depth: Int) {
        val relation = decided.waits[intent] ?: return out.said(depth, "once ${fill(once.condition)}: not decided")
        val giveUp = once.giveUp?.after?.let { ", giveUp = ${duration(it)}" }.orEmpty()
        if (decided.acts[intent to OTHERWISE] == null) {
            out.open(depth, "once(${quoted(relation)}$giveUp)") { chains.chain(intent, ACT, it) }
        } else {
            out.line(depth, "once(${quoted(relation)}$giveUp, otherwise = { why ->")
            chains.chain(intent, OTHERWISE, depth + 1)
            out.line(depth, "}) {")
            chains.chain(intent, ACT, depth + 1)
            out.line(depth, "}")
        }
    }

    private fun quoted(text: String) = kotlinString(text, slots = false)

    private companion object {
        const val ACT = "act"
        const val OTHERWISE = "otherwise"
    }
}

/** Words that are one input alone, `{name}`. */
private val INPUT = Regex("""\{(\w+)}""")

/**
 * A Whenever's `whenever`: who made its members and what they say, each an input where its words name one, and what
 * they hold.
 */
private fun whenever(step: Step.Whenever, match: Chain.Check?): String {
    fun said(words: String) = INPUT.matchEntire(words)?.groupValues?.get(1) ?: kotlinString(words, slots = false)
    val named = listOfNotNull(
        step.by?.let { "by = ${said(it)}" },
        step.says?.let { "says = ${said(it)}" },
        match?.let { "has = ${bound(it.facts)}" },
    )
    return if (named.isEmpty()) "whenever" else "whenever(${named.joinToString(", ")})"
}

/**
 * Each step's chains of acts, as [decided], written to [out]: each read and act as decided, each check setting
 * whether the acts after it are left undone here; an act that opens holds the parts after it in its block.
 */
internal class ChainScript(
    private val decided: Decisions,
    private val out: ScriptLines,
    private val fill: (String) -> String,
) {
    private var chains = 0

    /** [intent]'s chain for [part], at [depth]; a chain with a check has a variable of its own for it. */
    fun chain(intent: Step.Intent, part: String, depth: Int) {
        val chain = decided.acts[intent to part]
            ?: return out.said(depth, "${fill(intent.words)}: not decided in this run")
        // A check written as an actOnce needs no variable; any other does.
        val guarded = chain.parts.indices.any { at ->
            chain.parts[at] is Chain.Check && saidOnce(chain.parts, at - 1) == null
        }
        val undone = if (guarded) "undone${++chains}" else null
        undone?.let { out.line(depth, "var $it = false") }
        parts(chain.parts, undone, depth)
        if (!chain.complete) out.said(depth, "${fill(intent.words)}: decided no further in this run")
    }

    /** [parts] at [depth]: once a check is made, each part after it is done only where [undone] is false. */
    private fun parts(parts: List<Chain.Part>, undone: String?, depth: Int) {
        var guard = ""
        var at = 0
        while (at < parts.size) {
            val part = parts[at]
            val next = parts.getOrNull(at + 1) as? Chain.Check
            val once = saidOnce(parts, at)
            if (once != null) {
                out.line(depth, guard + once)
                at += ONCE_PARTS
                continue
            }
            if (part is Chain.Act && part.opens) {
                out.open(depth, "$guard${reach(part.chosen)}") { parts(parts.drop(at + 1), undone, it) }
                return
            }
            out.line(depth, guard + when {
                part is Chain.Check -> "$undone = holds(${bound(part.facts)})"
                next != null -> "${reach((part as Chain.Act).chosen)} { $undone = holds(${bound(next.facts)}) }"
                else -> "${reach((part as Chain.Act).chosen)} {}"
            })
            if (part is Chain.Check || next != null) guard = "if (!$undone) "
            at += if (part is Chain.Act && next != null) 2 else 1
        }
    }
}
