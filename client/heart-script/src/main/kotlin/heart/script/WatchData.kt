package heart.script

/*
 * A watch's decided parts as data (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, step 1): the steps WatchScript writes as
 * Kotlin, in the vocabulary of a script held as data (spec/schemas/heart-script.draft.schema.json), kept in the
 * capture beside the Kotlin, so a generator can write them as YAML without reading Kotlin back. Each value is read
 * again where it came from by a reference, `${label}`, `${state.title}`, `${why}`: named, never evaluated.
 */

/** One step as data: a map of its keys. */
internal typealias DataStep = Map<String, Any?>

/** The data a watch's [decided] parts amount to; [entry], [named] and [words] as for [WatchScript]. */
internal class WatchData(
    private val decided: Decisions,
    private val entry: String,
    private val named: String?,
    private val words: StepWatch.Host,
) {
    private val chains = ChainData(decided, words::fill, words::spoken)

    /** The steps of [steps], Whenevers side by side. */
    fun steps(steps: List<Step.Whenever>): List<DataStep> {
        val single = steps.singleOrNull()
        val scope = single?.let { decided.scopes[it] }?.takeIf { it != entry }
        return when {
            single != null && scope != null -> listOf(mapOf("at" to scope, "steps" to listOf(watch(single))))
            single != null -> listOf(watch(single))
            else -> listOf(mapOf("listeners" to until() + ("listen" to steps.map(::listen))))
        }
    }

    private fun watch(step: Step.Whenever): DataStep = mapOf("watch" to until() + ("steps" to look(step)))

    private fun listen(step: Step.Whenever): DataStep {
        val from = decided.scopes[step]?.takeIf { it != entry }
        return mapOf("match" to words.fill(step.match)) + from.named("from") + ("steps" to look(step))
    }

    /** The Until's facts, each read where it names; none, and the watch runs its time. */
    private fun until(): DataStep {
        val ends = decided.ends ?: return emptyMap()
        return mapOf("until" to factData(ends.facts).map { fact -> named.named("at") + fact })
    }

    /** One look of [step]: its list reached as decided, and each member answered past the guards that match it. */
    private fun look(step: Step.Whenever): List<DataStep> {
        val list = decided.lists[step] ?: return emptyList()
        val match = decided.matches[step].takeUnless { step.saidByWords }
        val watched = words.spoken(step.match)
        val listed = list.said?.let(words::unfilled)
        val said = listed?.let { mapOf("inferred" to it, "from-intent" to watched) } ?: mapOf("intent" to watched)
        val answered = step.saidByWords || match != null
        val member = if (answered) guards(step, match) + children(step.children) else emptyList()
        return listOf(reached(list, said) + ("then" to listOf(mapOf("whenever" to member))))
    }

    /** The steps under a Whenever, at a member: an If that leaves it is a guard before the steps after it. */
    private fun children(steps: List<Step>): List<DataStep> {
        val out = mutableListOf<DataStep>()
        for ((at, step) in steps.withIndex()) {
            val intent = step as? Step.Intent ?: continue
            val check = decided.ifs[intent]
            when {
                intent.condition == null -> out += plain(intent)
                check == null -> Unit
                intent.leaves -> return out + left(intent, check) + children(steps.drop(at + 1))
                else -> out += mapOf("given" to factData(check.facts), "then" to chains.chain(intent, ACTED))
            }
        }
        return out
    }

    private fun left(intent: Step.Intent, check: Chain.Check): DataStep =
        mapOf("leave-where" to mapOf("say" to words.fill(intent.words), "facts" to factData(check.facts)))

    /** A step with no If: a Once, or a chain of acts. */
    private fun plain(intent: Step.Intent): List<DataStep> =
        intent.once?.let { waited(intent, it) } ?: chains.chain(intent, ACTED)

    /** A Once: its wait for the act decided, its act's chain, and its Otherwise's, told why the wait gave up. */
    private fun waited(intent: Step.Intent, once: Once): List<DataStep> {
        val relation = decided.waits[intent] ?: return emptyList()
        val giveUp = once.giveUp?.after?.let { mapOf("give-up" to span(it)) }.orEmpty()
        val otherwise = intent.otherwise
        val after = when {
            // The model's every time: asked in the step's words, part by part, with the server's reason.
            otherwise != null && otherwise.reasoned -> gaveUpIntents(words.spoken(otherwise.words), WHY_AT)
                .map { mapOf("intent" to it.replace(WHY_AT, WHY), "reasoned" to true) }
            decided.acts[intent to OTHERWISE_PART] == null -> null
            else -> chains.chain(intent, OTHERWISE_PART)
        }
        val waits = mapOf("offered" to relation) + giveUp + ("steps" to chains.chain(intent, ACTED))
        return listOf(mapOf("once" to waits + after.named("otherwise")))
    }

}

/** A Whenever's guards: who made the member and what it says, an input where the words name one; what it holds. */
private fun guards(step: Step.Whenever, match: Chain.Check?): List<DataStep> = listOfNotNull(
    step.by?.let { mapOf("made-by" to guarded(it)) },
    step.says?.let { mapOf("saying" to guarded(it)) },
    match?.let { mapOf("has" to factData(it.facts)) },
)

private fun guarded(words: String): String = INPUT.matchEntire(words)?.let { "\${${it.groupValues[1]}}" } ?: words

/**
 * Each step's chains of acts as data, as [ChainScript] writes them as Kotlin: each read and act as decided, each
 * check a `done-where` that leaves the rest undone where it holds; an act that opens holds the parts after it.
 */
internal class ChainData(
    private val decided: Decisions,
    private val fill: (String) -> String,
    private val spoken: (String) -> String,
) {
    /** [intent]'s chain for [part]: a `doing` in the intent's words when a check guards it. */
    fun chain(intent: Step.Intent, part: String): List<DataStep> {
        val chain = decided.acts[intent to part] ?: return emptyList()
        val guarded = chain.parts.indices.any { at ->
            chain.parts[at] is Chain.Check && saidOnce(chain.parts, at - 1) == null
        }
        val words = if (part == OTHERWISE_PART) intent.otherwise?.words ?: intent.words else intent.words
        val said = mapOf("intent" to spoken(words))
        val steps = parts(chain.parts, said)
        return if (guarded) listOf(mapOf("doing" to fill(words), "steps" to steps)) else steps
    }

    private fun parts(parts: List<Chain.Part>, said: Map<String, String>): List<DataStep> {
        if (parts.isEmpty()) return emptyList()
        val part = parts.first()
        val next = parts.getOrNull(1) as? Chain.Check
        return when {
            saidOnce(parts, 0) != null -> listOf(once(parts)) + parts(parts.drop(ONCE_PARTS), said)
            part is Chain.Act && part.opens -> listOf(acted(part, said, parts(parts.drop(1), said)))
            part is Chain.Check -> listOf(mapOf("done-where" to factData(part.facts))) + parts(parts.drop(1), said)
            next != null -> listOf(acted(part, said, listOf(mapOf("done-where" to factData(next.facts))))) +
                parts(parts.drop(2), said)
            else -> listOf(acted(part, said, emptyList())) + parts(parts.drop(1), said)
        }
    }

    private fun acted(part: Chain.Part, said: Map<String, String>, then: List<DataStep>): DataStep =
        reached((part as Chain.Act).chosen, said) + ("then" to then)

    /** "Say it, unless it's said already", as [saidOnce] finds it: the act, and the list it looks in first. */
    private fun once(parts: List<Chain.Part>): DataStep {
        val read = parts[0] as Chain.Act
        val act = reached((parts[2] as Chain.Act).chosen)
        val fields = act.filterKeys { it == "fields" }
        return mapOf("act-once" to act["act"]) + fields + ("listed-in" to read.chosen.relation)
    }
}

/** [chosen], made again as data: a follow of its link, or an act of its form with each value read again; [said]. */
internal fun reached(chosen: Chosen, said: Map<String, String> = emptyMap()): DataStep {
    if (chosen.kind == "link") return mapOf("follow" to chosen.relation) + chosen.title.named("title")
    val fields = chosen.values.mapValues { (_, value) -> datum(value) }
    return mapOf("act" to chosen.relation) + fields.takeIf { it.isNotEmpty() }.named("fields") + said
}

/** Facts as data, each value read again where it came from: a list's items each a `has`, any other an `is`. */
internal fun factData(facts: Map<String, Bound>): List<DataStep> = facts.entries.flatMap { (member, value) ->
    val listed = value.kept as? List<*>
    when {
        value.items != null -> value.items.map { mapOf("field" to member, "has" to datum(it)) }
        listed != null -> listed.map { mapOf("field" to member, "has" to plain(it)) }
        else -> listOf(mapOf("field" to member, "is" to datum(value)))
    }
}

/** This value under [key], or nothing when there is none. */
private fun Any?.named(key: String): DataStep = this?.let { mapOf(key to it) }.orEmpty()

private const val ACTED = "act"
private const val OTHERWISE_PART = "otherwise"

/** Where the server's reason goes in a reasoned Otherwise's words, until its reference is put there. */
private const val WHY_AT = "\u0000why\u0000"

/** Words that are one input alone, `{name}`. */
private val INPUT = Regex("""\{(\w+)}""")
