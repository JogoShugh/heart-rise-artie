package heart.cli.generate

/*
 * A list's members a run visited one by one (spec/PLAN-COST-FOLLOWS-STATES.md, step 3): written one by one, by name,
 * a script serves those members alone. A list the run went through, every member of every page, is written as a
 * whenever over it, which goes through every member it lists, page after page, each time the script runs: each member
 * acted on by what it offers (when-offered), or, where each member is a list of its own, gone through the same way.
 */

/** The link a list names each of its members by (RFC 6573), as `whenever` follows it. */
private const val ITEM = "item"

/** The link a list names its next page by (RFC 8288), as `whenever` follows it to the list's end. */
private const val NEXT = "next"

/**
 * A list the run went through, page after page: its [members], each opened by its item link, and, when each member
 * is a list of its own, those lists' members gone through the same way ([inner]); null when each holds an act or none.
 */
internal class Loop(val members: List<Walked.Selection>, val inner: Loop?)

/**
 * The list [block] went through, as one loop: every member of its page, and of each page after it the run reached by
 * its next link, opened by its item link; each holding an act or none, each act sending what every other on its form
 * sent, or a list of its own gone through the same way. Null when [block] is anything else, and it is written as the
 * run made it: a page the run picked some members of, or a member [chosen] by its title, as an input gave it.
 */
internal fun loop(block: Block, chosen: (String) -> Boolean): Loop? =
    pages(block)?.takeUnless { members -> members.any { chosen(title(it)) } }?.let(::over)

/** The acts made on [members], one for each form, in the order the run first made them. */
internal fun cases(members: List<Walked.Selection>): List<Walked.Selection> =
    members.flatMap { it.block.statements }.filterIsInstance<Walked.Selection>().distinctBy { it.step.relation }

/** [members] as one loop: each holding an act or none, alike; or each a list of its own, or nothing, gone through. */
private fun over(members: List<Walked.Selection>): Loop? = when {
    members.isEmpty() || !members.all(::opened) -> null
    members.all(::acted) -> Loop(members, null).takeIf { alike(members) }
    else -> listsOf(members)?.let(::over)?.let { Loop(members, it) }
}

/** The members of the lists [members] are, all together; null when one holds anything but a list. */
private fun listsOf(members: List<Walked.Selection>): List<Walked.Selection>? {
    val lists = members.filter { it.block.statements.isNotEmpty() }.map { pages(it.block) }
    return if (lists.any { it == null }) null else lists.filterNotNull().flatten()
}

/**
 * The members [block]'s page and the pages after it list, when the run opened every one of them and followed the next
 * page from the last; null otherwise.
 */
private fun pages(block: Block): List<Walked.Selection>? {
    val selections = block.statements.filterIsInstance<Walked.Selection>()
        .takeIf { it.isNotEmpty() && it.size == block.statements.size }
    val next = selections?.last()?.takeIf { it.call == Call.Follow && it.step.relation == NEXT }
    val page = selections?.let { if (next == null) it else it.dropLast(1) }?.takeIf { every(block, it) }
    val rest = next?.let { pages(it.block) }
    return if (page == null || next != null && rest == null) null else page + rest.orEmpty()
}

/** Whether [page] is every member [block]'s list links; where the list isn't known, two at least, as a list has. */
private fun every(block: Block, page: List<Walked.Selection>): Boolean = page.all { it.step.relation == ITEM } &&
    if (block.listed.isEmpty()) page.size >= 2 else page.map(::title).toSet() == block.listed.toSet()

/** Whether [visit] opened a member by its item link, keeping nothing from it. */
private fun opened(visit: Walked.Selection): Boolean =
    visit.call == Call.Follow && visit.step.relation == ITEM && visit.step.opens && visit.block.keeps.isEmpty()

/** Whether [visit] made one act at the member it opened, or none. */
private fun acted(visit: Walked.Selection): Boolean {
    val inside = visit.block.statements
    val act = inside.singleOrNull() as? Walked.Selection
    return inside.isEmpty() || act != null && act.call == Call.Act && act.block.statements.isEmpty()
}

/** Whether every act on the same form sent what every other sent, but its form. */
private fun alike(members: List<Walked.Selection>): Boolean {
    val acts = members.flatMap { it.block.statements }.filterIsInstance<Walked.Selection>()
    val sent = acts.groupBy { it.step.relation }.values.map { same -> same.map { it.step.params - "relation" } }
    return sent.all { it.distinct().size == 1 }
}

/** The title [visit] followed its item link by. */
private fun title(visit: Walked.Selection): String = visit.step.params["title"]?.toString().orEmpty()
