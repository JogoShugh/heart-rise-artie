package heart.cli.generate

import heart.cli.BadArguments
import heart.cli.generate.BareText.escaped

/**
 * What a watch run's calls to the service show (spec/ideas/bare-runs.md, "Rails"): for each act of its kept script,
 * the call it came down to, found under the spans of the steps that made it in the gateway's upstream record.
 */
internal class RailsRun(val capture: Capture, upstream: String) {
    private val run = BareRun(capture, upstream)
    private val from = capture.watchFrom ?: 1
    private val watching = capture.steps.filter { it.number >= from }

    /** The calls the step [step] came down to, each the service took. */
    private fun calls(step: Step): List<UpstreamCall> {
        val spans = step.traceparents.map { it.split('-')[2] }.toSet()
        return run.calls.filter { it.span in spans && it.taken }
    }

    /** The first write the service took for an act on [relation], in the watch. */
    fun act(relation: String): UpstreamCall =
        watching.filter { it.relation == relation }.flatMap(::calls).firstOrNull { it.writes } ?: missing(relation)

    /** The read that gave the list a `whenever` went through, reached by [relation]: the first read answered a list. */
    fun list(relation: String): UpstreamCall = watching.filter { it.relation == relation }.flatMap(::calls)
        .firstOrNull { it.method == "GET" && it.answer is List<*> } ?: missing(relation)

    /**
     * Each read of a member the watch visited, in order: where the gateway read it again and found it unchanged
     * (304), the last full answer to the same address.
     */
    val members: List<UpstreamCall> = watching.filter { it.relation == ITEM }.flatMap { step ->
        val spans = step.traceparents.map { it.split('-')[2] }.toSet()
        run.calls.filter { it.span in spans && it.method == "GET" }
    }.mapNotNull { read -> run.calls.lastOrNull { it.url == read.url && it.taken && it.answer is Map<*, *> } }

    /** The read of a member: the first. */
    fun member(): UpstreamCall = members.firstOrNull() ?: missing(ITEM)

    /** The write a reasoned step made, whose words began with [words]: its act, in the step's own words. */
    fun said(words: String): UpstreamCall = watching.filter { it.mode == "intent" && it.say.startsWith(words) }
        .flatMap(::calls).firstOrNull { it.writes } ?: throw BadArguments("$CANT the run made no call for \"$words…\"")

    /** The writes before the watch, in order, each with the name its step kept what it led to by, if any. */
    val before: List<Pair<String?, UpstreamCall>> = capture.stepped.flatMap { step ->
        calls(step).filter { it.writes }.map { capture.marks(step)["as"]?.toString() to it }
    }

    private fun missing(relation: String): Nothing =
        throw BadArguments("$CANT the run made no call for \"${escaped(relation)}\"")

    companion object {
        const val CANT = "can't bring the watch down to calls:"
        private const val ITEM = "item"
    }
}

/**
 * Where a value sent can be read from in a bare watch: the script's name for it, and the answers that name stood
 * for in the run. A [listed] source is one item of a list, read relative to the item.
 */
internal class Source(val name: String, val answers: List<Any?>, val listed: Boolean = false)

/**
 * A bare watch's values as Kotlin: each input put in by name, each id and sha read from where the script holds it
 * ([Source]), the rest as recorded. [inputs] are the run's.
 */
internal class Templates(inputs: Map<String, Map<*, *>>) {
    private val given = inputs.mapNotNull { (name, input) ->
        input["value"]?.toString()?.takeIf { it.isNotEmpty() }?.let { it to name }
    }.sortedByDescending { it.first.length }
    private val pattern = Regex(
        (given.map { "(?<![A-Za-z0-9])" + Regex.escape(it.first) + "(?![A-Za-z0-9])" } + NUMBERS).joinToString("|"),
    )

    /** [v] as Kotlin, read from [sources] where it can be; a string not read from anywhere is [words], when given. */
    fun value(v: Any?, sources: List<Source>, words: String? = null): String = when (v) {
        is Map<*, *> -> v.entries.joinToString(", ", "mapOf(", ")") { (k, x) ->
            "\"$k\" to ${value(x, sources, words)}"
        }
        is List<*> -> "listOf(" + v.joinToString(", ") { value(it, sources, words) } + ")"
        // In a reasoned call, words are the reasoner's to write, whatever values they happened to hold in the run.
        is String -> if (words != null && !SHA.matches(v)) "write($words)" else text(v, sources)
        is Number -> read(v.toString(), sources) ?: v.toString()
        else -> v.toString()
    }

    /** [s] as a Kotlin string: a sha read whole, else each input and each id put in, the rest as recorded. */
    fun text(s: String, sources: List<Source>): String =
        s.takeIf { SHA.matches(it) }?.let { read(it, sources) }?.let { "$it.text" } ?: templated(s, sources)

    private fun templated(s: String, sources: List<Source>): String {
        val out = StringBuilder()
        var last = 0
        pattern.findAll(s).forEach { m ->
            val expr = given.firstOrNull { it.first == m.value }?.second ?: read(m.value, sources)?.let { "$it.text" }
            if (expr != null) {
                out.append(escaped(s.substring(last, m.range.first))).append("\${").append(expr).append('}')
                last = m.range.last + 1
            }
        }
        return "\"" + out + escaped(s.substring(last)) + "\""
    }

    /** Where [value] stands in the first of [sources] that holds it, as an expression; null when none does. */
    private fun read(value: String, sources: List<Source>): String? = sources.firstNotNullOfOrNull { source ->
        source.answers.flatMap { leaves(it, "") }
            .filter { (_, leaf) -> leaf !is Boolean && leaf?.toString() == value }
            .map { (at, _) -> if (source.listed) at.substringAfter('/').substringAfter('/', "").let { "/$it" } else at }
            .minByOrNull { at -> at.split('/').count { it.isNotEmpty() } }
            ?.let { at -> source.name + at.split('/').drop(1).joinToString("") { "[\"$it\"]" } }
    }

    private fun leaves(node: Any?, at: String): List<Pair<String, Any?>> = when (node) {
        is Map<*, *> -> node.entries.flatMap { (k, v) -> leaves(v, "$at/$k") }
        is List<*> -> node.flatMapIndexed { i, v -> leaves(v, "$at/$i") }
        else -> listOf(at to node)
    }

    private companion object {
        val NUMBERS = listOf("""(?<=/)\d+(?=/|$|\?)""", """(?<=#)\d+""")
        val SHA = Regex("""^[0-9a-f]{40}$""")
    }
}
