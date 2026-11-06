package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** RFC 9457 problem details (SPEC §6.2), as every binding carries them: a JSON body. */
internal object ProblemDetails {
    const val BASE = "https://w3id.org/heart-rise/problems/"

    fun type(body: String): String? =
        runCatching { Json.parseToJsonElement(body).jsonObject["type"]?.jsonPrimitive?.content }.getOrNull()

    /** What a problem says of itself: its `detail`, else its `title`; null when the body is no problem. */
    fun said(body: String): String? {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return (root["detail"] ?: root["title"])?.jsonPrimitive?.content
    }

    /** The `violations` of an invalid-input body, or its `error` text, or its `detail`, or nothing. */
    fun violations(body: String): List<String> {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        // Each violation is {pointer, message}. An older server sent strings.
        val listed = (root["violations"] as? JsonArray)?.map { item ->
            (item as? JsonObject)?.let { pointerMessage(it) } ?: item.jsonPrimitive.content
        }
        return listed ?: listOfNotNull((root["error"] ?: root["detail"])?.jsonPrimitive?.content)
    }

    /** A violation as every client says it: where, then what the service said of it; either may be missing. */
    private fun pointerMessage(violation: JsonObject): String =
        listOfNotNull(violation["pointer"], violation["message"] ?: violation["detail"])
            .map { it.jsonPrimitive.content }.filter { it.isNotEmpty() }.joinToString(" ")
}
