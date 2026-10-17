package com.artie.heart

/**
 * Renders a declared resource type in the three layers of SPEC §6.4:
 *
 * - Layer 1, the resource: mutable and revalidated. At L0 it carries its
 *   forms and their full schemas inline; at L1 and L2 it links to the
 *   affordance set rendered for that rung.
 * - Layer 2, the affordance sets: one per (offer key, rendering), shared by
 *   every resource with that key, immutable and named by content (§6.5).
 *   Targets stay URI templates, so no set names one resource (§6.9).
 * - Layer 3, the schemas: immutable and named by content.
 *
 * Every Layer 2 and Layer 3 body, and every absence explanation (§6.12), is
 * computed once, here, from the declaration, as the JCS canonical bytes that
 * are hashed and served.
 */
class Layers<S, K>(val type: ResourceType<S, K>) {

    private val schemaBodies: Map<String, String> =
        type.allActs.associate { it.name to ContentAddress.canonicalText(it.schema) }

    /** Address -> canonical schema, for `/schemas/{address}`. */
    val schemas: Map<String, String> = SharedContent.named(schemaBodies.values)

    /** Each offer key's sets and explanation: a declared key's from start, an open key's once met. */
    private val content = OfferContent(type) { names, embedSchemas -> forms(names, embedSchemas) }

    /**
     * Address -> canonical explanation of the acts a key leaves out, for
     * `/unavailable/{address}`. With open offers it grows as keys are met.
     */
    val explanations: Map<String, String> get() = content.explanations

    /** Address -> canonical affordance set, for `/affordance-sets/{address}`. It grows as open keys are met. */
    val affordanceSets: Map<String, String> get() = content.affordanceSets

    /** The acts [key] offers, by name, in order. */
    fun offered(key: K): List<String> = type.offers.offered(content.known(key))

    /** The canonical affordance set of [key] at [rendering]. */
    fun affordanceSet(key: K, rendering: SetRendering): String = content.affordanceSet(key, rendering)

    /**
     * Layer 1 of [state] at [rung]. L0, the default, collapses all three
     * layers: state, forms and full schemas, one hop. L1 and L2: state and a
     * link to that rung's affordance set; `type` and `profile` on the link
     * tell a client what it will get before fetching it.
     */
    fun layer1(state: S, rung: Rung = Rung.L0): Map<String, Any?> {
        val key = type.state.keyOf(state)
        val body = LinkedHashMap(type.state.bodyOf(state))
        val item = type.itemPath.expand(body)
        val self = mapOf("href" to item)
        val rendering = rung.setRendering
        if (rendering == null) {
            body["_links"] = mapOf(
                "self" to self,
                "curies" to type.profile.curies + HR_CURIE,
                // SPEC §6.12: at L0 the set is embedded, so its explanation link is in Layer 1.
                UNAVAILABLE_REL to explanationLink(key),
            )
            body["_forms"] = forms(offered(key), embedSchemas = true).mapValues { (_, form) -> resolved(form, item) }
        } else {
            val set = affordanceSet(key, rendering)
            body["_links"] = mapOf(
                "self" to self,
                // SPEC §6.1: the rel is $HEART_RISE_PROFILE/rels/forms, written as the CURIE hr:forms.
                "curies" to listOf(HR_CURIE),
                FORMS_REL to mapOf(
                    "href" to AFFORDANCE_SETS + ContentAddress.address(set),
                    "type" to Dialect.JSONSCHEMA.mediaType,
                    // HAL's own link hint; replaces the withdrawn media-type parameter (§6.1).
                    "profile" to Dialect.JSONSCHEMA.profile,
                    // HAL-SCHEMA-FORMS-HR Rule 2: what the set's bytes must hash to.
                    "integrity" to ContentAddress.integrity(set),
                ),
            )
        }
        return body
    }

    /**
     * An RFC 8288 Link to [state]'s affordance set, with each schema inline,
     * for an answer that carries no forms of its own: the plain JSON twin.
     * A client that follows it learns which acts the state allows now.
     */
    fun formsLink(state: S): String {
        val set = affordanceSet(type.state.keyOf(state), SetRendering.EMBEDDED_SCHEMAS)
        return "<$AFFORDANCE_SETS${ContentAddress.address(set)}>; rel=\"$HEART_RISE_PROFILE/rels/forms\"; " +
            "type=\"${Dialect.JSONSCHEMA.mediaType}\"; integrity=\"${ContentAddress.integrity(set)}\""
    }

    /**
     * Layer 1 of [state] in HAL-FORMS, the L0 on-ramp: the state, its self
     * link, and one template per offered act. A template's properties come
     * from the act's schema ([HalFormsProperties]), as the index's do, so
     * the two dialects say the same about every field. Every template keeps
     * its act's name. HAL-FORMS says a lone template SHOULD be keyed
     * "default", but that name says nothing about the act.
     */
    fun halForms(state: S): Map<String, Any?> {
        val body = LinkedHashMap(type.state.bodyOf(state))
        val item = type.itemPath.expand(body)
        body["_links"] = mapOf("self" to mapOf("href" to item))
        val templates = offered(type.state.keyOf(state)).associate { name ->
            val act = type.act(name)
            name to mapOf(
                "title" to type.profile.relation(act.relation).title,
                "method" to "POST",
                "contentType" to JSON_CONTENT,
                "target" to "$item/actions/${act.path}",
                // HAL-SCHEMA-FORMS-HR Rule 8: each property's choices for this state, as HAL-FORMS options.
                "properties" to HalFormsProperties.of(
                    act.schema,
                    Options.halForms(act, state, type.state.bodyOf(state)),
                ),
            )
        }
        if (templates.isNotEmpty()) body["_templates"] = templates
        return body
    }

    /** HAL-SCHEMA-FORMS-HR Rules 6 and 7: forms keyed by relation, and self-describing. */
    private fun forms(names: List<String>, embedSchemas: Boolean): Map<String, Any?> =
        names.associate { name ->
            val act = type.act(name)
            type.profile.key(act.relation) to form(act, embedSchemas)
        }

    /**
     * One form. Its target stays a URI template, so the set that holds it is
     * the same bytes for every resource with one key; a client expands each of
     * the item path's variables from its JSON Pointer in `hr:templatePointers`
     * (HAL-SCHEMA-FORMS-HR Rule 1).
     */
    private fun form(act: Act, embedSchemas: Boolean): Map<String, Any?> {
        val relation = type.profile.relation(act.relation)
        val body = schemaBodies.getValue(act.name)
        val id = SCHEMAS + ContentAddress.address(body)
        val schema: Map<String, Any?> = if (embedSchemas) {
            @Suppress("UNCHECKED_CAST")
            LinkedHashMap(JsonTrees.plain(body) as Map<String, Any?>).apply { put("\$id", id) }
        } else {
            // HAL-SCHEMA-FORMS-HR Rule 3: a reference, pinned by the schema's integrity.
            mapOf("\$ref" to id, "hr:integrity" to ContentAddress.integrity(body))
        }
        return mapOf(
            "title" to relation.title,
            "description" to relation.description,
            "_links" to mapOf(
                "target" to mapOf("href" to "${type.itemPath.template}/actions/${act.path}", "templated" to true),
            ),
            TEMPLATE_POINTERS to type.itemPath.pointers,
            "method" to "POST",
            "contentType" to "application/json",
            "schema" to schema,
        )
    }

    /** At L0 a form belongs to one resource: its target is concrete and needs no pointers. */
    private fun resolved(form: Any?, item: String): Map<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val entry = form as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val target = (entry.getValue("_links") as Map<String, Map<String, Any?>>).getValue("target")
        val href = (target.getValue("href") as String).replace(type.itemPath.template, item)
        return (entry - TEMPLATE_POINTERS) + ("_links" to mapOf("target" to mapOf("href" to href)))
    }

    private fun explanationLink(key: K): Map<String, Any> = content.explanationLink(key)

    companion object {
        /** Where each kind of content-addressed resource is served; the address follows. */
        const val AFFORDANCE_SETS = "/affordance-sets/"
        const val SCHEMAS = "/schemas/"
        const val EXPLANATIONS = "/unavailable/"

        const val FORMS_REL = "hr:forms"
        const val UNAVAILABLE_REL = "hr:unavailable"
        private const val TEMPLATE_POINTERS = "hr:templatePointers"
        private const val JSON_CONTENT = "application/json"
        internal val HR_CURIE: Map<String, Any> =
            mapOf("name" to "hr", "href" to "$HEART_RISE_PROFILE/rels/{rel}", "templated" to true)
    }
}
