package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What a server's home document says it serves: its dialects, and the highest rung it honours. */
data class ServerOffer(val dialects: Set<Dialect>, val rung: Rung)

/**
 * SPEC §6.11.1: the service index's JSON Home representation
 * (draft-nottingham-json-home). Its link hints (draft-ietf-httpapi-link-hint)
 * declare the dialects (`formats`) and the Prefer values honoured
 * (`accept-prefer`). Hints are advisory: Preference-Applied on each
 * response stays the truth.
 */
object HomeDocument {
    const val MEDIA_TYPE = "application/json-home"
    private const val OK = 200

    /** The offer in [response], or null when it is not a home document. */
    fun offer(response: HeartResponse): ServerOffer? {
        val mediaType = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
        val hints = if (response.status == OK && mediaType == MEDIA_TYPE) hintsOf(response.body) else null
        return hints?.let { ServerOffer(dialects(it), rung(it)) }
    }

    /**
     * The dialect and rung to ask for: [preferred] if the server offers it,
     * else a dialect it does offer; and no rung above [maxRung], the offer,
     * or what that dialect defines.
     */
    fun choose(offer: ServerOffer, preferred: Dialect, maxRung: Rung): Pair<Dialect, Rung> {
        val dialect = preferred.takeIf { it in offer.dialects || offer.dialects.isEmpty() }
            ?: offer.dialects.first()
        return dialect to minOf(maxRung, offer.rung, dialect.maxRung)
    }

    /** The hints of the bed resource (`item`), or of the first resource listed. */
    private fun hintsOf(body: String): JsonObject? {
        val resources = (Json.parseToJsonElement(body) as? JsonObject)?.get("resources") as? JsonObject
        val resource = resources?.get("item") ?: resources?.values?.firstOrNull()
        return (resource as? JsonObject)?.get("hints") as? JsonObject
    }

    private fun dialects(hints: JsonObject): Set<Dialect> {
        val formats = (hints["formats"] as? JsonObject)?.keys.orEmpty()
        return Dialect.entries.filter { it.mediaType in formats }.toSet()
    }

    private fun rung(hints: JsonObject): Rung =
        Rung.applied((hints["accept-prefer"] as? JsonArray)?.joinToString(", ") { it.jsonPrimitive.content })
}
