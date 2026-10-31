package com.artie.heart

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * What a resource is, laid out for a person: its links, its state and its embedded resources. A member's label is
 * its name titled ([FieldTitle]), with the name itself on hover. The first [SHOWN] members are shown; the rest are
 * folded behind "N more", in a native `<details>`. Members of the protocol (`_links`, `hr:address`) are not state.
 */
internal object HtmlState {
    private const val SHOWN = 12

    /** The relations that are the protocol's own, not a place a person goes. */
    private val PROTOCOL = setOf("self", "curies")

    /** What heads a resource's page: its link's title, else its title, name, login or label, else its address. */
    fun title(body: Map<*, *>): String =
        (self(body)?.get("title") ?: NAMING.firstNotNullOfOrNull { body[it] } ?: self(body)?.get("href") ?: "")
            .toString()

    fun links(body: Map<*, *>): String {
        val links = (body["_links"] as? Map<*, *>).orEmpty().filterKeys { rel ->
            rel !in PROTOCOL && !rel.toString().startsWith("hr:")
        }
        val anchors = links.flatMap { (rel, value) -> each(value).map { rel.toString() to it } }
            .filter { (_, link) -> link["templated"] != true }
            .joinToString("") { (rel, link) ->
                "<a${Html.attr("rel", rel)}${Html.attr("href", link["href"])}>" +
                    "${Html.text(link["title"] ?: FieldTitle.of(rel.substringAfter(':')))}</a>"
            }
        return if (anchors.isEmpty()) "" else "<nav class=\"links\" aria-label=\"Links\">$anchors</nav>"
    }

    fun state(body: Map<*, *>): String {
        val members = body.filterKeys { key -> key.toString().let { !it.startsWith("_") && !it.startsWith("hr:") } }
        val shown = members.entries.take(SHOWN)
        val folded = members.entries.drop(SHOWN)
        val more = if (folded.isEmpty()) "" else
            "<details class=\"more\"><summary>${folded.size} more</summary>${list(folded)}</details>"
        return if (members.isEmpty()) "" else list(shown) + more
    }

    fun embedded(body: Map<*, *>): String = (body["_embedded"] as? Map<*, *>).orEmpty().entries.joinToString("") {
        (rel, value) ->
        val items = each(value).joinToString("") { item ->
            val self = self(item)
            val words = Html.text(self?.get("title") ?: named(item) ?: self?.get("href") ?: rel)
            val href = self?.get("href")
            if (href == null) "<li>$words</li>" else "<li><a${Html.attr("href", href)}>$words</a></li>"
        }
        "<section class=\"embedded\"><h2>${Html.text(FieldTitle.of(rel.toString().substringAfter(':')))}</h2>" +
            "<ul>$items</ul></section>"
    }

    private fun list(members: List<Map.Entry<Any?, Any?>>): String = "<dl class=\"state\">" +
        members.joinToString("") { (name, value) ->
            "<dt${Html.attr("title", name)}>${Html.text(FieldTitle.of(name.toString()))}</dt><dd>${shown(value)}</dd>"
        } + "</dl>"

    /** A value as text: a scalar as it is, a list of scalars joined, anything else as compact JSON. */
    private fun shown(value: Any?): String = when {
        value == null -> "<span class=\"none\">none</span>"
        value is List<*> && value.all { it !is Map<*, *> && it !is List<*> } -> Html.text(value.joinToString(", "))
        value is Map<*, *> || value is List<*> -> "<code>${Html.text(MAPPER.writeValueAsString(value))}</code>"
        else -> Html.text(value)
    }

    /** What names [item] for a person: its title, name, login or label, else its first words. */
    private fun named(item: Map<*, *>): Any? = NAMING.firstNotNullOfOrNull { item[it] }
        ?: item.entries.firstOrNull { (key, value) -> value is String && !key.toString().startsWith("_") }?.value

    private val NAMING = listOf("title", "name", "login", "label")

    private fun self(body: Map<*, *>): Map<*, *>? = ((body["_links"] as? Map<*, *>)?.get("self") as? Map<*, *>)

    @Suppress("UNCHECKED_CAST")
    private fun each(value: Any?): List<Map<*, *>> = when (value) {
        is List<*> -> value.filterIsInstance<Map<*, *>>()
        is Map<*, *> -> listOf(value)
        else -> emptyList()
    }

    private val MAPPER = ObjectMapper()
}
