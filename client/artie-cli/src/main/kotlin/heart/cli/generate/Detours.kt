package heart.cli.generate

/**
 * A goal run's detours, left out of the script made from it (spec/PLAN-PLAIN-TEXT-RUN.md): a selection, with what
 * is nested in it, that changed nothing, waited for nothing, checked nothing, and gave no later step a value. A
 * planner looks around as it goes; the script does only what the goal needed. A script's own run keeps every
 * selection, as its author wrote it.
 */
internal object Detours {
    fun trimmed(root: Node, capture: Capture): Node {
        val used = used(capture)
        prune(root, used)
        return root
    }

    private fun prune(node: Node, used: Set<Int>) {
        node.children.removeAll { !needed(it, used) }
        node.children.forEach { prune(it, used) }
    }

    private fun needed(node: Node, used: Set<Int>): Boolean = steps(node).any { it.number in used || acts(it) }

    private fun steps(node: Node): Sequence<Step> = sequenceOf(node.step) + node.children.asSequence().flatMap(::steps)

    /** Whether [step] did something a script must do again: an act that changes, a wait, a check. */
    private fun acts(step: Step): Boolean = step.say == "until" || step.say == "check" ||
        (!step.follows && step.relation != null && step.chose["method"]?.toString()?.uppercase() != "GET")

    /** The steps a later value is read from, as the generator binds it: an input wins; else the earliest answer. */
    private fun used(capture: Capture): Set<Int> {
        val sources = Sources(capture)
        fun read(from: List<*>): List<Int> {
            if (from.any { it.map()["input"] != null }) return emptyList()
            val parts = from.flatMap { it.map()["parts"].map().values }.filter { it.map()["input"] == null }
            return listOfNotNull(sources.first(from)?.first) + parts.mapNotNull { sources.first(listOf(it))?.first }
        }
        return capture.steps.flatMap { step ->
            (step.values.values + step.slots.values).flatMap { read(it.map()["from"].list()) }
        }.toSet()
    }
}
