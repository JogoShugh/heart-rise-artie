package com.artie.heart

/**
 * HTML for people (spec/PLAN-HTML-DIALECT.md): a resource as a browser shows it, rendered from its L0 HAL Schema
 * Forms representation and the explanation of the acts it leaves out, so every binding, and a gateway, serves the
 * same page. It is an on-ramp outside the profile, as plain JSON is: chosen only when Accept names `text/html`
 * above both dialects, with a validator of its own, and offering exactly the forms the JSON offers.
 */
object HtmlView {
    /** The renderer's version, part of what the HTML's validator names: a release that renders anew changes it. */
    const val VERSION = "1"

    /** The validator's tag, beside the dialects' `hf` and `hsf`. */
    const val ETAG_TAG = "html"

    const val MEDIA_TYPE = "text/html;charset=UTF-8"

    /** What the HTML of [state] is rendered from, and so what its validator names. */
    fun <S, K> source(layers: Layers<S, K>, state: S): Map<String, Any?> =
        mapOf("html" to VERSION, "assets" to HtmlAssets.names, "l0" to layers.layer1(state, Rung.L0))

    /** [state]'s HTML, its forms sending [etag] as If-Match. */
    fun <S, K> of(layers: Layers<S, K>, state: S, etag: String): String {
        val body = layers.layer1(state, Rung.L0)
        val type = layers.type
        val order = type.acts.map { type.profile.reference(it.relation) }
        return render(body, etag, absent(layers, body), order)
    }

    /**
     * The page of [body], an L0 representation: its state, links and embedded resources; its offered forms and
     * the [absent] acts (each `rel`, `title` and `reason`), as buttons in [order], by full relation.
     */
    fun render(body: Map<*, *>, etag: String, absent: List<Map<*, *>>, order: List<String>): String {
        val acts = entries(body, absent, order)
        val title = HtmlState.title(body)
        // On a wide screen the acts stand in a column beside the information; a small one stacks them below it.
        return document(
            title, etag,
            HtmlState.links(body) + "<h1>${Html.text(title)}</h1>" +
                "<div class=\"layout\"><div class=\"info\">${HtmlState.state(body)}${HtmlState.embedded(body)}</div>" +
                "<aside class=\"side\" aria-label=\"Acts\">${HtmlActs.buttons(acts)}</aside></div>" +
                HtmlActs.dialogs(acts, etag),
        )
    }

    /** The bookmark's page: its forms as dialogs, a search's sent as an address to go to (SPEC §6.11). */
    fun index(index: ServiceIndex, etag: String): String =
        render(JsonTrees.plain(index.body(Dialect.JSONSCHEMA)) as Map<*, *>, etag, emptyList(), emptyList())

    /** The bookmark page's validator: it changes when the index does, or the renderer. */
    fun indexValidator(index: ServiceIndex): String =
        "\"index-$ETAG_TAG-${ContentAddress.address(VERSION + HtmlAssets.names + index.body(Dialect.JSONSCHEMA))}\""

    /** A whole page around [main], with the server's own stylesheet and scripts. */
    internal fun document(title: String, etag: String, main: String): String =
        "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<title>${Html.text(title)}</title>" +
            "<link rel=\"stylesheet\"${Html.attr("href", HtmlAssets.href("heart.css"))}>" +
            "<script defer${Html.attr("src", HtmlAssets.href("htmx-2.0.4.min.js"))}></script>" +
            "<script defer${Html.attr("src", HtmlAssets.href("ajv2020-8.17.1.min.js"))}></script>" +
            "<script defer${Html.attr("src", HtmlAssets.href("heart-forms.js"))}></script>" +
            "</head><body hx-headers='{\"Accept\": \"text/html\"}'>" +
            "<main id=\"resource\"${Html.attr("data-etag", etag)}>$main</main>" +
            // Outside what an act's answer swaps, so what the act did is said once the new page is in.
            "<div id=\"announce\" class=\"announce\" role=\"status\" aria-live=\"polite\"></div>" +
            "</body></html>\n"

    /** The offered forms and the absent acts, one entry per act, in [order]; any not in it after, as they come. */
    private fun entries(body: Map<*, *>, absent: List<Map<*, *>>, order: List<String>): List<HtmlActs.Entry> {
        val curies = Curies((body["_links"] as? Map<*, *>)?.get("curies"))
        val forms = (body["_forms"] as? Map<*, *>).orEmpty().entries.associate { (key, form) ->
            val title = (form as? Map<*, *>)?.get("title")?.toString() ?: key.toString()
            curies.expand(key.toString()) to HtmlActs.Entry(key.toString(), title, form as? Map<*, *>, null)
        }
        val greyed = absent.associate { act ->
            val rel = act["rel"].toString()
            rel to HtmlActs.Entry(curies.compact(rel), act["title"]?.toString() ?: rel, null, act["reason"]?.toString())
        }
        val all = forms + greyed.filterKeys { it !in forms }
        return order.mapNotNull { all[it] } + all.filterKeys { it !in order }.values
    }

    /** The explanation [body] links to, held by [layers]: the acts it leaves out, each with why. */
    private fun absent(layers: Layers<*, *>, body: Map<*, *>): List<Map<*, *>> {
        val link = (body["_links"] as? Map<*, *>)?.get(Layers.UNAVAILABLE_REL) as? Map<*, *>
        val explanation = link?.let { layers.explanations[it["href"].toString().removePrefix(Layers.EXPLANATIONS)] }
        val listed = explanation?.let { (JsonTrees.plain(it) as? Map<*, *>)?.get("unavailable") as? List<*> }
        return listed.orEmpty().filterIsInstance<Map<*, *>>()
    }

    /** A body's CURIEs: `home:dim` and `/profiles/home#dim` name one relation. */
    private class Curies(declared: Any?) {
        private val prefixes = (declared as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().associate {
            it["name"].toString() to it["href"].toString().substringBefore("{rel}")
        }

        fun expand(key: String): String =
            prefixes[key.substringBefore(':', "")]?.let { it + key.substringAfter(':') } ?: key

        fun compact(rel: String): String =
            prefixes.entries.firstOrNull { rel.startsWith(it.value) }?.let { "${it.key}:${rel.removePrefix(it.value)}" }
                ?: rel
    }
}
