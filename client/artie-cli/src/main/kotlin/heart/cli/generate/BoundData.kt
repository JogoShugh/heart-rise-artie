package heart.cli.generate

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * The values a generated script sends, as data (spec/PLAN-KEPT-SCRIPTS-AS-DATA.md, step 3): what Bindings writes as
 * Kotlin, written for a script held as data. A value read again is a reference, `${title}` an input,
 * `${issue.number}` a member of a resource kept; any other value is as it was sent. Nothing is ever code.
 */

/** The reference that reads [path]: a name, then the members under it. */
internal fun reference(path: String): String = "\${$path}"

/** The reference that reads [name], an input or a variable the script knows: `${title}`, `${issue.number}`. */
internal fun Bindings.referenceOf(name: String): String =
    variables.values.firstOrNull { it.name == name }?.reference ?: reference(name)

/** [value] as data, as a field of [type] takes it: text that is a number, a truth or JSON for those types, read so. */
internal fun data(value: Any?, type: String?): Any? = when {
    value !is String -> value
    type == "integer" -> value.toLongOrNull() ?: value
    type == "number" -> value.toDoubleOrNull() ?: value
    type == "boolean" -> value.toBooleanStrictOrNull() ?: value
    type == "array" || type == "object" -> parsed(value)?.let { plain(it, emptyMap()) } ?: value
    else -> value
}

/**
 * [text] as data, each part of it that [names] names ([read] gives the reference for a name) put as its reference:
 * a field of [type] array or object as its JSON's data, any other as text.
 */
internal fun filled(text: String, type: String?, names: Map<String, String>, read: (String) -> String): Any? {
    val structured = (type == "array" || type == "object")
    val parsed = if (structured) parsed(text) else null
    if (parsed != null) return plain(parsed, names.mapValues { read(it.value) })
    return KotlinText.pieces(text, names).joinToString("") { (part, name) -> name?.let(read) ?: part }
}

private fun parsed(text: String): JsonElement? = runCatching { Json.parseToJsonElement(text) }.getOrNull()

/** [element] as data, each string one of [read]'s texts its reference. */
private fun plain(element: JsonElement, read: Map<String, String>): Any? = when (element) {
    is JsonArray -> element.map { plain(it, read) }
    is JsonObject -> element.mapValues { plain(it.value, read) }
    is JsonNull -> null
    is JsonPrimitive -> when {
        element.isString -> read[element.content] ?: element.content
        else -> element.content.toLongOrNull() ?: element.content.toDoubleOrNull()
            ?: element.content.toBooleanStrictOrNull() ?: element.content
    }
}
