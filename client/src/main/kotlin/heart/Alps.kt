package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/**
 * ALPS profile documents (SPEC §8.5). A form's relation URI names a
 * descriptor: the document is the URI without its fragment, and the
 * fragment is the descriptor's id. The fetch is an ordinary GET, so the
 * client's HTTP cache keeps the document as long as the server allows.
 */
object Alps {
    const val MEDIA_TYPE = "application/alps+json"
    private const val OK = 200
    private const val LAST_SUCCESS = 299
    private val SUCCESS = OK..LAST_SUCCESS

    /** The doc of the descriptor [relation] names, or null if it is not an absolute URI or names none. */
    fun describe(transport: Transport, relation: String): String? {
        val uri = runCatching { URI(relation) }.getOrNull()?.takeIf { it.isAbsolute && it.fragment != null }
        val document = uri?.let { URI(it.scheme, it.schemeSpecificPart, null).toString() }
        val response = document?.let { transport.send(HeartRequest("GET", it, mapOf("Accept" to MEDIA_TYPE))) }
        return response?.takeIf { it.status in SUCCESS }
            ?.let { doc(Json.parseToJsonElement(it.body).jsonObject, uri.fragment) }
    }

    private fun doc(alps: JsonObject, id: String): String? =
        (alps["alps"]?.jsonObject?.get("descriptor") as? JsonArray).orEmpty().map { it.jsonObject }
            .firstOrNull { it["id"]?.jsonPrimitive?.content == id }
            ?.let { it["doc"]?.jsonObject?.get("value")?.jsonPrimitive?.content ?: it["title"]?.jsonPrimitive?.content }
}
