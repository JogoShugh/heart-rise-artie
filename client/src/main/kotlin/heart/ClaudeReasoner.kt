package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The Reason step on the Claude Messages API, through ARTIE's own Transport.
 *
 * Prompt layout follows SPEC §6.8's prompt-cache rule. The tool and the
 * system prompt never change, so they form a cached prefix. Everything that
 * changes per turn (state, offers, intent) is the last user message, so
 * manifest churn invalidates only that. The tool's input schema is generic
 * on purpose: putting this turn's affordance names in it would change the
 * cached prefix every time the manifest changes.
 *
 * The model is forced to call the one "select" tool, whose fields are an
 * affordance name, field values, or a question. It is never shown a URI.
 */
class ClaudeReasoner(
    private val transport: Transport,
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : Reasoner {

    override fun reason(intent: String, state: JsonObject, offers: List<Offer>): Selection {
        val response = transport.send(
            HeartRequest(
                "POST",
                ENDPOINT,
                mapOf("x-api-key" to apiKey, "anthropic-version" to API_VERSION, "Content-Type" to "application/json"),
                request(intent, state, offers).toString(),
            ),
        )
        check(response.status in SUCCESS) {
            "Claude API answered ${response.status}: ${response.body.take(ERROR_EXCERPT)}"
        }
        return selection(Json.parseToJsonElement(response.body).jsonObject)
    }

    private fun request(intent: String, state: JsonObject, offers: List<Offer>): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", MAX_TOKENS)
        putJsonArray("tools") { add(TOOL) }
        putJsonObject("tool_choice") {
            put("type", "tool")
            put("name", TOOL_NAME)
        }
        putJsonArray("system") {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", SYSTEM)
                    putJsonObject("cache_control") { put("type", "ephemeral") }
                },
            )
        }
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", turn(intent, state, offers))
                            },
                        )
                    }
                },
            )
        }
    }

    private fun turn(intent: String, state: JsonObject, offers: List<Offer>): String {
        val offered = JsonObject(offers.associate { it.name to it.schema })
        return "<state>\n$state\n</state>\n<offers>\n$offered\n</offers>\n<intent>\n$intent\n</intent>"
    }

    private fun selection(message: JsonObject): Selection {
        val call = message.getValue("content").jsonArray.map { it.jsonObject }
            .firstOrNull { it["type"]?.jsonPrimitive?.content == "tool_use" }
            ?: error("Claude did not call the $TOOL_NAME tool: $message")
        val input = call.getValue("input").jsonObject
        return if (input["action"]?.jsonPrimitive?.content == "clarify") {
            Selection.Clarify(input["question"]?.jsonPrimitive?.content ?: "Could you say more?")
        } else {
            Selection.Act(
                affordance = input["affordance"]?.jsonPrimitive?.content ?: "",
                fields = input["fields"] as? JsonObject ?: JsonObject(emptyMap()),
            )
        }
    }

    companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
        private const val API_VERSION = "2023-06-01"
        private const val TOOL_NAME = "select"
        private const val MAX_TOKENS = 1024
        private const val ERROR_EXCERPT = 300
        private val SUCCESS = 200..299

        private val SYSTEM = """
            You choose what to do next for a user, from the actions a server offers right now.
            The user message has three parts: <state> is the resource as it is now; <offers> maps each
            action the server currently allows to the JSON Schema of its fields; <intent> is what the
            user asked for. Call the select tool exactly once.
            - If one offered action clearly matches the intent, and the intent gives every required
              field, answer action "act" with that action's exact name and field values that satisfy
              its schema.
            - If no offered action matches, or the intent is ambiguous, or a required value is
              missing, answer action "clarify" with one short question for the user.
            Never invent an action that is not in <offers>. Never guess a required value.
        """.trimIndent()

        private val TOOL: JsonObject = buildJsonObject {
            put("name", TOOL_NAME)
            put("description", "Select one offered action with its field values, or ask the user a question.")
            putJsonObject("input_schema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        put("enum", buildJsonArray { add(jsonString("act")); add(jsonString("clarify")) })
                    }
                    putJsonObject("affordance") { put("type", "string") }
                    putJsonObject("fields") { put("type", "object") }
                    putJsonObject("question") { put("type", "string") }
                }
                put("required", JsonArray(listOf(jsonString("action"))))
            }
            putJsonObject("cache_control") { put("type", "ephemeral") }
        }

        private fun jsonString(value: String) = kotlinx.serialization.json.JsonPrimitive(value)
    }
}
