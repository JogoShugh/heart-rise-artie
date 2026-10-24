package heart

import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.ValidationError
import kotlinx.serialization.json.JsonObject

class HeartSchemaValidator : JsonSchemaValidator {
    override fun validate(data: JsonObject, schema: JsonObject): Boolean {
        val schemaInstance = JsonSchema.Companion.fromDefinition(schema.toString())
        val errors = mutableListOf<ValidationError>()
        return schemaInstance.validate(data, errors::add).also { valid ->
            if (!valid) {
                val errorMessages = errors.joinToString { it.message }
                throw IllegalArgumentException("H.E.A.R.T. Validation failed: $errorMessages")
            }
        }
    }
}