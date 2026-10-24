package com.artie.heart

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.net.URLEncoder

/** One choice for a property: what is sent, and what is shown. */
data class Choice(val value: JsonNode, val prompt: String)

/**
 * How a list's items become choices: [value] and [prompt] name an object
 * item's members (a string or number item is both), only items whose members
 * equal every entry of [where] are kept, and [minItems] and [maxItems] say how
 * many may be chosen. The names are HAL-FORMS's.
 */
data class Pick(
    val value: String = "value",
    val prompt: String = "prompt",
    val where: Map<String, Any?> = emptyMap(),
    val minItems: Int? = null,
    val maxItems: Int? = null,
)

/**
 * Where a property's choices are (HAL-SCHEMA-FORMS-HR Rule 8): in the owning
 * resource's state, by JSON Pointer ([FromState]), or in another resource, by a
 * link ([FromLink]). It is an instruction, not the values, so the form that
 * carries it is the same bytes for every resource in the same state.
 */
sealed class Options(val pick: Pick) {
    /** `hr:options` as the property's schema carries it. */
    abstract fun json(): Map<String, Any?>

    /** One sentence for a description that cannot carry `hr:options`, such as OpenAPI's. */
    abstract fun prose(property: String): String

    /** The items the choices come from, for [state] (its JSON [body]) and the form's [fields]. */
    protected abstract fun items(state: Any?, body: JsonNode, fields: JsonNode): List<JsonNode>

    /** The choices now: every item that passes the [pick]'s filter, as its value and prompt. */
    fun choices(state: Any?, body: JsonNode, fields: JsonNode): List<Choice> =
        items(state, body, fields).filter(::kept).map { item ->
            when {
                item.isObject -> Choice(item.path(pick.value), item.path(pick.prompt).asText())
                else -> Choice(item, item.asText())
            }
        }

    private fun kept(item: JsonNode): Boolean =
        pick.where.all { (name, wanted) -> item.path(name) == MAPPER.valueToTree<JsonNode>(wanted) }

    protected fun common(): Map<String, Any?> = buildMap {
        put("valueField", pick.value)
        put("promptField", pick.prompt)
        if (pick.where.isNotEmpty()) put("where", pick.where)
        pick.minItems?.let { put("minItems", it) }
        pick.maxItems?.let { put("maxItems", it) }
    }

    /** Choices from the owning resource: [pointer] may name other form properties as `{name}`. */
    class FromState(val pointer: String, pick: Pick) : Options(pick) {
        /** The variables of [pointer]: properties of the same form. */
        val variables: List<String> = VARIABLE.findAll(pointer).map { it.groupValues[1] }.toList()

        override fun json(): Map<String, Any?> = linkedMapOf<String, Any?>("pointer" to pointer) + common()

        override fun prose(property: String) = "$property: one of the values at $pointer in its state"

        override fun items(state: Any?, body: JsonNode, fields: JsonNode): List<JsonNode> {
            val path = VARIABLE.replace(pointer) { escape(fields.path(it.groupValues[1]).asText()) }
            return body.at(path).takeIf { it.isArray }?.toList().orEmpty()
        }

        /** RFC 6901: a value inside a pointer escapes `~` and `/`. */
        private fun escape(text: String) = text.replace("~", "~0").replace("/", "~1")
    }

    /**
     * Choices from another resource, at [href], an RFC 6570 template whose
     * variables come from [templatePointers] into the owning resource. [items]
     * points to the list in its answer. [resolve] is the server's own copy of
     * those items, used to check an act, so the server never calls itself.
     */
    class FromLink(
        val href: String,
        val templatePointers: Map<String, String>,
        val items: String?,
        pick: Pick,
        private val resolve: (state: Any?, fields: Map<String, Any?>) -> List<Any?>,
    ) : Options(pick) {
        override fun json(): Map<String, Any?> = buildMap {
            put("link", mapOf("href" to href, "templated" to href.contains('{')))
            if (templatePointers.isNotEmpty()) put("templatePointers", templatePointers)
            items?.let { put("items", it) }
            putAll(common())
        }

        override fun prose(property: String) = "$property: one of the values that $href lists"

        override fun items(state: Any?, body: JsonNode, fields: JsonNode): List<JsonNode> {
            @Suppress("UNCHECKED_CAST")
            val plain = MAPPER.convertValue(fields, Map::class.java) as Map<String, Any?>? ?: emptyMap()
            return resolve(state, plain).map { MAPPER.valueToTree(it) }
        }

        /** [href] with every variable [templatePointers] fills from [body]; the others stay templated. */
        fun expanded(body: JsonNode): String = TEMPLATE.replace(href) { match ->
            val operator = match.groupValues[1]
            val names = match.groupValues[2].split(',')
            val known = names.filter { name -> templatePointers[name]?.let { !body.at(it).isMissingNode } == true }
            val filled = known.map { name ->
                val text = URLEncoder.encode(body.at(templatePointers.getValue(name)).asText(), Charsets.UTF_8)
                if (operator == "?") "$name=$text" else text
            }
            val unknown = names - known.toSet()
            when {
                operator == "?" && filled.isNotEmpty() ->
                    "?" + filled.joinToString("&") + if (unknown.isEmpty()) "" else "{&${unknown.joinToString(",")}}"
                filled.isEmpty() -> match.value
                else -> filled.joinToString(",")
            }
        }
    }

    companion object {
        private val MAPPER = ObjectMapper()
        private val VARIABLE = Regex("""\{([^}]+)}""")
        private val TEMPLATE = Regex("""\{([?]?)([^}]+)}""")
        private const val KEYWORD = "hr:options"

        fun fromState(pointer: String, pick: Pick = Pick()): Options = FromState(pointer, pick)

        fun fromLink(
            href: String,
            templatePointers: Map<String, String> = emptyMap(),
            items: String? = null,
            pick: Pick = Pick(),
            resolve: (state: Any?, fields: Map<String, Any?>) -> List<Any?>,
        ): Options = FromLink(href, templatePointers, items, pick, resolve)

        /** [schema] with each property in [options] carrying its `hr:options`. */
        fun merged(schema: String, options: Map<String, Options>): String {
            if (options.isEmpty()) return schema
            val root = MAPPER.readTree(schema) as ObjectNode
            options.forEach { (property, choices) ->
                val node = root.path("properties").path(property)
                require(node is ObjectNode) { "no property $property for its options" }
                node.set<JsonNode>(KEYWORD, MAPPER.valueToTree(choices.json()))
            }
            return root.toString()
        }

        /** [schema] for an OpenAPI description: `hr:options` becomes the extension `x-heart-options`. */
        fun openApi(schema: String): String {
            val root = MAPPER.readTree(schema)
            root.path("properties").forEach { property ->
                if (property is ObjectNode && property.has(KEYWORD)) {
                    property.set<JsonNode>("x-heart-options", property.remove(KEYWORD))
                }
            }
            return root.toString()
        }

        /** The choices of [act]'s properties as sentences, for an OpenAPI description; empty when it has none. */
        fun prose(act: Act): String =
            if (act.options.isEmpty()) {
                ""
            } else {
                act.options.entries.joinToString(" ", "\n\n") { (property, options) -> options.prose(property) + "." }
            }

        /** The HAL-FORMS `options` of [act]'s properties with choices, for [state], whose body is [body]. */
        fun halForms(act: Act, state: Any?, body: Map<String, Any?>): Map<String, Map<String, Any?>> {
            if (act.options.isEmpty()) return emptyMap()
            val tree = MAPPER.valueToTree<JsonNode>(body)
            val schema = MAPPER.readTree(act.schema)
            return act.options.mapValues { (_, options) -> halForms(options, state, tree, schema) }
        }

        /**
         * The HAL-FORMS `options` for [choices] on a resource whose JSON body is
         * [body]: from its state, inline, for every value of a property the
         * pointer depends on that the [schema] lists in an enum; from a link,
         * the link with what the resource fills.
         */
        fun halForms(choices: Options, state: Any?, body: JsonNode, schema: JsonNode): Map<String, Any?> = buildMap {
            when (choices) {
                is FromState -> put("inline", inline(choices, state, body, schema))
                is FromLink -> {
                    val href = choices.expanded(body)
                    put("link", mapOf("href" to href, "templated" to href.contains('{'), "type" to "application/json"))
                    put("promptField", choices.pick.prompt)
                    put("valueField", choices.pick.value)
                }
            }
            choices.pick.minItems?.let { put("minItems", it) }
            choices.pick.maxItems?.let { put("maxItems", it) }
        }

        private fun inline(choices: FromState, state: Any?, body: JsonNode, schema: JsonNode): List<Map<String, Any?>> {
            val combinations = choices.variables.fold(listOf(emptyMap<String, JsonNode>())) { done, name ->
                val values = schema.path("properties").path(name).path("enum").toList()
                done.flatMap { fields -> values.map { fields + (name to it) } }
            }
            return combinations.flatMap { fields ->
                val node = JsonNodeFactory.instance.objectNode()
                fields.forEach { (name, value) -> node.set<JsonNode>(name, value) }
                choices.choices(state, body, node)
            }.distinctBy { it.value }.map {
                // Plain values, not JSON trees: the representation is canonicalized for its validator.
                mapOf("prompt" to it.prompt, "value" to JsonTrees.plain(it.value.toString()))
            }
        }
    }
}
