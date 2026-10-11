package com.artie.heart

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

/** An act's checked body: a JSON tree that satisfies the act's schema. */
class ActInput(val json: JsonNode, private val mapper: ObjectMapper) {
    /** The body as the act's input class. It satisfies the schema generated from that class. */
    fun <T : Any> bind(type: Class<T>): T = mapper.treeToValue(json, type)
}

/**
 * The checks before an act and the act itself (SPEC §6.2), the same for every
 * resource type and every binding. Each binding says what the act's validator
 * is and how it is compared; the order of the checks and what they refuse
 * are the same everywhere:
 *
 * 1. no validator: 428 ([PreconditionRequiredException]);
 * 2. a validator that does not match the current one: 412 ([StaleWriteConflict]);
 * 3. an act the current state does not offer: 409 ([NotOfferedException]);
 * 4. a body that breaks the act's schema: 422, or 400 when it is not JSON.
 *
 * The first failure wins, and nothing is performed after a refusal. A
 * [ConcurrentWriteException] while the act runs is stale too.
 */
class ActPipeline<S, K>(private val type: ResourceType<S, K>, private val mapper: ObjectMapper = ObjectMapper()) {
    private val checks: Map<String, BodyCheck> = type.acts.associate { it.name to BodyCheck(it.schema) }

    /**
     * Steps 1 to 3. [current] reads the resource's state, only when a
     * validator was sent; [matches] compares the sent [validator] with the
     * one the binding selects for that state. A validator is whatever the
     * binding carries: an HTTP If-Match value, CoAP If-Match options.
     * Returns the state checked.
     */
    suspend fun <V : Any> check(act: String, validator: V?, current: suspend () -> S, matches: (S, V) -> Boolean): S {
        val declared = type.act(act)
        if (validator == null) {
            throw PreconditionRequiredException("$act needs a validator: the current one of the resource it acts on")
        }
        val state = current()
        val key = type.state.keyOf(state)
        val refusal = when {
            !matches(state, validator) ->
                StaleWriteConflict("the validator does not match the current one of the selected representation")
            act !in type.offers.offered(key) -> NotOfferedException(
                "$act is not in the resource's current affordance set (key $key)",
                rel = type.profile.reference(declared.relation),
            )
            else -> null
        }
        refusal?.let { throw it }
        return state
    }

    /** Step 4, then the act: [perform] runs with the checked body. */
    suspend fun <R> run(act: String, body: String?, perform: suspend (ActInput) -> R): R {
        val input = ActInput(checks.getValue(type.act(act).name).check(body), mapper)
        return try {
            perform(input)
        } catch (e: ConcurrentWriteException) {
            throw StaleWriteConflict(e.message ?: "another write landed first", e)
        }
    }
}
