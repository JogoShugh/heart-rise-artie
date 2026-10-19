package heart.script

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Read the held resource again until [condition] holds, at most [tries] times
 * [every] apart: for a server that settles a moment after an act. When it
 * never holds, the script stops here.
 */
fun Here.until(tries: Int = 10, every: Duration = 1.seconds, condition: Here.() -> Boolean) {
    val step = ++run.step
    for (attempt in 1..tries) {
        if (condition()) {
            // A step of its own, even when it held at once: a script made from the run waits here too.
            run.record(TraceStep.Asked("until", emptyMap()), "held", held)
            run.trace.last().waited = run.waitedFor(attempt - 1)
            return
        }
        if (attempt == tries) break
        run.pause(every)
        held = arrived(step, "\"until\"", navigator.visit(held.address))
    }
    throw ScriptStopped(step, "\"until\"", "\"the condition did not hold after $tries tries\"")
}

/**
 * Visit each member the held list links under [rel], page after page by
 * `next`, coming back to its page after each; then back where it began.
 */
fun Here.members(rel: String = "item", block: Here.() -> Unit) {
    val step = ++run.step
    val start = held.address
    while (true) {
        val page = held.address
        links(rel).forEach { title ->
            Here(navigator, run, arrived(step, "\"$rel\"", navigator.follow(rel, title))).block()
            held = arrived(step, "\"$rel\"", navigator.visit(page))
        }
        if (links(NEXT).isEmpty()) break
        held = arrived(step, "\"$NEXT\"", navigator.follow(NEXT, links(NEXT).first()))
    }
    if (held.address != start) held = arrived(step, "\"$rel\"", navigator.visit(start))
}

/** The IANA relation a list's next page is linked by (RFC 8288, RFC 5005). */
private const val NEXT = "next"
