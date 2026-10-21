package heart.cli.generate

import heart.script.kotlinString

/** Kotlin source text for the values a generated script writes. */
internal object KotlinText {

    /**
     * [text] as a Kotlin string literal, nothing in it a template: a raw string, `"""…"""`, when it holds a quote,
     * so it reads with no escapes.
     */
    fun quoted(text: String): String = kotlinString(text, slots = false)

    /**
     * [text] as words a literal's slots may stand in, each `$` of its own a dollar, not a slot: `${'$'}` before a
     * name, which reads so in a plain string and a raw one alike.
     */
    fun dollars(text: String): String = DOLLAR.replace(text) { "\${'$'}" }

    /**
     * [text] as a Kotlin string literal with each of [names]' values, where it stands as a whole word, put back as
     * that name: `"fix-$stamp"`. The longest value is put back first, so a shorter one inside it is left alone.
     */
    fun templated(text: String, names: Map<String, String>): String {
        var pieces: List<Piece> = listOf(Piece(text, null))
        names.entries.filter { it.key.isNotEmpty() }.sortedByDescending { it.key.length }.forEach { (value, name) ->
            pieces = pieces.flatMap { piece ->
                if (piece.name != null) listOf(piece) else split(piece.text, value, name)
            }
        }
        val out = StringBuilder()
        pieces.forEachIndexed { i, piece ->
            val next = pieces.getOrNull(i + 1)?.takeIf { it.name == null }?.text?.firstOrNull()
            when {
                piece.name == null -> out.append(dollars(piece.text))
                next != null && next.isJavaIdentifierPart() -> out.append("\${").append(piece.name).append('}')
                else -> out.append('$').append(piece.name)
            }
        }
        return kotlinString(out.toString())
    }

    /** [value] as a whole word: not inside a longer word or number (`1` is not in `1.2`; `1018` is in `1018.md`). */
    fun whole(value: String): Regex = Regex("(?<!\\w)(?<!\\d\\.)" + Regex.escape(value) + "(?!\\w)(?!\\.\\d)")

    /**
     * Each slot of the Kotlin string literal [literal] (`"Relates to #$issue."`), by its name, as [value] fills it;
     * null when the value doesn't fit the literal. A `${…}` slot is named by what is inside it.
     */
    fun match(literal: String, value: String): Map<String, String>? {
        val raw = literal.startsWith(RAW)
        val body = (if (raw) literal.removeSurrounding(RAW) else literal.removeSurrounding("\"")).replace(OWN, "\u0001")
        val slot = Regex("""\$\{([^}]*)}|\$([A-Za-z_][A-Za-z0-9_]*)""")
        val names = slot.findAll(body).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
        val pattern = slot.split(body).joinToString("(.+?)") {
            Regex.escape((if (raw) it else unescape(it)).replace("\u0001", "$"))
        }
        val found = Regex("^$pattern$", RegexOption.DOT_MATCHES_ALL).matchEntire(value) ?: return null
        return names.zip(found.groupValues.drop(1)).toMap()
    }

    private fun unescape(text: String): String = text
        .replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\$", "$").replace("\\\\", "\\")

    /** The words of an `act(intent = …)` template, as its capture keeps them, with its slots taken out. */
    fun plain(template: String): String = template
        .replace(OWN, "$")
        .replace(Regex("""\$\{[^}]*}"""), "\u0000")
        .replace(Regex("""\$[A-Za-z_][A-Za-z0-9_]*"""), "\u0000")

    private fun split(text: String, value: String, name: String): List<Piece> {
        val found = whole(value).findAll(text).toList()
        if (found.isEmpty()) return listOf(Piece(text, null))
        val pieces = mutableListOf<Piece>()
        var at = 0
        found.forEach { match ->
            if (match.range.first > at) pieces += Piece(text.substring(at, match.range.first), null)
            pieces += Piece(value, name)
            at = match.range.last + 1
        }
        if (at < text.length) pieces += Piece(text.substring(at), null)
        return pieces
    }

    private class Piece(val text: String, val name: String?)

    private const val RAW = "\"\"\""

    /** A dollar of a literal's own, written as a raw string must: `${'$'}`. */
    private const val OWN = "\${'$'}"
    private val DOLLAR = Regex("""\$(?=[A-Za-z_{])""")
}
