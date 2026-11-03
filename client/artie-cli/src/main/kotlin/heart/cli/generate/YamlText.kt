package heart.cli.generate

import org.yaml.snakeyaml.Yaml

/*
 * YAML as a person reads it, for a script held as data (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, the format's rules):
 * block maps and lists only, never flow but for an empty list, `[]`; a scalar plain wherever it reads back as the
 * same text, else in double quotes; a note a comment after its value.
 */

/** A value written with [note] after it, as a comment. */
internal class Noted(val value: Any?, val note: String)

internal object YamlText {
    /** [entries], a map's, at the left margin. */
    fun lines(entries: Map<String, Any?>): List<String> = entries(entries, 0, dash = false)

    private fun entries(map: Map<*, *>, indent: Int, dash: Boolean): List<String> =
        map.entries.toList().flatMapIndexed { i, (key, value) ->
            val lead = if (!dash) "" else if (i == 0) "- " else "  "
            item(" ".repeat(indent) + lead + key, value, indent + if (dash) 2 else 0)
        }

    private fun item(head: String, value: Any?, indent: Int): List<String> = when {
        value is Noted -> listOf("$head: ${scalar(value.value)} # ${value.note}")
        value == null -> listOf("$head:")
        value is Map<*, *> && value.isEmpty() -> listOf("$head: {}")
        value is Map<*, *> -> listOf("$head:") + entries(value, indent + 2, dash = false)
        value is List<*> && value.isEmpty() -> listOf("$head: []")
        value is List<*> -> listOf("$head:") + value.flatMap { element(it, indent + 2) }
        else -> listOf("$head: ${scalar(value)}")
    }

    private fun element(value: Any?, indent: Int): List<String> =
        if (value is Map<*, *> && value.isNotEmpty()) {
            entries(value, indent, dash = true)
        } else {
            item(" ".repeat(indent) + "-", value, indent).map { it.replaceFirst("-:", "-") }
        }

    /** [value] as a YAML scalar: plain when it reads back as itself, else quoted. */
    fun scalar(value: Any?): String = when (value) {
        null -> "null"
        is Number, is Boolean -> value.toString()
        else -> value.toString().let { if (plain(it)) it else quoted(it) }
    }

    private fun plain(text: String): Boolean = text.isNotEmpty() && '\n' !in text &&
        runCatching { Yaml().load<Any?>("k: $text") == mapOf("k" to text) }.getOrDefault(false)

    private fun quoted(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\t", "\\t") + "\""
}
