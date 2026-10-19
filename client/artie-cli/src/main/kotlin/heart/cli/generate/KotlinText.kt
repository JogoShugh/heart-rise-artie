package heart.cli.generate

/** Kotlin source text for the values a generated script writes. */
internal object KotlinText {

    /** [text] as a Kotlin string literal, nothing in it a template. */
    fun quoted(text: String): String = "\"" + escape(text) + "\""

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
        val out = StringBuilder("\"")
        pieces.forEachIndexed { i, piece ->
            val next = pieces.getOrNull(i + 1)?.takeIf { it.name == null }?.text?.firstOrNull()
            when {
                piece.name == null -> out.append(escape(piece.text))
                next != null && next.isJavaIdentifierPart() -> out.append("\${").append(piece.name).append('}')
                else -> out.append('$').append(piece.name)
            }
        }
        return out.append('"').toString()
    }

    /** [value] as a whole word: not inside a longer word or number. */
    fun whole(value: String): Regex = Regex("(?<![\\w.])" + Regex.escape(value) + "(?![\\w]|\\.\\w)")

    /** The words of a reason(…) template with its slots taken out and its escapes undone. */
    fun plain(template: String): String = template
        .replace(Regex("""\$\{[^}]*}"""), "\u0000")
        .replace(Regex("""\$[A-Za-z_][A-Za-z0-9_]*"""), "\u0000")
        .replace("\\\"", "\"").replace("\\\\", "\\")

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

    private fun escape(text: String): String = text
        .replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
        .replace("\n", "\\n").replace("\t", "\\t")

    private class Piece(val text: String, val name: String?)
}
