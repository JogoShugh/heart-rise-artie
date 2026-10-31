package heart

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/** One choice for a property: what is sent, and what is shown. */
data class Choice(val value: JsonElement, val prompt: String)

/** The choices of one offered [form] on the resource [owner] that offers it, read with [read]. */
class FormChoices(
    private val form: AffordanceForm,
    private val owner: HeartRepresentation,
    private val read: (String) -> JsonElement?,
) {
    /** Each property's choices, with [fields] filled so far. */
    fun of(fields: JsonObject): Map<String, List<Choice>> =
        Choices.declared(form.schema).mapValues { (_, options) -> Choices.of(options, owner, fields, read) }

    /** One sentence per value in [fields] that is not among its choices; empty when every one is. */
    fun violations(fields: JsonObject): List<String> = Choices.violations(form, owner, fields, read)

    /** The form's schema as a reasoner is shown it, with its choices as enums. */
    fun shownSchema(): JsonObject = Choices.shown(form, owner, read)
}

/**
 * Choices from state (HAL-SCHEMA-FORMS-HR Rule 8), resolved by deterministic
 * code, never by the reasoner. A property's `hr:options` says where its choices
 * are: a `pointer` into the owning resource's state, which may name other
 * fields as `{name}`, or a `link` to another resource, whose template is
 * filled as Rule 1 fills a target (template pointers, then fields, then the
 * state). A reader reads a link's answer as JSON. Only items whose members
 * equal every entry of `where` are choices; `valueField` is sent, `promptField`
 * shown.
 */
object Choices {
    const val KEYWORD = "hr:options"
    private val variable = Regex("""\{([^}]+)}""")
    private val none = JsonObject(emptyMap())

    /** The properties of [schema] that carry `hr:options`, with it. */
    fun declared(schema: JsonObject): Map<String, JsonObject> =
        ((schema["properties"] as? JsonObject) ?: none).mapNotNull { (name, property) ->
            (property as? JsonObject)?.get(KEYWORD)?.let { name to it.jsonObject }
        }.toMap()

    /** The choices [options] gives for the resource [owner], with [fields] filled so far. */
    fun of(
        options: JsonObject,
        owner: HeartRepresentation,
        fields: JsonObject,
        read: (String) -> JsonElement?,
    ): List<Choice> {
        val items = when {
            options["pointer"] != null -> JsonPointer.resolve(owner.state, pointer(options, fields))
            options["link"] != null -> linked(options, owner, fields, read)
            else -> null
        }
        val where = (options["where"] as? JsonObject) ?: none
        val value = (options["valueField"] as? JsonPrimitive)?.contentOrNull ?: "value"
        val prompt = (options["promptField"] as? JsonPrimitive)?.contentOrNull ?: "prompt"
        return ((items as? JsonArray) ?: emptyList())
            .filter { item -> where.all { (name, wanted) -> (item as? JsonObject)?.get(name) == wanted } }
            .mapNotNull { item ->
                when (item) {
                    is JsonObject -> item[value]?.let { Choice(it, (item[prompt] as? JsonPrimitive)?.content ?: "$it") }
                    is JsonPrimitive -> Choice(item, item.content)
                    else -> null
                }
            }
    }

    /** One sentence per sent value of [form] that is not among its choices; empty when every one is. */
    fun violations(
        form: AffordanceForm,
        owner: HeartRepresentation,
        fields: JsonObject,
        read: (String) -> JsonElement?,
    ): List<String> = declared(form.schema).mapNotNull { (property, options) ->
        fields[property]?.let { sent ->
            val allowed = of(options, owner, fields, read).map { it.value }
            val values = if (sent is JsonArray) sent.toList() else listOf(sent)
            values.firstOrNull { it !in allowed }?.let { "$property: $it is not one of the choices" }
        }
    }

    /**
     * [form]'s schema as the reasoner is shown it: each property with choices
     * gets them as an `enum` and, in its description, with their prompts. A
     * pointer that names another field is resolved for each value that
     * field's own `enum` allows ("when team is home: 4 (Ana)").
     */
    fun shown(form: AffordanceForm, owner: HeartRepresentation, read: (String) -> JsonElement?): JsonObject {
        val declared = declared(form.schema)
        if (declared.isEmpty()) return form.schema
        val properties = (form.schema["properties"] as JsonObject).toMutableMap()
        declared.forEach { (property, options) ->
            val groups = groups(options, form.schema, owner, read)
            val described = groups.joinToString("; ") { (label, choices) ->
                (label?.let { "$it: " } ?: "") + choices.joinToString(", ") { "${it.value} (${it.prompt})" }
            }
            val original = properties.getValue(property).jsonObject
            val text = listOfNotNull((original["description"] as? JsonPrimitive)?.content, "Choices: $described")
            val choices = JsonArray(groups.flatMap { it.second }.map { it.value }.distinct())
            properties[property] = JsonObject(
                original - KEYWORD + ("enum" to choices) + ("description" to JsonPrimitive(text.joinToString(" "))),
            )
        }
        return JsonObject(form.schema + ("properties" to JsonObject(properties)))
    }

    /** The choices, grouped by the values of the fields a pointer names. */
    private fun groups(
        options: JsonObject,
        schema: JsonObject,
        owner: HeartRepresentation,
        read: (String) -> JsonElement?,
    ): List<Pair<String?, List<Choice>>> {
        val pointer = (options["pointer"] as? JsonPrimitive)?.content.orEmpty()
        val names = variable.findAll(pointer).map { it.groupValues[1] }.toList()
        val properties = (schema["properties"] as? JsonObject) ?: none
        val combinations = names.fold(listOf(emptyMap<String, JsonElement>())) { done, name ->
            val values = (properties[name]?.jsonObject?.get("enum") as? JsonArray) ?: emptyList()
            done.flatMap { fields -> values.map { fields + (name to it) } }
        }
        return combinations.map { fields ->
            val label = fields.entries.takeIf { it.isNotEmpty() }
                ?.joinToString(" and ") { (name, value) -> "when $name is ${value.jsonPrimitive.content}" }
            label to of(options, owner, JsonObject(fields), read)
        }
    }

    /** The pointer with each `{name}` filled from [fields], escaped as RFC 6901 asks. */
    private fun pointer(options: JsonObject, fields: JsonObject): String =
        variable.replace(options.getValue("pointer").jsonPrimitive.content) { match ->
            (fields[match.groupValues[1]] as? JsonPrimitive)?.content.orEmpty().replace("~", "~0").replace("/", "~1")
        }

    private fun linked(
        options: JsonObject,
        owner: HeartRepresentation,
        fields: JsonObject,
        read: (String) -> JsonElement?,
    ): JsonElement? {
        val link = options.getValue("link").jsonObject
        val pointers = ((options["templatePointers"] as? JsonObject) ?: none)
            .mapValues { it.value.jsonPrimitive.content }
        val href = link.getValue("href").jsonPrimitive.content
        val templated = link["templated"]?.jsonPrimitive?.content == "true"
        val expanded = if (templated) UriTemplate.expand(href, owner.state, pointers, fields) else href
        val answer = read(URI(owner.url).resolve(expanded).toString()) ?: return null
        val items = (options["items"] as? JsonPrimitive)?.content
        return if (items == null) answer else JsonPointer.resolve(answer, items)
    }
}
