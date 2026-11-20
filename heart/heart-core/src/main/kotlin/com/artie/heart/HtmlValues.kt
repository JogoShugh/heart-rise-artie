package com.artie.heart

/**
 * A member's value as a person reads it, at any depth: a scalar as it is, a list of scalars joined, an object as
 * titled pairs, a list of objects as a table, and any other list as a list. No structure is shown as JSON.
 */
internal object HtmlValues {
    fun shown(value: Any?): String = when {
        value == null -> "<span class=\"none\">none</span>"
        value is Map<*, *> -> pairs(value)
        value is List<*> && value.all { it !is Map<*, *> && it !is List<*> } -> Html.text(value.joinToString(", "))
        value is List<*> && value.all { it is Map<*, *> } -> table(value.filterIsInstance<Map<*, *>>())
        value is List<*> -> "<ol class=\"nested\">" + value.joinToString("") { "<li>${shown(it)}</li>" } + "</ol>"
        else -> Html.text(value)
    }

    /** An object's members as pairs, each titled from its name, as the state's own members are. */
    private fun pairs(value: Map<*, *>): String = "<dl class=\"nested\">" +
        value.entries.joinToString("") { (name, member) ->
            "<div><dt${Html.attr("title", name)}>${Html.text(FieldTitle.of(name.toString()))}</dt>" +
                "<dd>${shown(member)}</dd></div>"
        } + "</dl>"

    /**
     * A list of objects as a table: a column for each member any of them has, in the order the members first
     * appear. An item without a member leaves its cell empty.
     */
    private fun table(items: List<Map<*, *>>): String {
        val columns = items.flatMap { it.keys }.distinct()
        val head = columns.joinToString("") { name ->
            "<th scope=\"col\"${Html.attr("title", name)}>${Html.text(FieldTitle.of(name.toString()))}</th>"
        }
        val rows = items.joinToString("") { item ->
            "<tr>" + columns.joinToString("") { name -> "<td>${if (name in item) shown(item[name]) else ""}</td>" } +
                "</tr>"
        }
        return "<table class=\"nested\"><thead><tr>$head</tr></thead><tbody>$rows</tbody></table>"
    }
}
