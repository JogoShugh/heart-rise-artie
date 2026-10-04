package com.artie.gardenbed.web

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Validator

/** 428: a state-changing request arrived without If-Match (SPEC §6.2). */
class PreconditionRequiredException(message: String) : RuntimeException(message)

/** 422: the body is JSON but does not satisfy the affordance's schema. */
class UnprocessableContentException(val violations: List<String>, cause: Throwable? = null) :
    RuntimeException("request body does not satisfy the affordance schema", cause)

/** 400: the body is not JSON at all. */
class MalformedActBodyException(message: String, cause: Throwable) : RuntimeException(message, cause)

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
            throw UnprocessableContentException(violations.map { "${it.propertyPath}: ${it.message}" }.sorted())
        }
        return value
    }

    private fun <T : Any> parse(body: String?, type: Class<T>): T =
        try {
            objectMapper.readValue(body?.ifBlank { null } ?: "{}", type)
        } catch (e: JsonParseException) {
            throw MalformedActBodyException("request body is not valid JSON: ${e.originalMessage}", e)
        } catch (e: JsonProcessingException) {
            // Valid JSON, wrong shape: a field of the wrong type is a schema failure.
            throw UnprocessableContentException(listOf(e.originalMessage), e)
        }
}
