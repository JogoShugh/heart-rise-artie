package heart.script

/**
 * `act(rel, …)` as step [step]: the form [rel] with [fields], refused here, by the client, before anything is sent,
 * when the manifest doesn't offer it. The step keeps [intent], what the act is for, in words.
 */
internal fun Here.acted(step: Int, rel: String, fields: Map<String, Any?>, intent: String?, block: (Here.() -> Unit)?) {
    if (rel !in held.offered) {
        val reason = navigator.why(rel) ?: "no reason given"
        val refusal = Answer.Refused(Refusal.NotOffered, Side.CLIENT, reason)
        run.record(actAsked(rel, fields), "refused", held, refusal)
        run.trace.lastOrNull()?.intent = intent
        throw refused(step, "\"$rel\"", refusal)
    }
    val answer = navigator.act(rel, fields)
    // The step just traced is this act's: it keeps what the act is for.
    run.trace.lastOrNull()?.intent = intent
    go(step, "\"$rel\"", answer, block)
}

/**
 * `act(intent = …)` with no relation, as step [step]: hand [intent] to the client's reasoner, which chooses one
 * selection from what is offered here: a form with its fields, or a link. The client checks the choice and
 * makes it; the script then holds what it led to, as after any act or follow. With a [block], visit it and
 * return. A run must allow it (`--reasoner`).
 */
internal fun Here.reasoned(step: Int, intent: String, block: (Here.() -> Unit)?) {
    if (!run.reasoning) {
        throw ScriptStopped(step, "\"intent\"", "\"act(intent = …) needs a reasoner, and this run has none\"")
    }
    go(step, "\"intent\"", navigator.reason(intent).answer, block)
}

/** What a reason step asked, for its trace: the words as said, and the choice made as its values. */
internal fun reasonAsked(intent: String, chosen: Choice?): TraceStep.Asked {
    val title = chosen?.title?.let { mapOf("title" to it) }.orEmpty()
    val values = chosen?.let { mapOf("relation" to it.relation) + it.fields + title }.orEmpty()
    return TraceStep.Asked(intent, values, mode = "intent")
}
