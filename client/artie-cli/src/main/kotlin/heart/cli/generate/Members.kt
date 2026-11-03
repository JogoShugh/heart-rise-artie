package heart.cli.generate

/*
 * A list's members a run visited one by one (spec/PLAN-COST-FOLLOWS-STATES.md, step 3): written one by one, by name,
 * a script serves those members alone. Members of one list, each opened by its item link and acted on once at most,
 * are written as a whenever over the list, each member acted on by what it offers (when-offered).
 */

/** The link a list names each of its members by (RFC 6573), as `whenever` follows it. */
private const val ITEM = "item"

/**
 * The visits in [block] when they are the members of one list, visited one by one: at least two, each opened by its
 * item link, each holding one act or none, and each act sending what every other act on the same form sent. Null
 * when [block] is anything else, and it is written as the run made it.
 */
internal fun members(block: Block): List<Walked.Selection>? {
    val visits = block.statements.filterIsInstance<Walked.Selection>()
        .takeIf { it.size == block.statements.size && it.size >= 2 }
        ?.takeIf { visits -> visits.all(::visit) }
    val acts = visits.orEmpty().flatMap { it.block.statements }.filterIsInstance<Walked.Selection>()
    val alike = acts.groupBy { it.step.relation }.values.all { same -> same.map(::sent).distinct().size == 1 }
    return visits?.takeIf { alike }
}

/** The acts made on [members], one for each form, in the order the run first made them. */
internal fun cases(members: List<Walked.Selection>): List<Walked.Selection> =
    members.flatMap { it.block.statements }.filterIsInstance<Walked.Selection>().distinctBy { it.step.relation }

/** Whether [visit] opened a member by its item link and made one act there, or none. */
private fun visit(visit: Walked.Selection): Boolean {
    val inside = visit.block.statements
    val act = inside.singleOrNull() as? Walked.Selection
    val acted = inside.isEmpty() || act != null && act.call == Call.Act && act.block.statements.isEmpty()
    return visit.call == Call.Follow && visit.step.relation == ITEM && visit.step.opens &&
        visit.block.keeps.isEmpty() && acted
}

/** What [act] sent, but its form. */
private fun sent(act: Walked.Selection): Map<*, *> = act.step.params.filterKeys { it != "relation" }
