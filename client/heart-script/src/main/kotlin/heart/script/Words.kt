package heart.script

/** How a value is found in words (Sources): whole, or as a paraphrase of them. */
internal object Words {
    private val WORD = Regex("[\\w-]+")

    /** [part] as a whole word: not inside a longer word or number. */
    fun whole(part: String) = Regex("(?<![\\w])" + Regex.escape(part) + "(?![\\w])")

    /**
     * Whether [value] is [words] again, whatever its case, its closing stop, or its words' endings: "Basil" for
     * "basil", "press" for "pressing", "Opened by a HEART script." for "saying it was opened by a HEART script".
     */
    fun paraphrases(value: String, words: String): Boolean {
        val said = words.lowercase()
        val wanted = value.lowercase().trim().trimEnd('.', '!', '?', ',', ';', ':')
        val saidWords = WORD.findAll(said).map { it.value }.toList()
        val wantedWords = WORD.findAll(wanted).map { it.value }.toList()
        val stems = wantedWords.isNotEmpty() && wantedWords.all { w -> saidWords.any { it.startsWith(w) } }
        return wanted.isNotEmpty() && (whole(wanted).containsMatchIn(said) || stems)
    }
}
