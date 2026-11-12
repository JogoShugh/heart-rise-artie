package heart.cli.generate

/*
 * A goal's steps marked as scenarios (goal-scenarios.feature): the run marks each act a scenario's step made, and the
 * script made from the run keeps those acts as that scenario, so it runs every scenario and says which passed.
 */

/** The scenario the run's act [walked] was part of, as its goal step was marked; null when none. */
internal fun scenarioOf(capture: Capture, walked: Walked): String? =
    (walked as? Walked.Selection)?.let { capture.marks(it.step)["scenario"]?.toString() }

/** A script a link hands over, run as the goal's step said (goal-rise.feature): on what, with what, kept by name. */
internal fun ranScript(marks: Map<*, *>): Map<String, Any?> =
    linkedMapOf<String, Any?>("run-script" to marks["run-script"]).also { made ->
        marks["on"]?.let { made["on"] = it }
        (marks["inputs"] as? Map<*, *>)?.takeIf { it.isNotEmpty() }?.let { made["inputs"] = it }
        made["as"] = marks["as"]
    }

/** An act's version and refusal, as [marks] say them: `as-of` a name, and `refused-as` the refusal it expected. */
internal fun versioned(marks: Map<*, *>): Map<String, String> =
    listOf("as-of", "refused-as").mapNotNull { key -> marks[key]?.let { key to it.toString() } }.toMap()

/** [made], an act, as the ask it was: its relation and fields but the envelope, and the reply it was asked on. */
internal fun asked(made: Map<String, Any?>, reply: String): List<Map<String, Any?>> {
    val ask = linkedMapOf<String, Any?>("ask" to made["act"])
    val fields = (made["fields"] as? Map<*, *>)?.filterKeys { it != "_links" }
    fields?.takeIf { it.isNotEmpty() }?.let { ask["fields"] = it }
    ask["reply"] = reply
    return listOf(ask)
}

/** The names the run's acts went as of, and its Verify: facts read: `${before.due}` reads `before`. */
internal fun namesRead(capture: Capture): Set<String> = capture.steps.flatMap { step ->
    val marks = capture.marks(step)
    // Every name a mark reads again, a fact's, a script's input's, an Otherwise's: `${name.member}`, `${name.@self}`.
    val read = listOf("facts", "inputs", "otherwise").flatMap { key ->
        NAME.findAll(marks[key]?.toString().orEmpty()).map { it.groupValues[1] }
    }
    val given = (marks["given"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("at")?.toString() }
    listOfNotNull(marks["as-of"]?.toString()) + read + given
}.toSet()

private val NAME = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)\.""")

/** [made], each statement's steps; those of one scenario, side by side, written as that scenario's. */
internal fun scenarios(made: List<Pair<String?, List<Map<String, Any?>>>>): List<Map<String, Any?>> {
    val written = mutableListOf<Map<String, Any?>>()
    made.forEach { (named, steps) ->
        val last = written.lastOrNull()
        when {
            named == null -> written += steps
            last?.get("scenario") == named ->
                written[written.lastIndex] = last + ("steps" to (last["steps"] as List<*>) + steps)
            else -> written += linkedMapOf("scenario" to named, "steps" to steps)
        }
    }
    return written
}
