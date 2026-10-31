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

    /**
     * The watch's lines, at [depth]; with [intents] (`--intents`), each act that keeps its words written as them
     * alone, for the reasoner to choose, the rest of the watch as it was.
     */
    fun lines(depth: Int, intents: Boolean = false): List<String> = script?.lines()?.map { line ->
        val said = if (intents) WORDED.replace(line) { "${it.groupValues[1]}act(${it.groupValues[2]})" } else line
        INDENT.repeat(depth) + said
    }.orEmpty()

    /** How many of the watch's acts are written as words alone, for the reasoner to choose each time one is reached. */
    fun asked(intents: Boolean): Int = lines(0, intents).count { ASKED.containsMatchIn(it) }

    private companion object {
        val ASKED = Regex("""^\s*act\((?:intent|inferred) = """)
        val NAMED = Regex("""\bat\("(\w+)"\) \{""")

        /** An act with a relation and its words, the last of its arguments: `act("rel", …, intent = "…")`. */
        private const val TEXT = """(?:"(?:[^"\\]|\\.)*"|""" + "\"\"\".*?\"\"\")"

        /** `intent = "…"`, or `inferred = "…"` with the person's words it serves, `fromIntent = "…"`. */
        val WORDED = Regex(
            """^(\s*)act\("[^"]*"(?:, .*?)?, ((?:intent = $TEXT|inferred = $TEXT(?:, fromIntent = $TEXT)?))\)""",
        )
        val UNITS = listOf("minutes", "seconds")
        const val INDENT = "    "
    }
}
