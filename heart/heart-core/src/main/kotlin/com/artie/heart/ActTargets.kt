package com.artie.heart

/**
 * SPEC §6.3: an act's target is a resource of its own. Its representation is
 * small: a link `up` to its owning resource, the owner's ETag it is offered
 * under, and a link to its form where the form already lives, in the owner's
 * shared affordance set, by a JSON Pointer fragment (RFC 6901 §6) with the
 * set's integrity and the form's own (HAL-SCHEMA-FORMS-HR Rules 2 and 10).
 * No copy of the form, and none of the owner's state: the body changes
 * exactly when the owner's representation does, so the act's ETag is the
 * owner's, and a client that read the owner already holds it.
 */
class ActTargets<S, K>(private val layers: Layers<S, K>) {
    private val type = layers.type

    /** [act]'s target on [state], in HAL Schema Forms at [rung], under the owner's [ownerETag]. */
    fun of(state: S, act: Act, rung: Rung, ownerETag: String): Map<String, Any?> {
        val item = type.itemPath.expand(type.state.bodyOf(state))
        // L0 embeds the owner's forms; its act links them in the set that holds them with their schemas.
        val set = layers.affordanceSet(type.state.keyOf(state), rung.setRendering ?: SetRendering.EMBEDDED_SCHEMAS)
        val key = type.profile.key(act.relation)
        val pointer = "/_forms/" + key.replace("~", "~0").replace("/", "~1")
        @Suppress("UNCHECKED_CAST")
        val form = ((JsonTrees.plain(set) as Map<String, Any?>)["_forms"] as Map<String, Map<String, Any?>>)
            .getValue(key)
        return linkedMapOf(
            OWNER_ETAG to ownerETag,
            "_links" to linkedMapOf(
                "self" to mapOf("href" to "$item/actions/${act.path}"),
                "up" to mapOf("href" to item),
                "curies" to listOf(Layers.HR_CURIE),
                FORM_REL to linkedMapOf(
                    "href" to Layers.AFFORDANCE_SETS + ContentAddress.address(set) + "#" + pointer,
                    "type" to Dialect.JSONSCHEMA.mediaType,
                    "profile" to Dialect.JSONSCHEMA.profile,
                    // What the set's bytes must hash to, and what the form in it does (its own integrity).
                    "integrity" to ContentAddress.integrity(set),
                    "hr:integrity" to form["hr:integrity"],
                ),
            ),
        )
    }

    /** [act]'s target on [state] in HAL-FORMS, the L0 on-ramp: its one template, as the owner renders it. */
    fun halForms(state: S, act: Act, ownerETag: String): Map<String, Any?> {
        val owner = layers.halForms(state)
        @Suppress("UNCHECKED_CAST")
        val template = (owner["_templates"] as Map<String, Any?>?)?.get(act.name)
        val item = type.itemPath.expand(type.state.bodyOf(state))
        return linkedMapOf(
            OWNER_ETAG to ownerETag,
            "_links" to mapOf("self" to mapOf("href" to "$item/actions/${act.path}"), "up" to mapOf("href" to item)),
            "_templates" to mapOf(act.name to template),
        )
    }

    companion object {
        /** The relation from an act's target to its form. */
        const val FORM_REL = "hr:form"

        /** The member that names the owner's representation the act is offered on. */
        const val OWNER_ETAG = "ownerETag"
    }
}
