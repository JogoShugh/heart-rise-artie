package heart.script

private const val RAW = "\"\"\""
private val DOLLAR = Regex("""\$(?=[A-Za-z_{])""")

/**
 * [text] as Kotlin source for a string, as a person would write it: a raw string, `"""…"""`, when the text holds a
 * quote, so it reads with no escapes; else a plain one. With [slots], each `$name` and `${…}` in [text] stays a slot;
 * without, a `$` is a dollar. Text a raw string can't hold, a line break or three quotes, is written plain.
 */
fun kotlinString(text: String, slots: Boolean = true): String {
    val raw = '"' in text && RAW !in text && '\n' !in text && '\t' !in text
    if (raw) return RAW + (if (slots) text else DOLLAR.replace(text) { "\${'$'}" }) + RAW
    val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"").let { if (slots) it else it.replace("$", "\\$") }
    return "\"" + escaped.replace("\n", "\\n").replace("\t", "\\t") + "\""
}
