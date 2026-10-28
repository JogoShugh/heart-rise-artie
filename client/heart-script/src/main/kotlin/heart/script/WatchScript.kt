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
 * where the script stands as it begins; [words] puts the inputs and names into words, and says a goal's and a
 * model's words as the script does.
 */
internal class WatchScript(
    private val decided: Decisions,
    private val entry: String,
    private val named: String?,
    private val words: StepWatch.Host,
) {
    private val fill: (String) -> String = words::fill
    private val out = ScriptLines()
    private val chains = ChainScript(decided, out, fill, words::spoken)

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
        val ends = decided.ends ?: return "{ that(false) }"
        return if (named == null) "{ ${facts(ends.facts)} }" else "{ at(${quoted(named)}) { ${facts(ends.facts)} } }"
    }

    /** One look of [step]: its list reached as decided, and each member answered past the guards that match it. */
    private fun look(step: Step.Whenever, depth: Int) {
        val list = decided.lists[step] ?: return out.said(depth, "${fill(step.match)}: no list decided in this run")
        val match = decided.matches[step].takeUnless { step.saidByWords }
        // A Whenever says what to watch for, not what to do: the list keeps the planner's words, from the Whenever's.
        val watched = kotlinString(words.spoken(step.match), slots = true)
        val listed = list.said?.let { kotlinString(words.unfilled(it), slots = true) }
        val said = listed?.let { "inferred = $it, fromIntent = $watched" } ?: "intent = $watched"
        out.open(depth, reach(list, said)) { inside ->
            out.open(inside, "whenever") { member ->
                if (step.saidByWords || match != null) {
                    guards(step, match).forEach { out.line(member, it) }
                    children(step.children, member)
                } else {
                    out.said(member, "${fill(step.match)}: no member matched in this run")
                }
            }
        }
    }

    /** The steps under a Whenever, at a member: an If that leaves it is a guard before the steps after it. */
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
                else -> out.open(depth, "given { ${facts(check.facts)} } then") { chains.chain(intent, ACT, it) }
            }
        }
    }

    /** An If that leaves the member where its [check] holds, saying so: a guard, then the [rest] of the steps. */
    private fun left(intent: Step.Intent, check: Chain.Check, rest: List<Step>, depth: Int) {
        val leaving = kotlinString(fill(intent.words), slots = true)
        out.line(depth, "leaveWhere($leaving) { ${facts(check.facts)} }")
        children(rest, depth)
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
        val otherwise = intent.otherwise
        if (otherwise != null && otherwise.reasoned) {
            // The model's every time: asked in the step's words, with the server's reason.
            val asked = kotlinString(gaveUpIntent(fill(otherwise.words), WHY_MARK), slots = true)
            out.line(depth, "once(${quoted(relation)}$giveUp, otherwise = { why ->")
            out.line(depth + 1, "act(intent = ${asked.replace(WHY_MARK, WHY_SLOT)})")
            out.line(depth, "}) {")
            chains.chain(intent, ACT, depth + 1)
            out.line(depth, "}")
        } else if (decided.acts[intent to OTHERWISE] == null) {
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
}

/** The parts of a step a chain is decided for: its words, and its Otherwise. */
private const val ACT = "act"
private const val OTHERWISE = "otherwise"

/** Where the server's reason goes in a reasoned Otherwise's words, until the script's `why` is put there. */
private const val WHY_MARK = "\u0000why\u0000"

/** Words that are one input alone, `{name}`. */
private val INPUT = Regex("""\{(\w+)}""")

/**
 * A Whenever's guards, the first lines of its member's turn: who made it and what it says, each an input where its
 * words name one, and what it holds, as the match decided.
 */
private fun guards(step: Step.Whenever, match: Chain.Check?): List<String> {
    fun said(words: String) = INPUT.matchEntire(words)?.groupValues?.get(1) ?: kotlinString(words, slots = false)
    return listOfNotNull(
        step.by?.let { "madeBy(${said(it)})" },
        step.says?.let { "saying(${said(it)})" },
        match?.let { "has { ${facts(it.facts)} }" },
    )
}

/**
 * Each step's chains of acts, as [decided], written to [out]: each read and act as decided, each check setting
 * whether the acts after it are left undone here; an act that opens holds the parts after it in its block.
 */
internal class ChainScript(
    private val decided: Decisions,
    private val out: ScriptLines,
    private val fill: (String) -> String,
    private val spoken: (String) -> String = fill,
) {
    /**
     * [intent]'s chain for [part], at [depth]. A chain with a check is a `doing` block in the intent's words, each
     * check a `doneWhere` that ends it where the rest is done already.
     */
    fun chain(intent: Step.Intent, part: String, depth: Int) {
        val chain = decided.acts[intent to part]
            ?: return out.said(depth, "${fill(intent.words)}: not decided in this run")
        // A check written as an actOnce needs no block; any other does.
        val guarded = chain.parts.indices.any { at ->
            chain.parts[at] is Chain.Check && saidOnce(chain.parts, at - 1) == null
        }
        val words = if (part == OTHERWISE) intent.otherwise?.words ?: intent.words else intent.words
        // The step's words, the goal's, are the intent of each act the chain makes; a link it follows is a way there.
        val said = "intent = " + kotlinString(spoken(words), slots = true)
        if (guarded) {
            out.open(depth, "doing(${kotlinString(fill(words), slots = false)})") { parts(chain.parts, it, said) }
        } else {
            parts(chain.parts, depth, said)
        }
        if (!chain.complete) out.said(depth, "${fill(intent.words)}: decided no further in this run")
    }

    /**
     * [parts] at [depth]: each check a doneWhere on what the act before it reached, or on what is held; each act
     * with the step's words, [said], as its intent.
     */
    private fun parts(parts: List<Chain.Part>, depth: Int, said: String) {
        var at = 0
        while (at < parts.size) {
            val part = parts[at]
            val next = parts.getOrNull(at + 1) as? Chain.Check
            val once = saidOnce(parts, at)
            if (once != null) {
                out.line(depth, once)
                at += ONCE_PARTS
                continue
            }
            if (part is Chain.Act && part.opens) {
                out.open(depth, reach(part.chosen, said)) { parts(parts.drop(at + 1), it, said) }
                return
            }
            out.line(depth, when {
                part is Chain.Check -> "doneWhere { ${facts(part.facts)} }"
                next != null -> "${reach((part as Chain.Act).chosen, said)} { doneWhere { ${facts(next.facts)} } }"
                else -> "${reach((part as Chain.Act).chosen, said)} {}"
            })
            at += if (part is Chain.Act && next != null) 2 else 1
        }
    }
}
