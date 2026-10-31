package com.artie.heart

import com.fasterxml.jackson.databind.ObjectMapper

/**
 * A resource's acts as a person meets them: one button per act, in the type's declared order, so each keeps its
 * place. An offered act's button opens its dialog; an act the server explains it won't offer is greyed
 * (`aria-disabled`, still focusable) and its reason is shown when pressed. An act neither offered nor explained has
 * no button. A dialog asks for the act's fields and sends them to its target by its method, with the page's
 * validator as If-Match; a safe act (GET) is a plain form, so its answer is a page of its own.
 */
internal object HtmlActs {
    /** One act's button: its form key, title, and its form when offered or its reason when not. */
    class Entry(val key: String, val title: String, val form: Map<*, *>?, val reason: String?)

    fun buttons(entries: List<Entry>): String = if (entries.isEmpty()) "" else
        "<div class=\"acts\" role=\"toolbar\" aria-label=\"Acts\">" + entries.joinToString("") { entry ->
            val id = Html.token(entry.key)
            if (entry.form != null) {
                "<button type=\"button\" class=\"act\"${Html.attr("data-rel", entry.key)}" +
                    "${Html.attr("data-dialog", "act-$id")}>${Html.text(entry.title)}</button>"
            } else {
                "<button type=\"button\" class=\"act\"${Html.attr("data-rel", entry.key)} aria-disabled=\"true\"" +
                    "${Html.attr("aria-describedby", "why-$id")}>${Html.text(entry.title)}</button>" +
                    "<span class=\"why\" id=\"why-$id\" hidden>${Html.text(entry.reason)}</span>"
            }
        } + "</div>"

    fun dialogs(entries: List<Entry>, etag: String): String =
        entries.filter { it.form != null }.joinToString("") { dialog(it, checkNotNull(it.form), etag) }

    private fun dialog(entry: Entry, form: Map<*, *>, etag: String): String {
        val id = "act-${Html.token(entry.key)}"
        val schema = form["schema"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val required = (schema["required"] as? List<*>).orEmpty().toSet()
        val fields = (schema["properties"] as? Map<*, *>).orEmpty().entries.joinToString("") { (name, property) ->
            HtmlFields.of(id, name.toString(), property as? Map<*, *> ?: emptyMap<String, Any?>(), name in required)
        }
        val description = form["description"]?.let { "<p class=\"desc\">${Html.text(it)}</p>" }.orEmpty()
        return "<dialog${Html.attr("id", id)}${Html.attr("aria-labelledby", "$id-title")}>" +
            "<form${sending(entry.key, form, etag)}>" +
            "<h2${Html.attr("id", "$id-title")}>${Html.text(entry.title)}</h2>$description$fields" +
            "<div class=\"problem\" role=\"alert\" hidden></div>" +
            "<div class=\"buttons\"><button type=\"button\" class=\"quiet\" data-close>Cancel</button>" +
            "<button type=\"submit\">${Html.text(entry.title)}</button></div></form></dialog>"
    }

    /** How the form sends: htmx by the act's method, If-Match the page's validator; a GET as a plain form. */
    private fun sending(key: String, form: Map<*, *>, etag: String): String {
        val method = form["method"]?.toString()?.uppercase() ?: "POST"
        val template = ((form["_links"] as? Map<*, *>)?.get("target") as? Map<*, *>)?.get("href")?.toString().orEmpty()
        val target = template.substringBefore("{?")
        // A safe act goes to an address: heart-forms.js fills the template, path and query alike.
        if (method == "GET") {
            return Html.attr("data-rel", key) + " method=\"get\"" + Html.attr("action", target) +
                Html.attr("data-template", template)
        }
        val headers = MAPPER.writeValueAsString(mapOf("If-Match" to etag, "Accept" to "text/html"))
        return Html.attr("data-rel", key) + Html.attr("hx-${method.lowercase()}", target) +
            Html.attr("hx-headers", headers) +
            " hx-target=\"#resource\" hx-select=\"#resource\" hx-swap=\"outerHTML\""
    }

    private val MAPPER = ObjectMapper()
}
