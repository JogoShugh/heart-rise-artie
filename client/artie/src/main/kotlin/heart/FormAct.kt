package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.util.Base64

/**
 * A form's act: what HAL-SCHEMA-FORMS-HR Rule 10's integrity covers, but the target. Its method, content type,
 * template pointers and schema, as ARTIE parsed them, with every object's members in order of name, digested as
 * `sha256-<Base64>`. ARTIE never builds an address: it acts at the target the form gives. So a lock file that pins
 * the act lets a form the server moved, and changed in nothing else, go on (script-guard.feature).
 */
object FormAct {
    fun of(form: AffordanceForm): String {
        val act = JsonObject(
            mapOf(
                "contentType" to JsonPrimitive(form.contentType),
                "hr:templatePointers" to JsonObject(form.templatePointers.mapValues { JsonPrimitive(it.value) }),
                "method" to JsonPrimitive(form.method),
                "schema" to form.schema,
            ),
        )
        val bytes = Json.encodeToString(JsonElement.serializer(), sorted(act)).toByteArray()
        return "sha256-" + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))
    }

    private fun sorted(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.entries.sortedBy { it.key }.associate { it.key to sorted(it.value) })
        is JsonArray -> JsonArray(element.map(::sorted))
        else -> element
    }
}
