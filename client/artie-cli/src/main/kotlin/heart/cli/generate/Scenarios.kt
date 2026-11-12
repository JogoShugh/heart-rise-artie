package heart.cli.generate

/*
 * A goal's steps marked as scenarios (goal-scenarios.feature): the run marks each act a scenario's step made, and the
 * script made from the run keeps those acts as that scenario, so it runs every scenario and says which passed.
 */

/** The scenario the run's act [walked] was part of, as its goal step was marked; null when none. */
internal fun scenarioOf(capture: Capture, walked: Walked): String? =
    (walked as? Walked.Selection)?.let { capture.marks(it.step)["scenario"]?.toString() }

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
