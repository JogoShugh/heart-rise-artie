package heart

import kotlinx.serialization.json.JsonObject

interface JsonSchemaValidator {
    fun validate(data: JsonObject, schema: JsonObject): Boolean
}