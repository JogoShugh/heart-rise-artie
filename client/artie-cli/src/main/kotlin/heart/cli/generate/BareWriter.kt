package heart.cli.generate

import heart.cli.generate.BareText.declaration
import heart.cli.generate.BareText.encoded
import heart.cli.generate.BareText.escaped
import heart.cli.generate.BareText.plain

/**
 * A bare script's text (spec/ideas/bare-runs.md): the [run]'s writes, in order, and the reads whose answers they
 * send values from, each value an input, an earlier answer read by pointer, or as recorded.
 */
internal class BareWriter(private val run: BareRun, private val upstreamName: String) {
    private val inputs = run.capture.inputs.mapNotNull { (name, input) ->
        input["value"]?.toString()?.takeIf { it.isNotEmpty() }?.let { it to name }
    }.sortedByDescending { it.first.length }
    private val pattern = Regex(
        (inputs.map { "(?<![A-Za-z0-9])" + Regex.escape(it.first) + "(?![A-Za-z0-9])" } + NUMBERS).joinToString("|"),
    )
    private val lines = sortedMapOf<Int, String>()
    private val read = mutableSetOf<Int>()

    /** The reads kept, for a value a later call sends. */
    val reads: Int get() = lines.keys.count { it !in run.writes.map { (call, _) -> call.number } }

    fun write(): String {
        run.writes.forEach { (call, waited) ->
            lines[call.number] = call(call, if (waited) ", retry = $RETRIES" else "")
        }
        val declared = run.capture.inputs.map { (name, input) -> declaration(name, input) }
        val calls = lines.map { (n, line) -> "    " + (if (n in read) "val r$n = " else "") + line }
        return (listOf(HEAD.format(run.capture.name, upstreamName)) + declared + listOf("", "bare {") + calls + "}")
            .joinToString("\n") + "\n"
    }

    private fun call(call: UpstreamCall, retry: String): String {
        val body = call.body?.let { ", " + value(it, call.number) }.orEmpty()
        return "call(\"${call.method}\", ${text(call.url, call.number)}$body$retry)"
    }

    /** The answer value [value] can be read from before the call [before], as an expression; null when none. */
    private fun from(value: String, before: Int): String? = run.source(value, before)?.let { (source, pointer) ->
        read += source.number
        if (source.number !in lines) lines[source.number] = call(source, "")
        "r${source.number}" + pointer.split('/').drop(1)
            .joinToString("") { if (it.all(Char::isDigit)) "[$it]" else "[\"$it\"]" }
    }

    private fun value(v: Any?, before: Int): String = when (v) {
        is Map<*, *> -> "mapOf(" + v.entries.joinToString(", ") { (k, x) -> "\"$k\" to ${value(x, before)}" } + ")"
        is List<*> -> "listOf(" + v.joinToString(", ") { value(it, before) } + ")"
        is String -> text(v, before)
        is Number -> v.toString().takeIf { ID.matches(it) }?.let { from(it, before) } ?: v.toString()
        else -> v.toString()
    }

    /** [s] as a Kotlin string: each input and each value read from an answer put in, the rest as recorded. */
    private fun text(s: String, before: Int): String = whole(s, before) ?: templated(s, before)

    /** [s] when it is a whole value: a file's content in base64 with values in it, or an id or a sha read before. */
    private fun whole(s: String, before: Int): String? =
        encoded(s)?.let { decoded -> text(decoded, before).takeIf { it != plain(decoded) }?.let { "b64($it)" } }
            ?: s.takeIf { ID.matches(it) || SHA.matches(it) }?.let { from(it, before) }?.let { "$it.text" }

    private fun templated(s: String, before: Int): String {
        val out = StringBuilder()
        var last = 0
        pattern.findAll(s).forEach { m ->
            val expr = inputs.firstOrNull { it.first == m.value }?.second ?: from(m.value, before)?.let { "$it.text" }
            if (expr != null) {
                out.append(escaped(s.substring(last, m.range.first))).append("\${").append(expr).append('}')
                last = m.range.last + 1
            }
        }
        return "\"" + out + escaped(s.substring(last)) + "\""
    }

    private companion object {
        const val RETRIES = 30
        val NUMBERS = listOf("""(?<=#)\d{3,}""", """(?<=/)\d{3,}(?=/|$)""")
        val ID = Regex("""^\d{3,}$""")
        val SHA = Regex("""^[0-9a-f]{40}$""")
        const val HEAD = "// Made by artie generate --bare from %s and %s (spec/ideas/bare-runs.md): the calls the\n" +
            "// run came down to, sent straight to the service, with no entry, no forms, no validators and no checks."
    }
}
