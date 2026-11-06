package heart

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object HalSchemaToDescriptorConverter {
    fun convert(formId: String, schema: JsonObject): ToolDescriptor {
        val props = schema["properties"]?.jsonObject ?: JsonObject(emptyMap())
        val req = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()

        return ToolDescriptor(
            name = formId,
            description = schema["title"]?.jsonPrimitive?.content ?: "Action: $formId",
            requiredParameters = props.filter { it.key in req }.map { it.toParam() },
            optionalParameters = props.filter { it.key !in req }.map { it.toParam() }
        )
    }

    private fun Map.Entry<String, JsonElement>.toParam(): ToolParameterDescriptor {
        val p = value.jsonObject
        return ToolParameterDescriptor(
            name = key,
            description = p["title"]?.jsonPrimitive?.content ?: key,
            type = when (p["type"]?.jsonPrimitive?.content) {
                "integer" -> ToolParameterType.Integer
                "boolean" -> ToolParameterType.Boolean
                "number" -> ToolParameterType.Float
                else -> ToolParameterType.String
            }
        )
    }
}