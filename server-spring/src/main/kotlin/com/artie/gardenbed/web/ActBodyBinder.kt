package com.artie.gardenbed.web

import com.artie.heart.MalformedActBodyException
import com.artie.heart.UnprocessableContentException
import com.artie.heart.Violation
import com.artie.heart.pointerOf
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Validator

/**
 * Binds and validates an act's body. SPEC [0.5] §6.2 puts schema
 * validation last (428, 412, 409, then 422), so the controller calls this
 * only after the precondition and legality checks. Letting Spring bind
 * with `@Valid @RequestBody` would run it first, and a bad body would
 * mask a stale or missing If-Match.
 */
class ActBodyBinder(private val objectMapper: ObjectMapper, private val validator: Validator) {

    fun <T : Any> bind(body: String?, type: Class<T>): T {
        val value = parse(body, type)
        val violations = validator.validate(value)
        if (violations.isNotEmpty()) {
            throw UnprocessableContentException(
                violations.map { Violation(pointerOf(it.propertyPath.map { node -> node.name }), it.message) }
                    .sortedBy { it.pointer },
            )
        }
        return value
    }

    private fun <T : Any> parse(body: String?, type: Class<T>): T =
        try {
            objectMapper.readValue(body?.ifBlank { null } ?: "{}", type)
        } catch (e: JsonParseException) {
            throw MalformedActBodyException("request body is not valid JSON: ${e.originalMessage}", e)
        } catch (e: JsonMappingException) {
            // Valid JSON, wrong shape: a field of the wrong type is a schema failure.
            val pointer = pointerOf(e.path.map { it.fieldName ?: it.index.toString() })
            throw UnprocessableContentException(listOf(Violation(pointer, e.originalMessage)), e)
        } catch (e: JsonProcessingException) {
            throw UnprocessableContentException(listOf(Violation("", e.originalMessage)), e)
        }
}
