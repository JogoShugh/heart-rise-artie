package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration

/*
 * The values of a watch as data (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, step 1), as WatchText writes them as Kotlin:
 * each value bound to where it came from, as the reference that reads it again, `${label}`, `${state.title}`,
 * `${why}`; any other value as it is.
 */

/** The server's reason a wait gave up, as data reads it. */
internal const val WHY = "\${why}"

/** [value] as data, read again where it came from: an input, the member's state, the reason a wait gave up. */
internal fun datum(value: Bound): Any? {
    val items = value.items
    val whole = value.whole
    val template = value.template
    return when {
        items != null -> items.map(::datum)
        whole != null -> reference(whole) ?: plain(value.kept)
        template != null -> template(template, value.keys) ?: plain(value.kept)
        else -> plain(value.kept)
    }
}

/** The reference that reads [key], a source of values; null for one a script can't read again. */
private fun reference(key: String): String? = when {
    key.startsWith("input:") -> "\${${key.removePrefix("input:")}}"
    key.startsWith("member:") -> "\${state.${key.removePrefix("member:")}}"
    key == "why" -> WHY
    else -> null
}

/** [template] with each `{n}` the reference that reads the nth of [keys]; null when one can't be read again. */
private fun template(template: String, keys: List<String>): String? {
    val reads = keys.map { reference(it) ?: return null }
    return reads.foldIndexed(template) { at, text, read -> text.replace("{$at}", read) }
}

/** [value] as plain data: text, a number, a truth, a list, or null. */
internal fun plain(value: Any?): Any? = when (value) {
    null -> null
    is JsonPrimitive -> if (value.isString) value.content else number(value.content)
    is JsonArray -> value.map(::plain)
    is List<*> -> value.map(::plain)
    is Number, is Boolean -> value
    else -> value.toString()
}

private fun number(text: String): Any =
    text.toLongOrNull() ?: text.toDoubleOrNull() ?: text.toBooleanStrictOrNull() ?: text

/** [duration] as data: whole minutes as `3m`, else seconds, `15s`. */
internal fun span(duration: Duration): String = if (duration.inWholeSeconds % SECONDS_IN_A_MINUTE == 0L) {
    "${duration.inWholeMinutes}m"
} else {
    "${duration.inWholeSeconds}s"
}

private const val SECONDS_IN_A_MINUTE = 60L
