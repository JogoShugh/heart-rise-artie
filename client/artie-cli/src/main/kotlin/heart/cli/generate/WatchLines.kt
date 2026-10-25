package heart.cli.generate

/**
 * A goal run's watch in the script made from it (spec/PLAN-LISTENERS.md, slice 8): the script its decisions amount
 * to, as the capture keeps it, with no model in it; the imports its durations need; and, for each resource a step
 * named that the watch reads again, `keepAs` in the block of the act that led to it, so the watch reads it by name.
 */
class WatchLines(private val capture: Capture) {
    private val script = capture.watchScript

    /** The names the watch reads again, as its facts say them: `at("<name>") { … }`. */
    private val names: List<String> = script?.let { text ->
        NAMED.findAll(text).map { it.groupValues[1] }.distinct().toList()
    }.orEmpty()

    /** The imports the watch's durations need. */
    fun imports(): List<String> = UNITS.filter { script?.contains(".$it") == true }
        .map { "import kotlin.time.Duration.Companion.$it" }

    /** In the block of [step]'s act, the resource it led to, kept by name when its step named it for the watch. */
    fun kept(step: Step, depth: Int): List<String> = capture.marks(step)["as"]?.toString()?.takeIf { it in names }
        ?.let { listOf(INDENT.repeat(depth) + "keepAs(\"$it\")") }.orEmpty()

    /** The watch's lines, at [depth]. */
    fun lines(depth: Int): List<String> = script?.lines()?.map { INDENT.repeat(depth) + it }.orEmpty()

    private companion object {
        val NAMED = Regex("""\bat\("(\w+)"\) \{""")
        val UNITS = listOf("minutes", "seconds")
        const val INDENT = "    "
    }
}
