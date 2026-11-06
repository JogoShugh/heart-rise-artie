package heart.script

/** The cases of a [whenOffered], in order: each a relation, and what to do where the held resource offers it. */
class OfferedCases internal constructor() {
    internal val cases = mutableListOf<Pair<String, Here.() -> Unit>>()

    /** Where the held resource offers [rel], [block], if no case before it is offered. */
    fun case(rel: String, block: Here.() -> Unit) {
        cases += rel to block
    }
}

/**
 * The first of [cases] whose relation the held resource offers now (spec/PLAN-COST-FOLLOWS-STATES.md): the server
 * decided the state, and the offer says it. Where none is offered: the words, [intent] or a model's [inferred], with
 * the person's [fromIntent] they serve, chosen by the reasoner from what is offered, where the run falls back to its
 * words (`on-change: intent`) and the resource offers an act, so a script grows by use; else [otherwise], with no
 * model. A script held as data's `when-offered` runs through this, so the two behave alike.
 */
fun Here.whenOffered(
    intent: String? = null,
    inferred: String? = null,
    fromIntent: String? = null,
    otherwise: Here.() -> Unit = {},
    cases: OfferedCases.() -> Unit,
) {
    val named = OfferedCases().apply(cases).cases
    if (named.isEmpty()) throw ScriptStopped(run.step, "\"whenOffered\"", "\"whenOffered names no case\"")
    val case = named.firstOrNull { offers(it.first) }
    when {
        case != null -> case.second(this)
        (intent ?: inferred) != null && run.fallsBack && held.offered.isNotEmpty() ->
            act(intent = intent, inferred = inferred, fromIntent = fromIntent)
        else -> otherwise()
    }
}
