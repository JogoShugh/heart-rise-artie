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
    private val checks: Map<String, BodyCheck> = type.allActs.associate { it.name to BodyCheck(it.schema) }

    /**
     * Steps 1 to 3 for an act a resource offers. [current] reads the
     * resource's state, only when a validator was sent; [matches] compares
     * the sent [validator] with the one the binding selects for that state.
     * A validator is whatever the binding carries: an HTTP If-Match value,
     * CoAP If-Match options. Returns the state checked.
     */
    suspend fun <V : Any> check(act: String, validator: V?, current: suspend () -> S, matches: (S, V) -> Boolean): S {
        require(!type.atBookmark(act)) { "$act is offered by the bookmark; use checkAtBookmark" }
        var state: S? = null
        steps(act, validator, { held -> current().also { state = it }.let { matches(it, held) } }) {
            val key = type.state.keyOf(checkNotNull(state))
            if (act in type.offers.offered(key)) null else type.offers.whyAbsent(key, act)
        }
        return checkNotNull(state)
    }

    /**
     * Steps 1 to 3 for an act the service index offers. Its owning resource
     * is the index, so [matches] compares the sent [validator] with the
     * index's current one; the index always offers its acts.
     */
    suspend fun <V : Any> checkAtBookmark(act: String, validator: V?, matches: (V) -> Boolean) {
        require(type.atBookmark(act)) { "$act is not offered by the bookmark" }
        steps(act, validator, { matches(it) }) { null }
    }

    /**
     * The order of SPEC §6.2: [notOffered] says why the act is absent now, or is
     * null when it is offered. A detail names the act by its relation's CURIE, as
     * the forms do, never by its name in the code.
     */
    private suspend fun <V : Any> steps(
        act: String,
        validator: V?,
        matches: suspend (V) -> Boolean,
        notOffered: () -> String?,
    ) {
        val relation = type.act(act).relation
        val curie = type.profile.key(relation)
        val refusal = when {
            validator == null ->
                PreconditionRequiredException("$curie needs a validator: the current one of the resource offering it")
            !matches(validator) ->
                StaleWriteConflict("the validator does not match the current one of the selected representation")
            else -> notOffered()?.let { why ->
                NotOfferedException("$curie is not offered now: $why", rel = type.profile.reference(relation))
            }
        }
        refusal?.let { throw it }
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
