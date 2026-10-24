package heart.cli.generate

/**
 * A goal run's watch in the script made from it (spec/PLAN-LISTENERS.md, slice 8): the script its decisions amount
 * to, as the capture keeps it, with no model in it; the imports its durations need; and, for each resource a step
 * named that it reads again, a variable holding that resource's address, set in the block of the act that led to it.
 */
class WatchLines(private val capture: Capture) {
    private val script = capture.watchScript

    /** The names the watch reads again by address, each a variable `<name>At`. */
    private val names: List<String> = script?.let { text ->
        NAMED.findAll(text).map { it.groupValues[1] }.distinct().toList()
    }.orEmpty()

    /** The imports the watch's durations need. */
    fun imports(): List<String> = UNITS.filter { script?.contains(".$it") == true }
        .map { "import kotlin.time.Duration.Companion.$it" }

    /** A variable for each address the watch reads again, declared at the top of the script's block. */
    fun declared(): List<String> = names.map { "var ${it}At = \"\"" }

    /** In the block of [step]'s act, the address it led to, kept when its step named it for the watch. */
    fun kept(step: Step, depth: Int): List<String> = capture.marks(step)["as"]?.toString()?.takeIf { it in names }
        ?.let { listOf(INDENT.repeat(depth) + "${it}At = held.address") }.orEmpty()

    /** The watch's lines, at [depth]. */
    fun lines(depth: Int): List<String> = script?.lines()?.map { INDENT.repeat(depth) + it }.orEmpty()

    private companion object {
        val NAMED = Regex("""\bat\((\w+)At\)""")
        val UNITS = listOf("minutes", "seconds")
        const val INDENT = "    "
    }
}
