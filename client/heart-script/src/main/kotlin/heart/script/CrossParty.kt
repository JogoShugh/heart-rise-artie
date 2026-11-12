package heart.script

/**
 * A goal's steps that cross parties and wait on their answers (goal-rise.feature), done with no model: `At {party},
 * …` enters that party's entry and does the rest there; a step that asks to be told, named `as NAME`, makes its act
 * with a reply the host keeps, by that name; `Once {NAME} …,` waits on that reply and does the rest holding it. Each
 * leaves a line of the [script] and its mark, so the script made from the run does the same with no model.
 */
internal class CrossParty(
    private val run: Run,
    private val script: MoveLines,
    private val marks: StepMarks,
    private val fill: (String) -> String,
) {
    /** The steps whose ask was made: one reply each. */
    private val asked = mutableSetOf<Step>()

    /** [step] at [here], in the block on line [opener]: entered, waited on, or done as it is by [intent]. */
    fun done(step: Step.Intent, here: Here, opener: Int?, intent: (Step.Intent, Here, Int?) -> Unit) {
        val at = AT.matchEntire(step.words.trim())
        val reply = step.once?.condition?.let { ONCE.find(it) }?.groupValues?.get(1)
            ?.takeIf { run.replied.containsKey(it) }
        when {
            at != null -> {
                val (party, rest) = at.destructured
                val line = script.written("enter(\"{$party}\") {", opener)
                marks.put(line, "enter", "\${$party}")
                val traced = run.trace.size
                here.enter(fill("{$party}")) {
                    run.trace.getOrNull(traced)?.opens = true
                    done(step.saying(rest), this, line, intent)
                }
            }
            reply != null -> {
                val line = script.written("onceAnswered(\"$reply\") {", opener)
                marks.put(line, "once-answered", reply)
                val traced = run.trace.size
                here.onceAnswered(reply) {
                    run.trace.getOrNull(traced)?.opens = true
                    intent(step, this, line)
                }
            }
            else -> intent(step, here, opener)
        }
    }

    /**
     * The envelope of [step]'s first act when it asks to be told and names what it leads to: a reply the host
     * opens, kept by that name; empty for any other act.
     */
    fun envelope(step: Step.Intent, line: Int): Map<String, Any?> {
        val name = step.name
        val replies = run.replies
        val asks = name != null && replies != null && ASK.containsMatchIn(step.words) && asked.add(step)
        if (!asks) return emptyMap()
        val reply = checkNotNull(replies).open()
        run.replied[checkNotNull(name)] = reply
        marks.put(line, "ask", name)
        val links = mapOf("hr:reply-to" to mapOf("href" to reply.address), "hr:keys" to mapOf("href" to reply.keys))
        return mapOf("_links" to links)
    }

    /** The act on [step] asked to be told, and brought [request] into being: the host is told it answers the reply. */
    fun askedAt(step: Step.Intent, request: String) {
        val reply = step.name?.let { run.replied[it] } ?: return
        run.replies?.asked(reply, request)
    }

    private companion object {
        val AT = Regex("""(?i)^at\s+\{(\w+)}\s*,\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)
        val ONCE = Regex("""^\s*\{(\w+)}""")
        val ASK = Regex("""(?i)\bask(ing)?\s+to\s+be\s+told\b""")
    }
}

/** This step, its words [words]: the rest of an `At {party}, …` step, done at that party. */
private fun Step.Intent.saying(words: String) =
    Step.Intent(words, line, name, moves, reasoned, children, once, otherwise, condition, scenario)
