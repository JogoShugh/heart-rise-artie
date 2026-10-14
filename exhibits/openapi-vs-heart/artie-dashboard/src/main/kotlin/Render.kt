import heart.AffordanceForm
import heart.HeartRepresentation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/**
 * Any HEART representation as an htmx card. Nothing here knows a domain: the
 * state is a table of whatever members it has, each offered form is an HTML form
 * built from its JSON Schema with its title on the button, and each link is
 * shown by its title. The card re-syncs every 2 seconds, so what the server
 * offers now is what the page shows now.
 */
object Render {

    fun page(body: String): String = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<title>ARTIE dashboard</title><script src="/htmx.min.js"></script></head>
<body><h1>ARTIE dashboard</h1>$body</body></html>"""

    fun card(rep: HeartRepresentation, message: String = ""): String {
        val at = enc(rep.url)
        return """
    <section class="card" hx-get="/card?at=$at" hx-trigger="every 2s" hx-swap="outerHTML">
      <h2>${esc(title(rep))}</h2>
      <table class="state">${state(rep.state)}</table>
      <div class="buttons">${rep.affordances.values.joinToString("") { form(rep, it) }}</div>
      <nav class="links">${links(rep)}</nav>
      <form class="ask" hx-post="/ask?at=$at" hx-target="closest .card" hx-swap="outerHTML">
        <input name="intent" placeholder="Ask ARTIE" aria-label="Ask ARTIE"><button type="submit">Ask</button>
      </form>
      ${if (message.isNotEmpty()) "<p class=\"message\">${esc(message)}</p>" else ""}
    </section>"""
    }

    private fun title(rep: HeartRepresentation): String =
        rep.links("self").firstOrNull()?.title ?: rep.state["label"]?.text() ?: rep.state["title"]?.text() ?: rep.url

    private fun state(state: JsonObject): String =
        state.entries.joinToString("") { (name, value) ->
            "<tr><th>${esc(name)}</th><td data-member=\"${esc(name)}\">${esc(shown(value))}</td></tr>"
        }

    private fun shown(value: JsonElement): String = when (value) {
        is JsonNull -> "none"
        is JsonPrimitive -> value.content
        is JsonArray -> "${value.size} items"
        is JsonObject -> "${value.size} members"
    }

    /** An offered form: one input per schema property, and its title on the button. */
    private fun form(rep: HeartRepresentation, form: AffordanceForm): String {
        val properties = (form.schema["properties"] as? JsonObject).orEmpty()
        val required = (form.schema["required"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
        val inputs = properties.entries.joinToString("") { (name, schema) -> input(name, schema.jsonObject, name in required) }
        return """
      <form class="act" hx-post="/act?at=${enc(rep.url)}" hx-target="closest .card" hx-swap="outerHTML">
        <input type="hidden" name="_form" value="${esc(form.rel)}">$inputs<button type="submit">${esc(form.title ?: form.rel)}</button>
      </form>"""
    }

    private fun input(name: String, schema: JsonObject, required: Boolean): String {
        val label = schema["title"]?.text() ?: name
        val example = (schema["examples"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content.orEmpty()
        val attrs = buildString {
            append(" name=\"${esc(name)}\" aria-label=\"${esc(label)}\" placeholder=\"${esc(label)}\"")
            if (example.isNotEmpty()) append(" value=\"${esc(example)}\"")
            if (required) append(" required")
            schema["minimum"]?.let { append(" min=\"${it.text()}\"") }
            schema["maximum"]?.let { append(" max=\"${it.text()}\"") }
        }
        val enum = schema["enum"] as? JsonArray
        return when {
            enum != null -> "<select$attrs>${enum.joinToString("") { "<option>${esc(it.jsonPrimitive.content)}</option>" }}</select>"
            schema["type"]?.text() in setOf("integer", "number") -> "<input type=\"number\"$attrs>"
            else -> "<input$attrs>"
        }
    }

    private fun links(rep: HeartRepresentation): String =
        rep.linksByRel.filterKeys { it != "self" && !it.startsWith("hr:") && it != "curies" && it != "profile" }
            .flatMap { (_, links) -> links.filter { it.title != null } }
            .joinToString(" ") { "<a href=\"/?at=${enc(java.net.URI(rep.url).resolve(it.href).toString())}\">${esc(it.title!!)}</a>" }

    private fun JsonElement.text(): String? = (this as? JsonPrimitive)?.content

    fun enc(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)

    fun esc(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
