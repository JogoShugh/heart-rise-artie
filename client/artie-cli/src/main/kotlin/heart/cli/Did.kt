package heart.cli

import heart.cli.ArtieCli.Ended
import heart.script.Run

/** What a finished run did: each relation it acted by, in the order it first acted, with how many times; its notes. */
internal class Did(val acted: Map<String, Int> = emptyMap(), val noted: Int = 0)

/**
 * How a run that finished ends: its steps, then what it did, counted. Each relation it acted by, a form it used and
 * the server took, in the order it first acted, with how many times; then how many notes it made. A caller that
 * reads only the end of a long run, a person or a model, knows what was done without reading every step.
 */
internal fun finished(run: Run): Ended {
    val acted = linkedMapOf<String, Int>()
    run.trace.filter { it.outcome in TAKEN }
        .mapNotNull { step -> step.used?.takeIf { it.kind == "form" }?.relation }
        .forEach { acted[it] = (acted[it] ?: 0) + 1 }
    val noted = run.notes.size
    val said = listOfNotNull(
        acted.entries.takeIf { it.isNotEmpty() }?.joinToString(", ", "acted: ") { "${it.key} ${it.value}" },
        "noted $noted".takeIf { noted > 0 },
    )
    val line = "finished, ${run.trace.size} steps" + said.joinToString("") { "; $it" }
    return Ended(0, "finished", line, run.trace.size, did = Did(acted, noted))
}

/** The outcomes of an act the server took. */
private val TAKEN = setOf("done", "new resource")
