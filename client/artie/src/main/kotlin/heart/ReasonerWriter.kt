package heart

import heart.script.Writes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A bare script's one reasoned line (spec/ideas/bare-runs.md, "Rails"), written by ARTIE's [reasoner]: where there
 * is no form to choose from, only words to send, it is offered one form of its own, `write`, whose one field is the
 * text; it chooses that form and fills it, as it would any other. Nothing it isn't shown is shown: no state, no
 * links, no address.
 */
class ReasonerWriter(private val reasoner: Reasoner?) : Writes {
    override fun write(intent: String): String? {
        val chosen = reasoner?.reason(intent, JsonObject(emptyMap()), listOf(OFFER), emptyList())
        val act = (chosen as? Selection.Act)?.takeIf { it.affordance == FORM }
        return (act?.fields?.get(FIELD) as? JsonPrimitive)?.takeIf { it.isString }?.content
    }

    private companion object {
        const val FORM = "write"
        const val FIELD = "text"
        val OFFER = Offer(
            FORM,
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") { putJsonObject(FIELD) { put("type", "string") } }
                putJsonArray("required") { add(JsonPrimitive(FIELD)) }
            },
            title = "Write the words",
            description = "The words the intent asks for, sent as written.",
        )
    }
}
