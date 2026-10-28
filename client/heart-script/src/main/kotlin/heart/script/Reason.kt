package heart.script

/**
 * `act(rel, …)` as step [step]: the form [rel] with [fields], refused here, by the client, before anything is sent,
 * when the manifest doesn't offer it. The step keeps what the act is for, in words: [said].
 */
internal fun Here.acted(step: Int, rel: String, fields: Map<String, Any?>, said: Said, block: (Here.() -> Unit)?) {
    if (rel !in held.offered) {
        val reason = navigator.why(rel) ?: "no reason given"
        val refusal = Answer.Refused(Refusal.NotOffered, Side.CLIENT, reason)
        run.record(actAsked(rel, fields), "refused", held, refusal)
        run.trace.lastOrNull()?.said(said)
        throw refused(step, "\"$rel\"", refusal)
    }
    val answer = navigator.act(rel, fields)
    // The step just traced is this act's: it keeps what the act is for.
    run.trace.lastOrNull()?.said(said)
    go(step, "\"$rel\"", answer, block)
}

/**
 * What a step is for, in words: a person's, as a goal or a script wrote them ([intent]), or a model's, inferred while
 * it ran ([inferred]), with the person's words they serve, if any ([fromIntent]). Either an intent or inferred words
 * may be reasoned on; the trace keeps all three apart.
 */
class Said(val intent: String? = null, val inferred: String? = null, val fromIntent: String? = null) {
    /** The words to reason on: a person's, else a model's. */
    val words: String? get() = intent ?: inferred
}

/**
 * `act(intent = …)` with no relation, as step [step]: hand [intent] to the client's reasoner, which chooses one
 * selection from what is offered here: a form with its fields, or a link. The client checks the choice and
 * makes it; the script then holds what it led to, as after any act or follow. With a [block], visit it and
 * return. A run must allow it (`--reasoner`).
 */
internal fun Here.reasoned(step: Int, said: Said, block: (Here.() -> Unit)?) {
    val intent = checkNotNull(said.words)
    if (!run.reasoning) {
        throw ScriptStopped(step, "\"intent\"", "\"act(intent = …) needs a reasoner, and this run has none\"")
    }
    val before = run.trace.size
    val answer = navigator.reason(intent).answer
    // A model's words are kept as inferred on the reason step the client traced, so a script made from the run
    // says whose they were; a client that traces none is left as it is.
    if (said.intent == null && run.trace.size > before) run.trace[before].inferred = intent
    go(step, "\"intent\"", answer, block)
}

/** What a reason step asked, for its trace: the words as said, and the choice made as its values. */
internal fun reasonAsked(intent: String, chosen: Choice?): TraceStep.Asked {
    val title = chosen?.title?.let { mapOf("title" to it) }.orEmpty()
    val values = chosen?.let { mapOf("relation" to it.relation) + it.fields + title }.orEmpty()
    return TraceStep.Asked(intent, values, mode = "intent")
}
