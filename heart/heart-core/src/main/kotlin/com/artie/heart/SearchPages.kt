package com.artie.heart

import java.net.URLEncoder

/**
 * A search of [collection] by part of a name and by id; either may be absent.
 * A type that declares its collection path is searched at [path], that path
 * filled, by [filters], and lists its matches at their [item] paths.
 */
data class Search(
    val collection: String,
    val name: String? = null,
    val id: Long? = null,
    val path: String = "/$collection",
    val filters: Map<String, String> = emptyMap(),
    val item: UriTemplate = "/$collection/{id}",
)

/**
 * The pages of a search (SPEC §6.11): matches under the IANA `item` relation
 * (RFC 6573), each titled, paged by first, last, next and prev (RFC 5005).
 */
object SearchPages {
    /** Page [page] (from 1) of the [found] resources of a [search], [size] to a page. */
    fun page(search: Search, found: List<Found>, page: Int, size: Int): Map<String, Any> {
        val pageNo = page.coerceAtLeast(1)
        val lastPage = ((found.size + size - 1) / size).coerceAtLeast(1)
        val filters = listOfNotNull(
            search.name?.let { "name=${encode(it)}" },
            search.id?.let { "id=$it" },
        ) + search.filters.map { (field, value) -> "$field=${encode(value)}" }
        fun href(n: Int): Map<String, String> {
            val query = filters + listOfNotNull(if (n > 1) "page=$n" else null)
            return mapOf("href" to if (query.isEmpty()) search.path else "${search.path}?" + query.joinToString("&"))
        }
        // Page links appear only when the list does not fit on one page.
        val paging = buildMap {
            if (lastPage > 1 || pageNo > 1) {
                put("first", href(1))
                put("last", href(lastPage))
            }
            if (pageNo < lastPage) put("next", href(pageNo + 1))
            if (pageNo > 1 && pageNo <= lastPage) put("prev", href(pageNo - 1))
        }
        val items = found.drop((pageNo - 1) * size).take(size)
        // SPEC §6.11: each match the server summarised is embedded too, with its self link; no forms.
        val embedded = items.mapNotNull { match ->
            match.summary?.let { it + ("_links" to mapOf("self" to item(search, match))) }
        }
        return buildMap {
            put("count", found.size)
            put("_links", mapOf("self" to href(pageNo)) + paging + ("item" to items.map { item(search, it) }))
            if (embedded.isNotEmpty()) put("_embedded", mapOf("item" to embedded))
        }
    }

    /** Page [page] in plain JSON, the OpenAPI twin: each match by id and title, and no links. */
    fun plainPage(found: List<Found>, page: Int, size: Int): Map<String, Any> = mapOf(
        "count" to found.size,
        "items" to found.drop((page.coerceAtLeast(1) - 1) * size).take(size)
            .map { mapOf("id" to it.id, "title" to it.title) },
    )

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8)

    /** RFC 6573: a match as an `item` link, at its item path, titled. */
    private fun item(search: Search, found: Found): Map<String, String> =
        mapOf("href" to ItemPath.fill(search.item, found.variables), "title" to found.title)

}
