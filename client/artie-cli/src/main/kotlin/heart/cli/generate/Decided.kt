package heart.cli.generate

/**
 * The acts of [block] that send a value read from the resource the block keeps, the one they act on, each with the
 * names that resource is kept by. Someone may change it between the read and the act: refused as stale, a decide
 * reads it again, keeps it again, and the value is worked out anew from what is there now.
 */
internal fun decided(block: Block, bindings: Bindings): Map<Walked, Set<String>> {
    val keptAs = block.keeps.map { it.keptAs }.toSet()
    val kept = bindings.variables.values.filter { it.keptAs in keptAs }.associate { it.name to it.keptAs }
    return block.statements.filterIsInstance<Walked.Selection>().filter { it.call == Call.Act }
        .associateWith { act -> bindings.arguments(act.step).flatMap { it.named.values }.mapNotNull(kept::get).toSet() }
        .filterValues { it.isNotEmpty() }
}

/** Whether no statement of [block] before the first of [decided] opens a block, which might read what they keep. */
internal fun movable(block: Block, decided: Set<Walked>): Boolean =
    block.statements.takeWhile { it !in decided }.none { it is Walked.Selection && it.step.opens }

/** [act] in a decide, which keeps each of [kept] again before it. */
internal fun decide(kept: Set<String>, act: List<Map<String, Any?>>): Map<String, Any?> =
    mapOf("decide" to mapOf("steps" to kept.map { mapOf("keep-as" to it) } + act))
