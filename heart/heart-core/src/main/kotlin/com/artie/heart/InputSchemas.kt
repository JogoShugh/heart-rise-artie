package com.artie.heart

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.TextNode
import com.github.victools.jsonschema.generator.Option
import com.github.victools.jsonschema.generator.OptionPreset
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption
import com.github.victools.jsonschema.module.swagger2.Swagger2Module
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.PathType
import com.networknt.schema.SchemaValidatorsConfig
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/**
 * An act's JSON Schema (draft 2020-12), generated from its input class:
 * Jakarta Validation annotations give the constraints (`@NotNull` makes a
 * field required), and OpenAPI's `@Schema` gives the title, description and
 * examples. The schema is written once, as code, and never by hand.
 */
object InputSchemas {
    private val mapper = ObjectMapper()

    /**
     * Some character that is not white space. JSON Schema searches for a
     * pattern anywhere in the value, while HAL-FORMS clients that behave like
     * HTML match `regex` against the whole value, so the pattern means the
     * same under both.
     */
    private const val NOT_BLANK = "[\\s\\S]*\\S[\\s\\S]*"

    private val generator: SchemaGenerator =
        SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
        .with(Option.SCHEMA_VERSION_INDICATOR)
        .with(
            JakartaValidationModule(
                JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
                JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS,
            ),
        )
        .with(Swagger2Module())
        .apply {
            // @Schema(examples = [...]) as JSON values: "50" is the number 50, "basil" stays text.
            forFields().withInstanceAttributeOverride { node, field, _ ->
                val examples = field.getAnnotationConsideringFieldAndGetter(Schema::class.java)?.examples.orEmpty()
                if (examples.isNotEmpty()) node.putArray("examples").addAll(examples.map(::exampleValue))
            }
            // @NotBlank is at least one character that is not white space. The Jakarta module
            // says only minLength 1, which lets "   " through, so a pattern says the rest.
            // A field with its own @Pattern keeps it, and the not-blank rule goes beside it.
            forFields().withInstanceAttributeOverride { node, field, _ ->
                if (field.getAnnotationConsideringFieldAndGetter(NotBlank::class.java) != null) {
                    if (node.has("pattern")) {
                        node.putArray("allOf").addObject().put("pattern", NOT_BLANK)
                    } else {
                        node.put("pattern", NOT_BLANK)
                    }
                }
            }
            // @Schema(deprecated = true): a field kept for old clients, which new ones should leave alone.
            forFields().withInstanceAttributeOverride { node, field, _ ->
                if (field.getAnnotationConsideringFieldAndGetter(Schema::class.java)?.deprecated == true) {
                    node.put("deprecated", true)
                }
            }
            // An input with no fields still says it takes an object with no properties.
            forTypesInGeneral().withTypeAttributeOverride { node, _, _ ->
                if (node.path("type").asText() == "object" && !node.has("properties")) node.putObject("properties")
            }
        }
        .build()
        .let(::SchemaGenerator)

    /** The schema of [type], as JSON text. */
    fun of(type: Class<*>): String = generator.generateSchema(type).toString()

    /** The names of the properties [schema] declares, in its order. */
    fun propertyNames(schema: String): List<String> =
        mapper.readTree(schema).path("properties").fieldNames().asSequence().toList()

    /** The JSON Schema type [schema] gives property [name], or of its items when it is an array; null when none. */
    fun propertyType(schema: String, name: String): Pair<String?, String?> {
        val property = mapper.readTree(schema).path("properties").path(name)
        return property.path("type").asText(null) to property.path("items").path("type").asText(null)
    }

    private fun exampleValue(text: String): JsonNode =
        try {
            mapper.readTree(text)
        } catch (_: JsonProcessingException) {
            TextNode(text)
        }
}

/**
 * Checks an act's body against its schema, the same schema its form serves.
 * Not JSON at all is malformed (400); JSON that breaks the schema is
 * unprocessable (422), with one violation per failing field as an RFC 6901
 * JSON Pointer. A missing or blank body is the empty object.
 */
class BodyCheck(schema: String) {
    private val compiled = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(schema, SchemaValidatorsConfig.builder().pathType(PathType.JSON_POINTER).build())

    /** The body as a JSON tree, when it satisfies the schema. */
    fun check(body: String?): JsonNode {
        val node = try {
            mapper.readTree(body?.ifBlank { null } ?: "{}")
        } catch (e: JsonProcessingException) {
            throw MalformedActBodyException("request body is not valid JSON: ${e.originalMessage}", e)
        }
        val failures = compiled.validate(node)
        if (failures.isNotEmpty()) {
            throw UnprocessableContentException(failures.map(::violation).sortedBy { it.pointer })
        }
        return node
    }

    /** A missing required member is reported at the member's own pointer, not at its parent's. */
    private fun violation(message: ValidationMessage): Violation {
        val at = message.instanceLocation.toString()
        val pointer = if (message.type == "required") at + pointerOf(listOf(message.property)) else at
        return Violation(pointer, message.error ?: message.message)
    }

    private companion object {
        val mapper = ObjectMapper()
    }
}
