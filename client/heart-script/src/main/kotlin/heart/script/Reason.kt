package heart.script

/**
 * Hand [intent] to the client's reasoner, which chooses one selection from what is offered here: a form with
 * its fields, or a link. The client checks the choice and makes it; the script then holds what it led to, as
 * after any act or follow. With a [block], visit it and return. A run must allow it (`--reasoner`).
 */
fun Here.reason(intent: String, block: (Here.() -> Unit)? = null) {
    val step = ++run.step
    if (!run.reasoning) {
        throw ScriptStopped(step, "\"reason\"", "\"reason(…) needs a reasoner, and this run has none\"")
    }
    go(step, "\"reason\"", navigator.reason(intent).answer, block)
}

/** What a reason step asked, for its trace: the words as said, and the choice made as its values. */
internal fun reasonAsked(intent: String, chosen: Choice?): TraceStep.Asked {
    val title = chosen?.title?.let { mapOf("title" to it) }.orEmpty()
    val values = chosen?.let { mapOf("relation" to it.relation) + it.fields + title }.orEmpty()
    return TraceStep.Asked(intent, values, mode = "intent")
}
