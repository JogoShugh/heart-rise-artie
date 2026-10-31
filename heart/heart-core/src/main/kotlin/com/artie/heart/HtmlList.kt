package com.artie.heart

/**
 * A page of matches as a person reads it (SPEC §6.11, spec/PLAN-HTML-DIALECT.md): a grid, one row per match, its
 * title linked, then up to [COLUMNS] scalar members every summary shares; paged by the page's own links. A match
 * carries no forms, so each row's Actions are read from the match itself when a person asks (heart-forms.js).
 */
object HtmlList {
    private const val COLUMNS = 5

    /** The relations that page a list, in the order a person reads them. */
    private val PAGING = listOf("first", "prev", "next", "last")

    /** [body], a page of a search or a listing, as a whole page titled [title]. */
    fun page(body: Map<*, *>, title: String): String {
        val links = body["_links"] as? Map<*, *>
        val items = (links?.get("item") as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
        val summaries = ((body["_embedded"] as? Map<*, *>)?.get("item") as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .associateBy { ((it["_links"] as? Map<*, *>)?.get("self") as? Map<*, *>)?.get("href") }
        val titles = items.associate { it["href"] to it["title"] }
        val columns = columns(summaries.values.toList()).filterNot { name ->
            // A member that only repeats each row's title says nothing the first column doesn't.
            summaries.all { (href, summary) -> summary[name]?.toString() == titles[href]?.toString() }
        }
        val head = "<tr><th>Title</th>" + columns.joinToString("") { "<th>${Html.text(FieldTitle.of(it))}</th>" } +
            "<th><span class=\"hidden\">Acts</span></th></tr>"
        val rows = items.joinToString("") { item -> row(item, summaries[item["href"]], columns) }
        val count = body["count"]?.let { "<p class=\"count\">${Html.text(it)} found</p>" }.orEmpty()
        val paging = PAGING.mapNotNull { rel ->
            val href = (links?.get(rel) as? Map<*, *>)?.get("href") ?: return@mapNotNull null
            "<a rel=\"$rel\"${Html.attr("href", href)}>${FieldTitle.of(rel)}</a>"
        }.joinToString("")
        val nav = if (paging.isEmpty()) "" else "<nav class=\"paging\" aria-label=\"Pages\">$paging</nav>"
        return HtmlView.document(
            title, "",
            "<h1>${Html.text(title)}</h1>$count<div class=\"grid\"><table><thead>$head</thead><tbody>$rows</tbody>" +
                "</table></div>$nav",
        )
    }

    /** A page's validator for a browser: it changes when the page does, or the renderer. */
    fun validator(body: Map<*, *>): String =
        "\"page.${HtmlView.ETAG_TAG}.${ContentAddress.address(HtmlView.VERSION + ContentAddress.canonical(body))}\""

    private fun row(item: Map<*, *>, summary: Map<*, *>?, columns: List<String>): String {
        val href = item["href"]
        val cells = columns.joinToString("") { "<td>${Html.text(summary?.get(it) ?: "")}</td>" }
        val title = "<a${Html.attr("href", href)}>${Html.text(item["title"] ?: href)}</a>"
        val acts = "<button type=\"button\" class=\"quiet\"${Html.attr("data-acts", href)} aria-expanded=\"false\">" +
            "Actions</button><div class=\"row-menu\" hidden></div>"
        return "<tr${Html.attr("data-item", href)}><td>$title</td>$cells<td class=\"row-acts\">$acts</td></tr>"
    }

    /** The scalar members every summary has, but the title, which the first column shows. */
    private fun columns(summaries: List<Map<*, *>>): List<String> {
        val first = summaries.firstOrNull() ?: return emptyList()
        return first.keys.map { it.toString() }
            .filter { name -> name != "title" && !name.startsWith("_") && !name.startsWith("hr:") }
            .filter { name -> summaries.all { it[name] != null && it[name] !is Map<*, *> && it[name] !is List<*> } }
            .take(COLUMNS)
    }
}
