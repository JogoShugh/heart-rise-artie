package heart.cli.generate

/**
 * The step sources a generated script can read again: a member of the state of a step that brought it into the
 * run, the entry or a selection (never a wait's, a check's or a block end's re-read, which an older capture may
 * still name), and of the state's own members, not the address's (`/hr:address`) or an embedded resource's.
 */
internal class Sources(capture: Capture) {
    private val bringing: Set<Int> = capture.steps
        .filter { it.relation != null || it.say.startsWith("enter") }.map { it.number }.toSet()

    /** The source in [from] a script can read again from the earliest step, whatever order the capture lists. */
    fun first(from: Any?): Pair<Int, String>? = usable(from.list()).firstOrNull()

    /** Every source in [from] a script can read again, the earliest step first; one step's in the capture's order. */
    fun usable(from: List<*>): List<Pair<Int, String>> = from.mapNotNull { source ->
        val step = (source.map()["step"] as? Number)?.toInt()?.takeIf { it in bringing }
        val pointer = source.map()["pointer"]?.toString()?.takeUnless { at -> UNREAD.any { at.startsWith(it) } }
        if (step == null || pointer == null) null else step to pointer
    }.sortedBy { it.first }

    private companion object {
        /** Members a script doesn't read with state: the address's own, and embedded resources'. */
        val UNREAD = listOf("/hr:", "/_embedded/")
    }
}
