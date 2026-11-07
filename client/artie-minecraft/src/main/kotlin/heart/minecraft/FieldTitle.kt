package heart.minecraft

/**
 * A title for a person, written from a member's or a field's name when nothing better names it, as HEART's web
 * view writes one: words split at underscores, hyphens, spaces and capitals, the first capitalised, as
 * `expectedHeadSha` is "Expected head sha". A word written all in capitals stays so. The name on the wire never
 * changes.
 */
internal object FieldTitle {
    private val BOUNDARY = Regex("[_\\-\\s]+|(?<=[a-z0-9])(?=[A-Z])")

    fun of(name: String): String = name.split(BOUNDARY).filter { it.isNotEmpty() }
        .joinToString(" ") { word -> if (word.length > 1 && word.all { it.isUpperCase() }) word else word.lowercase() }
        .replaceFirstChar { it.uppercase() }
}
