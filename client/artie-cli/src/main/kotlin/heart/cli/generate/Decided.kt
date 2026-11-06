package heart.cli.generate

/**
 * The acts of [block] that send a value read from the resource the block keeps, the one they act on, each with the
 * names that resource is kept by. Someone may change it between the read and the act: refused as stale, a decide
 * reads it again, keeps it again, and the value is worked out anew from what is there now. Decided from [capture]
 * alone, so the Kotlin script and the script held as data decide the same.
 */
internal fun decided(block: Block, capture: Capture): Map<Walked, Set<String>> {
    val sources = Sources(capture)
    val kept = block.keeps.groupBy({ it.step }, { it.keptAs })
    return block.statements.filterIsInstance<Walked.Selection>().filter { it.call == Call.Act }
        .associateWith { act ->
            act.step.values.values.flatMap { read(capture, sources, it.map()) }.flatMap { kept[it].orEmpty() }.toSet()
        }
        .filterValues { it.isNotEmpty() }
}

/**
 * The steps a value is read again from, as the bindings read it (Bindings.kt): none for an input or a goal run's
 * words; else its first source, or the source of each of its parts.
 */
private fun read(capture: Capture, sources: Sources, value: Map<*, *>): List<Int> {
    val from = value["from"].list()
    val held = sources.first(from)?.first
    return when {
        from.any { it.map()["input"] != null } || (capture.source != null && "words" in from) -> emptyList()
        held != null -> listOf(held)
        else -> from.flatMap { it.map()["parts"].map().values }.mapNotNull { sources.first(listOf(it))?.first }
    }
}

/** Whether no statement of [block] before the first of [decided] opens a block, which might read what they keep. */
internal fun movable(block: Block, decided: Set<Walked>): Boolean =
    block.statements.takeWhile { it !in decided }.none { it is Walked.Selection && it.step.opens }

/** [act] in a decide, as data, which keeps each of [kept] again before it. */
internal fun decide(kept: Set<String>, act: List<Map<String, Any?>>): Map<String, Any?> =
    mapOf("decide" to mapOf("steps" to kept.map { mapOf("keep-as" to it) } + act))
