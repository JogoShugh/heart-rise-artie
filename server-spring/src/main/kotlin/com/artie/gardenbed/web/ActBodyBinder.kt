package com.artie.gardenbed.web

import com.artie.heart.BodyCheck
import com.artie.heart.InputSchemas
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap

/**
 * Binds an act's body. SPEC [0.5] §6.2 puts schema validation last (428,
 * 412, 409, then 422), so the controller calls this only after the
 * precondition and legality checks. Letting Spring bind with
 * `@Valid @RequestBody` would run it first, and a bad body would mask a
 * stale or missing If-Match.
 *
 * The body is checked against the schema generated from [bind]'s input
 * class, the same schema the act's form serves, and bound only when it
 * passes. So the server enforces exactly what it publishes.
 */
class ActBodyBinder(private val objectMapper: ObjectMapper) {
    private val checks = ConcurrentHashMap<Class<*>, BodyCheck>()

    fun <T : Any> bind(body: String?, type: Class<T>): T {
        val node = checks.computeIfAbsent(type) { BodyCheck(InputSchemas.of(it)) }.check(body)
        return objectMapper.treeToValue(node, type)
    }
}
