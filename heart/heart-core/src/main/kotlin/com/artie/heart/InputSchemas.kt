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

/**
 * An act's JSON Schema (draft 2020-12), generated from its input class:
 * Jakarta Validation annotations give the constraints (`@NotNull` makes a
 * field required), and OpenAPI's `@Schema` gives the title, description and
 * examples. The schema is written once, as code, and never by hand.
 */
object InputSchemas {
    private val mapper = ObjectMapper()

    private val generator: SchemaGenerator =
        SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
        .without(Option.SCHEMA_VERSION_INDICATOR)
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
            // An input with no fields still says it takes an object with no properties.
            forTypesInGeneral().withTypeAttributeOverride { node, _, _ ->
                if (node.path("type").asText() == "object" && !node.has("properties")) node.putObject("properties")
            }
        }
        .build()
        .let(::SchemaGenerator)

    /** The schema of [type], as JSON text. */
    fun of(type: Class<*>): String = generator.generateSchema(type).toString()

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
