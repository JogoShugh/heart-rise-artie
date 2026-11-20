package heart

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * One request to the Messages API, as ARTIE's reasoner and its planner both send it: the one [tool] it may call, the
 * [system] prompt marked for the cache (the prefix every turn shares), and one user turn of [text].
 */
internal fun claudeRequest(model: String, maxTokens: Int, tool: JsonObject, system: String, text: String): JsonObject =
    buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        putJsonArray("tools") { add(tool) }
        putJsonObject("tool_choice") { put("type", "auto") }
        putJsonArray("system") {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", system)
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
                                put("text", text)
                            },
                        )
                    }
                },
            )
        }
    }
