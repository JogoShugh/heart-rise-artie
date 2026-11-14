package heart.script.test

import heart.script.Here
import heart.script.Refusal
import heart.script.Representation
import heart.script.Run
import heart.script.ScriptStopped
import heart.script.TraceStep

/** What a test script expects a step to answer, by meaning; [words] as its run report says it. */
sealed class Expected(val words: String) {
    internal abstract fun met(step: TraceStep, before: Representation): Boolean
}

/** The act was done, and the client holds what it led to. */
data object Done : Expected("done") {
    override fun met(step: TraceStep, before: Representation) = step.outcome == "done"
}

/** The act brought a new resource into being, and the client holds it. */
data object NewResource : Expected("new resource") {
    override fun met(step: TraceStep, before: Representation) = step.outcome == "new resource"
}

/** The act was done, and the resource is as it was: same address, same state. */
data object Unchanged : Expected("unchanged") {
    override fun met(step: TraceStep, before: Representation) =
        step.outcome == "done" && step.now?.address == before.address && step.now?.state == before.state
}

/** The step was refused as [kind], by either side. */
class Refused(val kind: Refusal) : Expected(kind.words) {
    override fun met(step: TraceStep, before: Representation) = step.refused?.kind == kind
}

val NotOffered = Refused(Refusal.NotOffered)
val Stale = Refused(Refusal.Stale)
val InvalidInput = Refused(Refusal.InvalidInput)
val PreconditionRequired = Refused(Refusal.PreconditionRequired)
val NotAuthenticated = Refused(Refusal.NotAuthenticated)
val Forbidden = Refused(Refusal.Forbidden)
val NotFound = Refused(Refusal.NotFound)
val NotAChoice = Refused(Refusal.NotAChoice)

/** A test failed: the [step] did not answer what the script expected. A run that ends here exits 1. */
class ExpectationNotMet(step: Int, reason: String) : ScriptStopped(step, "\"expect\"", reason)

/**
 * The first step in [block] must answer [expected]. Met, the script goes on, past a refusal it
 * expected; not met, it stops here, as a test failure. The trace marks the step with what was expected.
 */
fun Here.expect(expected: Expected, block: Here.() -> Unit) {
    val run = Run.current
    val mark = run.trace.size
    val number = run.step + 1
    val before = held
    val stop = try {
        block()
        null
    } catch (e: ScriptStopped) {
        e
    }
    val step = run.trace.getOrNull(mark)
    step?.expect = expected.words
    val unmet = when {
        step == null -> "no step was taken"
        !expected.met(step, before) -> "it was ${said(step)}"
        else -> null
    }
    unmet?.let { throw ExpectationNotMet(number, "expected ${expected.words}, but $it") }
    if (stop != null && step?.refused == null) throw stop
}

private fun said(step: TraceStep): String =
    step.refused?.let { "${it.kind.words}, refused by ${it.by.words}: \"${it.detail}\"" } ?: step.outcome
