package com.artie.heart

/**
 * A title for a person, written from a member's or a field's name when nothing better names it: words split at
 * underscores, hyphens, spaces and capitals, the first capitalised, as `team_reviewers` is "Team reviewers" and
 * `expectedHeadSha` "Expected head sha". A word written all in capitals stays so: `htmlURL` is "Html URL". Only
 * what a person reads changes; the name on the wire never does.
 */
object FieldTitle {
    private val BOUNDARY = Regex("[_\\-\\s]+|(?<=[a-z0-9])(?=[A-Z])")

    fun of(name: String): String = name.split(BOUNDARY).filter { it.isNotEmpty() }
        .joinToString(" ") { word -> if (word.length > 1 && word.all { it.isUpperCase() }) word else word.lowercase() }
        .replaceFirstChar { it.uppercase() }
}
