package heart

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

interface HeartRiseClient {
    /** The last synced resource, including after a refusal's re-sync. Null before enter(). */
    val current: HeartRepresentation?

    /** The rung [current] arrived at, from its Preference-Applied header (SPEC §6.11.1). Null before enter(). */
    val appliedRung: Rung?

    /** Whether the last act brought a resource into being, which [current] now is (SPEC §6.0, "New resource"). */
    val broughtIntoBeing: Boolean get() = false

    /**
     * Reads the service index at [url] as a JSON Home document (SPEC §6.11.1)
     * and from then on asks for the best dialect and rung it offers, up to
     * the client's own. Without a home document the client's settings stand.
     */
    fun discover(url: String)

    fun enter(url: String): HeartRepresentation
    fun act(rel: String, payload: JsonObject): HeartRepresentation

    /** Follows the [rel] link in the current representation whose title is [title]. */
    fun follow(rel: String, title: String?): HeartRepresentation

    /**
     * Whether [link] leads somewhere this client can go: one it can follow as it stands (SPEC §6.0), by a binding
     * it has for the link's scheme. A model is shown only these.
     */
    val reaches: (HeartLink) -> Boolean get() = { it.followable }

    /**
     * The resource at a URL as this client last held it, read only when it never held it: what a run off the rails
     * (spec/ideas/off-the-rails.md) acts on, with the last validator it was given for it.
     */
    val recall: (String) -> HeartRepresentation get() = { enter(it) }

    /** Whether each act carries the validator held; false when this client sends blind writes. */
    val sendsValidators: Boolean get() = true

    /**
     * The ALPS descriptor's doc for the offered form [rel] (SPEC §6.1, §8.5):
     * its relation URI, fetched and cached as HTTP allows. Null when the form's
     * relation is not a URI or names no descriptor. It describes; it never
     * makes an act legal.
     */
    fun describe(rel: String): String?

    /**
     * Why the act [rel] is not offered (SPEC §6.12), from the explanation the
     * manifest links to, checked against the link's integrity. [rel] is a
     * CURIE the manifest declares, or a relation URI. Null when the act is
     * offered, when the manifest links no explanation, or when the
     * explanation does not name it: the client never infers a reason.
     */
    fun explain(rel: String): String?

    /**
     * The choices of the offered form [rel] (HAL-SCHEMA-FORMS-HR Rule 8) on the
     * current resource: from its state, or from the resource a link names,
     * which the client reads. Null when [rel] is not offered.
     */
    fun choices(rel: String): FormChoices? =
        current?.let { held -> held.affordance(rel)?.let { FormChoices(it, held, listReader) } }

    /** How this client reads a list a form's choices come from, as JSON; null when it cannot be read. */
    val listReader: (String) -> JsonElement? get() = { null }

    /** Says the client has finished, in its event log, so a watcher can close what it was waiting for. */
    fun done()
}
