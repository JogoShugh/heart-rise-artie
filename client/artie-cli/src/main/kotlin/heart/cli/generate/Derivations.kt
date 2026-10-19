package heart.cli.generate

/** Where a text stands in a script: its line and column, from 1. */
class Span(val line: Int, val column: Int, val text: String)

/**
 * One derivation: how the generator bound a value, or a line, and the rule of spec/PLAN-SCRIPT-GENERATOR.md that
 * bound it, with where it stands in the run's script ([source]) and in the generated one ([generated]).
 */
class Derivation(
    val key: String,
    val kind: String,
    val rule: String,
    val title: String,
    val why: String,
) {
    /** Its number, in the order the generated script first shows it; 0 until the script is written. */
    var n = 0
    val source = mutableListOf<Span>()
    val generated = mutableListOf<Span>()
}

/**
 * Every derivation of a script being written, and where each step of the run begins in both scripts. A generated
 * text is marked as it is written ([mark], [anchor]); [strip] takes the marks out once the script is whole, noting
 * where each stood. A value bound again the same way (the same key) keeps its derivation.
 */
class Derivations(private val source: List<String>?) {
    private val byKey = linkedMapOf<String, Derivation>()
    private val taken = mutableSetOf<Pair<Int, Int>>()
    private val anchors = mutableListOf<Triple<String, Int?, Int>>()
    private val anchorSource = mutableMapOf<String, Int?>()

    /** The derivations the script shows, by number. */
    val shown: List<Derivation> get() = byKey.values.filter { it.n > 0 }.sortedBy { it.n }

    /** Where each step of the run begins: what it is, its line in the run's script, its line in the generated one. */
    val rows: List<Triple<String, Int?, Int>> get() = anchors.sortedBy { it.third }

    /** Forgets what an earlier pass of the writer found. */
    fun clear() {
        byKey.clear()
        taken.clear()
        anchorSource.clear()
    }

    /** The derivation of [key], made the first time. */
    fun of(key: String, kind: String, rule: String, title: String, why: String): Derivation =
        byKey.getOrPut(key) { Derivation(key, kind, rule, title, why) }

    /** [text], marked as [derivation]'s, which stands at [spans] in the run's script (the first not yet taken). */
    fun mark(derivation: Derivation, text: String, spans: List<List<Span>> = emptyList()): String {
        spans.forEach { found -> found.firstOrNull { (it.line to it.column) !in taken }?.let { claim(derivation, it) } }
        return OPEN + derivation.key + MIDDLE + text + CLOSE
    }

    /** A line of the generated script that begins the step [at], which begins at [line] in the run's script. */
    fun anchor(at: String, line: Int?, text: String): String {
        anchorSource[at] = line
        return ANCHOR + at + MIDDLE + text
    }

    private fun claim(derivation: Derivation, span: Span) {
        taken += span.line to span.column
        if (derivation.source.none { it.line == span.line && it.column == span.column }) derivation.source += span
    }

    /** Each whole line of the run's script that [line] matches, trimmed: where it is declared, or set. */
    fun lines(line: Regex): List<Span> = source.orEmpty().mapIndexedNotNull { i, text ->
        if (!line.containsMatchIn(text)) null else Span(i + 1, text.length - text.trimStart().length + 1, text.trim())
    }

    /** [script] with its marks taken out, each derivation numbered and placed as the marks said. */
    fun strip(script: String): String {
        anchors.clear()
        var n = 0
        return script.lines().mapIndexed { i, line ->
            val out = StringBuilder()
            val open = ArrayDeque<Pair<Derivation, Int>>()
            var at = 0
            while (at < line.length) {
                when (line[at]) {
                    OPEN, ANCHOR -> {
                        val end = line.indexOf(MIDDLE, at)
                        val key = line.substring(at + 1, end)
                        if (line[at] == ANCHOR) {
                            anchors += Triple(key, anchorSource[key], i + 1)
                        } else {
                            open.addLast(byKey.getValue(key) to out.length)
                        }
                        at = end + 1
                    }
                    CLOSE -> {
                        val (derivation, from) = open.removeLast()
                        if (derivation.n == 0) derivation.n = ++n
                        derivation.generated += Span(i + 1, from + 1, out.substring(from))
                        at++
                    }
                    else -> out.append(line[at++])
                }
            }
            out.toString()
        }.joinToString("\n")
    }

    companion object {
        private const val OPEN = ''
        private const val MIDDLE = ''
        private const val CLOSE = ''
        private const val ANCHOR = ''
        private val MARKS = Regex("[][^]*|")

        /** How long [text] is once its marks are taken out. */
        fun visible(text: String): Int = text.replace(MARKS, "").length
    }
}
