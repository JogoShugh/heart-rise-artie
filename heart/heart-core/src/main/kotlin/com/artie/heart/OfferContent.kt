package com.artie.heart

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Each offer key's Layer 2 content: its affordance set at every rendering,
 * and the explanation of the acts it leaves out (SPEC §6.5, §6.12), as the
 * canonical bytes that are hashed and served. A declared key's content is
 * rendered at start. With open offers, any other key's is rendered the first
 * time the key is met, and held from then on, so it is served like the rest.
 *
 * [forms] renders the forms of the named acts, with schemas embedded or not.
 */
internal class OfferContent<K>(
    private val type: ResourceType<*, K>,
    private val forms: (names: List<String>, embedSchemas: Boolean) -> Map<String, Any?>,
) {
    /** A key is known once its explanation and every set of it are held. */
    private val explanationByKey = ConcurrentHashMap<K, String>()
    private val setByKey = ConcurrentHashMap<Pair<K, SetRendering>, String>()
    private val explanationsByAddress = ConcurrentHashMap<String, String>()
    private val setsByAddress = ConcurrentHashMap<String, String>()

    /** Address -> explanation; it grows as open keys are met. */
    val explanations: Map<String, String> = Collections.unmodifiableMap(explanationsByAddress)

    /** Address -> affordance set; it grows as open keys are met. */
    val affordanceSets: Map<String, String> = Collections.unmodifiableMap(setsByAddress)

    init {
        type.offers.keys.forEach { known(it) }
    }

    fun affordanceSet(key: K, rendering: SetRendering): String = setByKey.getValue(known(key) to rendering)

    /** The link to why the acts [key] leaves out are absent, pinned by integrity. */
    fun explanationLink(key: K): Map<String, Any> = link(explanationByKey.getValue(known(key)))

    /** [key], rendered and held if it wasn't. With closed offers, a key never declared is refused. */
    fun known(key: K): K {
        if (!explanationByKey.containsKey(key)) {
            synchronized(this) { if (!explanationByKey.containsKey(key)) render(key) }
        }
        return key
    }

    private fun render(key: K) {
        if (!type.offers.open && key !in type.offers.keys) throw NoSuchElementException("key $key is not declared")
        type.checkKey(key)
        val explanation = explanation(key)
        hold(explanationsByAddress, explanation)
        SetRendering.entries.forEach { rendering ->
            // The set declares the CURIEs its keys use (HAL-SCHEMA-FORMS-HR Rule 6), and
            // links, with integrity, to why the other acts are absent (SPEC §6.12).
            val set = ContentAddress.canonical(
                mapOf(
                    "_links" to mapOf(
                        "curies" to type.profile.curies + Layers.HR_CURIE,
                        Layers.UNAVAILABLE_REL to link(explanation),
                    ),
                    "_forms" to forms(type.offers.offered(key), rendering == SetRendering.EMBEDDED_SCHEMAS),
                ),
            )
            hold(setsByAddress, set)
            setByKey[key to rendering] = set
        }
        // Last: a key is known only once all of its content is held.
        explanationByKey[key] = explanation
    }

    private fun explanation(key: K): String {
        val absent = type.acts.map { it.name } - type.offers.offered(key).toSet()
        return ContentAddress.canonical(
            mapOf(
                "unavailable" to absent.map { name ->
                    val relation = type.act(name).relation
                    mapOf(
                        "rel" to type.profile.reference(relation),
                        "title" to type.profile.relation(relation).title,
                        "reason" to type.offers.whyAbsent(key, name),
                    )
                },
            ),
        )
    }

    private companion object {
        /** Holds [body] at its address; two different bodies with one address are refused, never one dropped. */
        fun hold(byAddress: ConcurrentHashMap<String, String>, body: String) {
            val address = ContentAddress.address(body)
            val held = byAddress.putIfAbsent(address, body)
            check(held == null || held == body) { "two different bodies share the name $address" }
        }

        fun link(json: String): Map<String, Any> = mapOf(
            "href" to Layers.EXPLANATIONS + ContentAddress.address(json),
            "type" to "application/json",
            "integrity" to ContentAddress.integrity(json),
        )
    }
}
