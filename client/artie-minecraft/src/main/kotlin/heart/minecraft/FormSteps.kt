package heart.minecraft

import heart.AffordanceForm
import heart.Choice
import heart.Choices
import heart.FormChoices
import heart.UriTemplate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * An act's form as dialogs of inputs. A dialog cannot change while it is open, so the form is asked for in
 * [steps]: a property whose choices depend on another field (its `hr:options` names it) comes a step after that
 * field. A property with a closed set, its choices from state or its `enum`, is one option to choose; any other
 * property is a line of text, sent as the type its schema gives.
 */
internal class FormSteps(form: AffordanceForm, private val choices: FormChoices?) {
    private val properties: Map<String, JsonObject> =
        ((form.schema["properties"] as? JsonObject) ?: JsonObject(emptyMap())).mapValues { it.value as JsonObject }
    private val declared = Choices.declared(form.schema)

    /**
     * The form's properties, a step at a time; one empty step when it has none. A field no other field's choices
     * depend on waits for the last step, so the first steps ask only what the choices after them need.
     */
    val steps: List<List<String>> = run {
        val needed = properties.keys.flatMap(::dependsOn).toSet()
        val levels = properties.keys.associateWith(::level)
        val last = levels.values.maxOrNull() ?: 0
        properties.keys.groupBy { if (it in needed) levels.getValue(it) else last }.toSortedMap().values.toList()
            .ifEmpty { listOf(emptyList()) }
    }

    /** The input for the property [name], with [filled] what the steps before filled in. */
    fun input(name: String, filled: JsonObject): Input {
        val property = properties.getValue(name)
        val label = (property["title"] as? JsonPrimitive)?.content ?: name
        val closed = closedSet(name, filled)
        return if (closed != null) {
            OptionInput(name, label, closed.map { Option(idOf(it.value), it.prompt) })
        } else {
            TextInput(name, label, (property["maxLength"] as? JsonPrimitive)?.intOrNull ?: TEXT_LENGTH)
        }
    }

    /** [filled] and what the player entered in [values], each as the JSON its property takes; empty text left out. */
    fun filled(filled: JsonObject, values: Map<String, String>): JsonObject {
        val entered = values.filter { (name, text) -> name in properties && text.isNotEmpty() }
            .mapValues { (name, text) -> valueOf(name, text, filled) }
        return JsonObject(filled + entered)
    }

    /** The value the option [text] stands for, or the text as its property's type. */
    private fun valueOf(name: String, text: String, filled: JsonObject): JsonElement =
        closedSet(name, filled)?.firstOrNull { idOf(it.value) == text }?.value ?: typed(properties.getValue(name), text)

    /** The property's closed set: its choices from state, else its `enum`; null when any text will do. */
    private fun closedSet(name: String, filled: JsonObject): List<Choice>? =
        if (name in declared) {
            choices?.of(filled)?.get(name).orEmpty()
        } else {
            (properties.getValue(name)["enum"] as? JsonArray)?.map { Choice(it, idOf(it)) }
        }

    /** How many steps come before [name]'s: one more than the latest of the fields its choices depend on. */
    private fun level(name: String, seen: Set<String> = emptySet()): Int =
        dependsOn(name).filter { it !in seen }.maxOfOrNull { level(it, seen + name) + 1 } ?: 0

    /** The other fields of this form that [name]'s `hr:options` names: in its pointer, or in a link no state fills. */
    private fun dependsOn(name: String): Set<String> {
        val options = declared[name] ?: return emptySet()
        val pointer = (options["pointer"] as? JsonPrimitive)?.content?.let(UriTemplate::names).orEmpty()
        val link = ((options["link"] as? JsonObject)?.get("href") as? JsonPrimitive)?.content
            ?.let(UriTemplate::names).orEmpty() - (options["templatePointers"] as? JsonObject)?.keys.orEmpty()
        return (pointer + link).filter { it in properties && it != name }.toSet()
    }

    private companion object {
        /** How long a line of text may be when its schema gives no maxLength. Minecraft's own default is 32. */
        const val TEXT_LENGTH = 256

        /** A value as an option's id, and as typed text: a string or number as it reads. */
        fun idOf(value: JsonElement): String =
            (value as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: "$value"

        /** [text] as the JSON its [property]'s type takes; as a string when it isn't one, for the schema check. */
        fun typed(property: JsonObject, text: String): JsonElement = when (typeOf(property)) {
            "integer" -> text.toLongOrNull()?.let(::JsonPrimitive)
            "number" -> text.toDoubleOrNull()?.let(::JsonPrimitive)
            "boolean" -> text.toBooleanStrictOrNull()?.let(::JsonPrimitive)
            else -> null
        } ?: JsonPrimitive(text)

        fun typeOf(property: JsonObject): String? = (property["type"] as? JsonPrimitive)?.content
    }
}
