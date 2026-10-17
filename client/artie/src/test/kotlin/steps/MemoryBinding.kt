package steps

import heart.Act
import heart.Binding
import heart.Dialect
import heart.Outcome
import heart.Received
import heart.Rendering
import heart.Rung

/**
 * A toy protocol for SPEC §6.0: one bed held in memory under its own URI
 * scheme. It knows nothing of HTTP. It speaks the core's terms directly: a
 * read answers a representation with a validator, and an act is done,
 * stale or not offered. Its own codes are words, not numbers.
 */
class MemoryBinding(private val url: String, var status: String) : Binding {
    private var version = 1

    /** The validator of the bed's current state. */
    val validator: String get() = "mem-v$version"

    /** Someone else's act, outside the client under test. */
    fun sow() {
        check(status == "EMPTY") { "bed is $status" }
        status = "PLANTED"
        version++
    }

    override fun read(url: String, rendering: Rendering): Outcome = representation(url)

    override fun fetch(url: String, accept: List<String>, integrity: String?): Outcome = representation(url)

    override fun act(act: Act, rendering: Rendering): Outcome = when {
        act.validator != validator -> Outcome.Stale("stale", null)
        act.method != "POST" -> Outcome.Failed("no such method", null)
        act.url == "$url/sow" && status == "EMPTY" -> { sow(); Outcome.Done(received(), "done") }
        else -> Outcome.NotOffered("not offered", null)
    }

    private fun representation(requested: String): Outcome =
        if (requested == url) Outcome.Done(received(), "done") else Outcome.Failed("no such resource", null)

    private fun received() = Received(
        bytes = body().toByteArray(),
        mediaType = Dialect.HAL_SCHEMA_FORMS.mediaType,
        profiles = listOf(Dialect.HAL_SCHEMA_FORMS.profile),
        validator = validator,
        applied = Rung.L0,
        appliedAs = null,
    )

    /** The bed at L0: its state and the forms its state allows, targets relative to the bed. */
    private fun body(): String {
        val form = when (status) {
            "EMPTY" -> """"sow": {"_links": {"target": {"href": "${path()}/sow"}}, "method": "POST",
                "contentType": "application/json",
                "schema": {"type": "object", "properties": {"seedType": {"type": "string"}},
                "required": ["seedType"]}}"""
            else -> """"water": {"_links": {"target": {"href": "${path()}/water"}}, "method": "POST",
                "contentType": "application/json",
                "schema": {"type": "object", "properties": {"milliliters": {"type": "integer"}}}}"""
        }
        return """{"status": "$status", "_links": {"self": {"href": "$url"}}, "_forms": {$form}}"""
    }

    private fun path() = java.net.URI(url).path
}
