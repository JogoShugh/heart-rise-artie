package heart

import io.github.optimumcode.json.schema.JsonSchema
import io.github.optimumcode.json.schema.SchemaType
import io.github.optimumcode.json.schema.ValidationError
import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest
import java.util.Base64

/**
 * Each form's declared JSON Schema (2020-12), compiled for the check just
 * before an act is sent (SPEC §6.3). Schemas are found at run time, so each
 * is compiled once, kept by its integrity value, or by a digest of it when
 * it came inline. Only what a schema disallows is refused: nothing is
 * coerced, filled in or removed; a keyword the validator does not know
 * (such as hr:options, which Choices reads) asserts nothing; and a format is
 * an annotation, not a limit. A schema that cannot be compiled checks
 * nothing, so the service decides; it is reported once, with no field values.
 */
internal class DeclaredSchemas(private val decisions: DecisionLog) {

    /** What a held schema says of a body: its violations, or nothing when it could not be compiled. */
    private fun interface Check {
        fun violations(fields: JsonObject): List<String>
    }

    private val held = mutableMapOf<String, Check>()

    /** One sentence per way [fields] break [form]'s schema, each naming where in the body; empty means none. */
    fun violations(form: AffordanceForm, fields: JsonObject, url: String): List<String> {
        val key = form.schemaRef?.integrity ?: digest(form.schema)
        return held.getOrPut(key) { compile(form, key, url) }.violations(fields)
    }

    private fun compile(form: AffordanceForm, key: String, url: String): Check {
        val schema = try {
            JsonSchema.fromJsonElement(form.schema, SchemaType.DRAFT_2020_12)
        } catch (refused: IllegalArgumentException) {
            decisions.schemaUncompiled(form.rel, key, url, refused.message)
            return Check { emptyList() }
        }
        return Check { fields ->
            mutableListOf<ValidationError>().also { errors -> schema.validate(fields, errors::add) }.map(::sentence)
        }
    }

    /** Where in the body, as a JSON Pointer, and what the schema says of it; the message alone at the root. */
    private fun sentence(error: ValidationError): String =
        "${error.objectPath}".takeIf { it.isNotEmpty() }?.let { "$it: ${error.message}" } ?: error.message

    private fun digest(schema: JsonObject): String =
        "sha256-" + Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(schema.toString().toByteArray(Charsets.UTF_8)),
        )
}
