import heart.UniformArgs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

inline fun <reified T> T.toUniformArgs(): UniformArgs {
    return UniformArgs(Json.Default.encodeToJsonElement(this).jsonObject)
}