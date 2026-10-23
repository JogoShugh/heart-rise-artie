package heart.cli.generate

import java.util.Base64

/** Kotlin string text for a bare script's values (spec/ideas/bare-runs.md). */
internal object BareText {
    /** [s] as a plain Kotlin string literal, nothing in it a template. */
    fun plain(s: String) = "\"" + escaped(s) + "\""

    /** [s] escaped for a Kotlin string literal: each backslash, quote, dollar and newline. */
    fun escaped(s: String) =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n")

    /** The text [s] encodes, when it is base64 of printable text, as GitHub takes a file's content. */
    fun encoded(s: String): String? = s.takeIf { BASE64.matches(it) && it.length % QUANTUM == 0 }
        ?.let { runCatching { String(Base64.getDecoder().decode(it)) }.getOrNull() }
        ?.takeIf { it.all { c -> c >= ' ' } && Base64.getEncoder().encodeToString(it.toByteArray()) == s }

    private const val QUANTUM = 4
    private val BASE64 = Regex("""^[A-Za-z0-9+/]{8,}={0,2}$""")
}
