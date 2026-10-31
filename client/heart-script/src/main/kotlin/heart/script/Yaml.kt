package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * YAML for a person to read (spec/PLAN-LINE-BY-LINE-REPORT.md, "The capture is YAML"): block style; a string
 * plain where YAML reads it back as the same string, single-quoted where it wouldn't (so never a backslash
 * escape), and a literal block (`|`) when it has line breaks; numbers, booleans and null as they are; an
 * empty object `{}` and an empty list `[]`.
 */
internal object Yaml {

    /** [documents] as one YAML stream, each a document of its own. */
    fun stream(documents: List<JsonElement>): String = documents.joinToString("") { "---\n" + document(it) }

    private fun document(node: JsonElement): String = buildString { block(node, 0, this) }

    private fun block(node: JsonElement, indent: Int, out: StringBuilder) {
        val pad = " ".repeat(indent)
        when {
            node is JsonObject && node.isNotEmpty() -> node.forEach { (key, value) ->
                out.append(pad).append(key(key)).append(':')
                member(value, indent, out)
            }
            node is JsonArray && node.isNotEmpty() -> node.forEach { item ->
                if (item is JsonObject && item.isNotEmpty()) {
                    // An object in a list starts on the dash's line: "- input: owner".
                    val inner = StringBuilder().also { block(item, indent + 2, it) }
                    out.append(pad).append("- ").append(inner.substring(indent + 2))
                } else {
                    out.append(pad).append('-')
                    member(item, indent, out)
                }
            }
            else -> out.append(pad).append(scalar(node)).append('\n')
        }
    }

    /** What follows a key's colon or a list item's dash: a scalar on the line, or a block below it. */
    private fun member(value: JsonElement, indent: Int, out: StringBuilder) {
        val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
        when {
            value is JsonObject && value.isNotEmpty() || value is JsonArray && value.isNotEmpty() -> {
                out.append('\n')
                block(value, indent + 2, out)
            }
            text != null && '\n' in text -> {
                out.append(if (text.endsWith('\n')) " |\n" else " |-\n")
                text.removeSuffix("\n").lines().forEach { out.append(" ".repeat(indent + 2)).append(it).append('\n') }
            }
            else -> out.append(' ').append(scalar(value)).append('\n')
        }
    }

    private fun scalar(node: JsonElement): String = when (node) {
        is JsonNull -> "null"
        is JsonObject -> "{}"
        is JsonArray -> "[]"
        is JsonPrimitive -> if (node.isString) string(node.content) else node.content
    }

    private fun key(name: String): String = if (PLAIN_KEY.matches(name)) name else quoted(name)

    /** A string, plain when YAML reads it back unchanged as a string; else single-quoted. */
    private fun string(text: String): String = if (plain(text)) text else quoted(text)

    private fun plain(text: String): Boolean =
        text.isNotEmpty() && text.trim() == text && text.first() !in SPECIAL &&
            ": " !in text && " #" !in text && !text.endsWith(':') &&
            !RESERVED.matches(text) && !NUMBER.matches(text) && !READ_AS_OTHER.containsMatchIn(text)

    private fun quoted(text: String) = "'" + text.replace("'", "''") + "'"

    /** Characters a plain scalar may not begin with. */
    private const val SPECIAL = "-?:,[]{}#&*!|>'\"%@`"
    private val PLAIN_KEY = Regex("^[A-Za-z_/][\\w /.:-]*$")
    private val RESERVED = Regex("^(?i:true|false|yes|no|on|off|null|~|y|n)$")
    /** What YAML 1.1 readers take for something else: a timestamp (2026-10-18…), a base-60 number (12:30). */
    private val READ_AS_OTHER = Regex("^\\d{4}-\\d\\d?-\\d\\d?([Tt ]|$)|^[-+]?\\d+(:[0-5]?\\d)+(\\.\\d*)?$")
    private val NUMBER = Regex("^[-+]?(\\d[\\d_]*(\\.\\d*)?|\\.\\d+)([eE][-+]?\\d+)?$|^0x[0-9a-fA-F]+$|^\\.(inf|nan)$")
}
