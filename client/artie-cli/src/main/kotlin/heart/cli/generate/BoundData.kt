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

/** The variables [step]'s slots are read from, where an earlier answer of [capture] filled them, made now. */
internal fun Bindings.slotsRead(capture: Capture, step: Step) {
    val sources = Sources(capture)
    step.slots.values.map { it.map()["from"] }
        .filter { from -> from.list().none { it.map()["input"] != null } }
        .forEach { from -> sources.first(from)?.let { variable(it.first, it.second) } }
}

/**
 * [text], a check's, with each value a variable holds, found whole in it, written as the reference that reads it
 * again: "Seen in #10." checks "Seen in #${'$'}{issue.number}.", so another run checks its own.
 */
internal fun Bindings.readAgain(capture: Capture, text: String): String {
    var said = text
    for (variable in variables.values) {
        val value = capture.step(variable.step)?.read(variable.pointer)?.toString()
        if (value == null || value.length < 2) continue
        said = Regex("(?<![\\w$])${Regex.escape(value)}(?!\\w)").replace(said) { variable.reference }
    }
    return said
}

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

/** The path that reads [source] again when it is a member of an outcome kept by name: `smoke.of`; else null. */
internal fun keptPath(source: Any?): String? {
    val kept = source.map()["kept"]?.toString() ?: return null
    return kept + source.map()["pointer"].toString().split('/').drop(1).joinToString("") { ".$it" }
}

/**
 * This argument as data, read again from an outcome kept by name where the goal's step named it, `{smoke.of}`
 * ([info]: its value's sources): the whole value, unless an input gave it; else each scalar its `cited` names.
 */
internal fun Argument.citing(info: Map<*, *>): Argument {
    val from = info["from"].list()
    val whole = from.firstNotNullOfOrNull { keptPath(it) }?.takeIf { from.none { it.map()["input"] != null } }
    val cited = info["cited"].map()
    fun at(value: Any?, pointer: String): Any? = when (value) {
        is Map<*, *> -> value.mapValues { (key, member) -> at(member, "$pointer/$key") }
        is List<*> -> value.mapIndexed { i, member -> at(member, "$pointer/$i") }
        else -> keptPath(cited[pointer])?.let(::reference) ?: value
    }
    return when {
        whole != null -> Argument(field, expression, review, named, reference(whole))
        cited.isEmpty() -> this
        else -> Argument(field, expression, review, named, at(value, ""))
    }
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
