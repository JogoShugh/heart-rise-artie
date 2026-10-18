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
 * changes per turn (state, offers, links, intent) is the last user message, so
 * manifest churn invalidates only that. The tool's input schema is generic
 * on purpose: putting this turn's affordance names in it would change the
 * cached prefix every time the manifest changes.
 *
 * The model is offered one "select" tool, whose fields are an affordance
 * name and field values, a link's relation and title, or a question. Current
 * models refuse a forced tool call, so the model chooses whether to call it;
 * an answer in words alone is taken as a question for the user, never an act.
 * It is never shown a URI: links arrive as relation and title, without their href.
 */
class ClaudeReasoner(
    private val transport: Transport,
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : Reasoner {

    override fun reason(intent: String, state: JsonObject, offers: List<Offer>, links: List<LinkOffer>): Selection {
        val response = transport.send(
            HeartRequest(
                "POST",
                ENDPOINT,
                mapOf("x-api-key" to apiKey, "anthropic-version" to API_VERSION, "Content-Type" to "application/json"),
                request(intent, state, offers, links).toString(),
            ),
        )
        check(response.status in SUCCESS) {
            "Claude API answered ${response.status}: ${response.body.take(ERROR_EXCERPT)}"
        }
        return selection(Json.parseToJsonElement(response.body).jsonObject)
    }

    private fun request(
        intent: String,
        state: JsonObject,
        offers: List<Offer>,
        links: List<LinkOffer>,
    ): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", MAX_TOKENS)
        putJsonArray("tools") { add(TOOL) }
        putJsonObject("tool_choice") { put("type", "auto") }
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
                                put("text", turn(intent, state, offers, links))
                            },
                        )
                    }
                },
            )
        }
    }

    private fun turn(intent: String, state: JsonObject, offers: List<Offer>, links: List<LinkOffer>): String {
        val offered = buildJsonObject {
            offers.forEach { offer ->
                putJsonObject(offer.name) {
                    offer.title?.let { put("title", it) }
                    offer.description?.let { put("description", it) }
                    offer.detail?.let { put("detail", it) }
                    put("schema", offer.schema)
                }
            }
        }
        val linked = buildJsonArray {
            links.forEach { link ->
                add(
                    buildJsonObject {
                        put("rel", link.rel)
                        link.title?.let { put("title", it) }
                        // An embedded resource: what it says, so a link needn't be followed only to be read.
                        link.state?.let { put("state", it) }
                    },
                )
            }
        }
        return "<state>\n$state\n</state>\n<offers>\n$offered\n</offers>\n" +
            "<links>\n$linked\n</links>\n<intent>\n$intent\n</intent>"
    }

    private fun selection(message: JsonObject): Selection {
        val content = message.getValue("content").jsonArray.map { it.jsonObject }
        val call = content.firstOrNull { it["type"]?.jsonPrimitive?.content == "tool_use" }
            ?: return Selection.Clarify(words(content))
        val input = call.getValue("input").jsonObject
        fun text(name: String) = input[name]?.jsonPrimitive?.content
        return when (text("action")) {
            "clarify" -> Selection.Clarify(text("question") ?: "Could you say more?")
            // A blank title is no title: models name an untitled link with "".
            "follow" -> Selection.Follow(rel = text("rel").orEmpty(), title = text("title")?.ifBlank { null })
            "describe" -> Selection.Describe(text("affordance").orEmpty())
            "explain" -> Selection.Explain(text("affordance").orEmpty())
            else -> Selection.Act(
                affordance = text("affordance").orEmpty(),
                fields = input["fields"] as? JsonObject ?: JsonObject(emptyMap()),
            )
        }
    }

    companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
        private const val API_VERSION = "2023-06-01"
        private const val TOOL_NAME = "select"

        /** The model's words, when it answered without the tool: a question for the user. */
        fun words(content: List<JsonObject>): String =
            content.mapNotNull { block -> block["text"]?.jsonPrimitive?.content }.joinToString(" ").trim()
                .ifEmpty { "Could you say more?" }
        private const val MAX_TOKENS = 1024
        private const val ERROR_EXCERPT = 300
        private val ACTIONS = listOf("act", "describe", "explain", "follow", "clarify")
        private val SUCCESS = 200..299

        private val SYSTEM = """
            You choose what to do next for a user, from the actions a server offers right now.
            The user message has four parts: <state> is the resource as it is now; <offers> maps each
            action the server currently allows, by name, to its title, description and the JSON Schema
            of its fields (and, if you asked for it, a longer detail); <links> lists the links the
            resource offers, each by relation and title; <intent> is what the user asked for.
            Call the select tool exactly once.
            - If you need to know more about what an offered action does before choosing, answer
              action "describe" with that action's exact name. You will be asked again with its detail.
            - If the user asks why they cannot do something that is not in <offers>, answer action
              "explain" with the name it would have, in the same form as the offered names
              (for example "garden:harvest"). The server's own reason is then given to the user.
            - If one offered action clearly matches the intent, and the intent gives every required
              field, answer action "act" with that action's exact name and field values that satisfy
              its schema.
            - If the intent is served by going to another resource first, answer action "follow"
              with the exact rel and title of one link in <links>.
            - If no offered action or link matches, or the intent is ambiguous, or a required value is
              missing, answer action "clarify" with one short question for the user.
            Never invent an action that is not in <offers>, or a link that is not in <links>. Never guess a required value.
        """.trimIndent()

        private val TOOL: JsonObject = buildJsonObject {
            put("name", TOOL_NAME)
            put(
                "description",
                "Select one offered action with its field values, ask for an action's detail, " +
                    "follow one offered link, or ask a question.",
            )
            putJsonObject("input_schema") {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("action") {
                        put("type", "string")
                        put("enum", JsonArray(ACTIONS.map(::jsonString)))
                    }
                    putJsonObject("affordance") { put("type", "string") }
                    putJsonObject("fields") { put("type", "object") }
                    putJsonObject("rel") { put("type", "string") }
                    putJsonObject("title") { put("type", "string") }
                    putJsonObject("question") { put("type", "string") }
                }
                put("required", JsonArray(listOf(jsonString("action"))))
            }
            putJsonObject("cache_control") { put("type", "ephemeral") }
        }

        private fun jsonString(value: String) = kotlinx.serialization.json.JsonPrimitive(value)
    }
}
