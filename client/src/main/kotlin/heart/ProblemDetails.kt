package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** RFC 9457 problem details (SPEC §6.2), as every binding carries them: a JSON body. */
internal object ProblemDetails {
    const val BASE = "https://github.com/jogoshugh/heart-rise/problems/"

    fun type(body: String): String? =
        runCatching { Json.parseToJsonElement(body).jsonObject["type"]?.jsonPrimitive?.content }.getOrNull()

    /** The `violations` of an invalid-input body, or its `error` text, or nothing. */
    fun violations(body: String): List<String> {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        // Each violation is {pointer, message}. An older server sent strings.
        val listed = (root["violations"] as? JsonArray)?.map { item ->
            (item as? JsonObject)?.let { pointerMessage(it) } ?: item.jsonPrimitive.content
        }
        return listed ?: listOfNotNull(root["error"]?.jsonPrimitive?.content)
    }

    private fun pointerMessage(violation: JsonObject): String =
        "${violation["pointer"]?.jsonPrimitive?.content}: ${violation["message"]?.jsonPrimitive?.content}"
}
