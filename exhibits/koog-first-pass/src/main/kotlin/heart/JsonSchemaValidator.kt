package heart

import kotlinx.serialization.json.JsonObject

fun interface JsonSchemaValidator {
    fun validate(data: JsonObject, schema: JsonObject): Boolean
}