package heart.mcp

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The six verbs, which never change. What is legal now comes back in each
 * result, never in this list. No input is an address, a method, a header or
 * a validator: the model names an act, a link or a configured service, and
 * ARTIE builds every request.
 */
internal object Tools {

    data class Verb(val name: String, val description: String, val input: ToolSchema)

    fun all(services: Collection<String>): List<Verb> = listOf(
        Verb(
            "heart_look",
            "Look at a HEART service's current resource: its state, the acts it offers now (each with a " +
                "title, description and JSON Schema), and its links. Give `service` to start at that " +
                "service's bookmark; leave it out to read the current resource again.",
            schema(required = emptyList()) {
                putJsonObject("service") {
                    put("type", "string")
                    put("description", "A configured service to start at.")
                    putJsonArray("enum") { services.forEach { add(it) } }
                }
            },
        ),
        Verb(
            "heart_act",
            "Take one act the current resource offers, by its name exactly as listed in `acts`, with " +
                "fields that satisfy its schema. Acts not offered now cannot be taken. The result is the " +
                "resource after the act, with its new acts, and an `outcome`.",
            schema(required = listOf("act")) {
                putJsonObject("act") { put("type", "string"); put("description", "An offered act's name.") }
                putJsonObject("fields") { put("type", "object"); put("description", "The act's fields.") }
            },
        ),
        Verb(
            "heart_follow",
            "Follow one of the current resource's links, by its `rel` and `title` exactly as listed in `links`.",
            schema(required = listOf("rel")) {
                putJsonObject("rel") { put("type", "string") }
                putJsonObject("title") { put("type", "string") }
            },
        ),
        Verb(
            "heart_describe",
            "Read the service's own fuller description of an offered act, from its profile.",
            schema(required = listOf("act")) { putJsonObject("act") { put("type", "string") } },
        ),
        Verb(
            "heart_explain",
            "Ask why an act is not offered now. The answer is the service's own reason; nothing is sent.",
            schema(required = listOf("act")) { putJsonObject("act") { put("type", "string") } },
        ),
        Verb(
            "heart_back",
            "Go back to the resource before this one, and read it again.",
            schema(required = emptyList()) {},
        ),
    )

    private fun schema(required: List<String>, properties: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
        ToolSchema(buildJsonObject(properties), required)

    /** The empty object, for a tool called with no arguments. */
    val NONE = JsonObject(emptyMap())
}
