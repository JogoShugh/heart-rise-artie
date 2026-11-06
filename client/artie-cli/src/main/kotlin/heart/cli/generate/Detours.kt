package heart.cli.generate

/**
 * A goal run's detours, left out of the script made from it (spec/PLAN-PLAIN-TEXT-RUN.md): a selection, with what
 * is nested in it, that changed nothing, waited for nothing, checked nothing, and gave no later step a value. A
 * planner looks around as it goes; the script does only what the goal needed. A script's own run keeps every
 * selection, as its author wrote it.
 */
internal object Detours {
    /** The link a list names each of its members by (RFC 6573). */
    private const val ITEM = "item"

    fun trimmed(root: Node, capture: Capture): Node {
        val used = used(capture)
        prune(root, used)
        return root
    }

    private fun prune(node: Node, used: Set<Int>) {
        val members = members(node, used)
        node.children.removeAll { it !in members && !needed(it, used) }
        node.children.forEach { prune(it, used) }
    }

    /**
     * The members of the list [node]'s answer linked, when the run opened every one of them and acted in one at
     * least: each is kept, acted on or not, so the script goes through the list each time it runs. A run that opened
     * some of them only looked around.
     */
    private fun members(node: Node, used: Set<Int>): Set<Node> {
        val visits = node.children.filter { it.step.follows && it.step.relation == ITEM }
        val opened = visits.mapNotNull { it.step.chose["title"]?.toString() }.toSet()
        val every = node.step.items.isNotEmpty() && opened == node.step.items.toSet()
        return if (every && visits.any { needed(it, used) }) visits.toSet() else emptySet()
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
