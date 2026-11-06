package com.artie.heart.sims

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.concurrent.ConcurrentHashMap

/** How a service links its affordance set instead: under [rel], with the curie [curie] for [href], or none. */
data class SetLink(val rel: String, val curie: String? = null, val href: String? = null)

/**
 * What a service's representations say wrong: each fault rewrites the JSON of
 * every answer that has the part it names, and leaves the rest as served.
 */
class BodyFaults {
    private val mapper = ObjectMapper()

    /** A schema each read declares for a form, by its key, in place of the one the service checks. */
    val declared = ConcurrentHashMap<String, JsonNode>()

    /** A HAL-FORMS template each read carries, by its name, in place of the one the service renders. */
    val templates = ConcurrentHashMap<String, JsonNode>()

    /** Members each form, by its key, has instead: a null member is left out. */
    val reshaped = ConcurrentHashMap<String, JsonNode>()

    /** Members each representation's state has besides its own. */
    @Volatile var members: JsonNode? = null

    /** The only `_links` each representation has. */
    @Volatile var linksOnly: JsonNode? = null

    /** The only `_embedded` each representation has. */
    @Volatile var embedsOnly: JsonNode? = null

    /** The profile each representation names in its own `_links`, as a static host's file would. */
    @Volatile var profileLink: String? = null

    /** Where each read links the affordance set instead. */
    @Volatile var setLink: SetLink? = null

    /** Whether each read links the affordance set with an integrity its bytes do not match. */
    @Volatile var wrongIntegrity = false

    /** Whether each read links the affordance set with no integrity at all. */
    @Volatile var noIntegrity = false

    /** Whether each read links no affordance set. */
    @Volatile var noSet = false

    /** Whether every affordance set is linked at [ONE_ADDRESS], whatever it holds. */
    @Volatile var oneAddress = false

    /** The set behind the one address, as last linked. */
    @Volatile var behindOneAddress: String? = null
        private set

    fun json(text: String): JsonNode = mapper.readTree(text)

    fun clear() {
        listOf(declared, templates, reshaped).forEach { it.clear() }
        members = null
        linksOnly = null
        embedsOnly = null
        profileLink = null
        setLink = null
        wrongIntegrity = false
        noIntegrity = false
        noSet = false
        oneAddress = false
        behindOneAddress = null
    }

    /** [body] with each fault applied, or null when none changed it. */
    fun rewritten(body: String): String? {
        val root = (runCatching { mapper.readTree(body) }.getOrNull() as? ObjectNode) ?: return null
        val changed = listOf(forms(root), templates(root), state(root), links(root), profile(root)).any { it }
        return if (changed) mapper.writeValueAsString(root) else null
    }

    private fun forms(root: ObjectNode): Boolean {
        val forms = root.get("_forms") as? ObjectNode ?: return false
        declared.forEach { (key, schema) -> (forms.get(key) as? ObjectNode)?.set<JsonNode>("schema", schema) }
        // A form the service does not offer is offered as the patch says.
        reshaped.forEach { (key, patch) ->
            val form = forms.get(key) as? ObjectNode ?: forms.putObject(key)
            patch.properties().forEach { (name, value) -> if (value.isNull) form.remove(name) else form.set<JsonNode>(name, value) }
        }
        return declared.isNotEmpty() || reshaped.isNotEmpty()
    }

    private fun templates(root: ObjectNode): Boolean {
        val forms = root.get("_templates") as? ObjectNode ?: return false
        templates.forEach { (name, template) -> forms.set<JsonNode>(name, template) }
        return templates.isNotEmpty()
    }

    private fun state(root: ObjectNode): Boolean {
        members?.properties()?.forEach { (name, value) -> root.set<JsonNode>(name, value) }
        embedsOnly?.let { root.set<JsonNode>("_embedded", it) }
        linksOnly?.let { root.set<JsonNode>("_links", it) }
        return members != null || embedsOnly != null || linksOnly != null
    }

    private fun profile(root: ObjectNode): Boolean {
        val href = profileLink ?: return false
        val links = root.get("_links") as? ObjectNode ?: root.putObject("_links")
        links.putObject("profile").put("href", href)
        return true
    }

    private fun links(root: ObjectNode): Boolean {
        val links = root.get("_links") as? ObjectNode ?: return false
        val set = links.get(FORMS) as? ObjectNode ?: return false
        if (noSet) links.remove(FORMS)
        if (noIntegrity) set.remove("integrity")
        if (wrongIntegrity) set.put("integrity", WRONG_INTEGRITY)
        if (oneAddress) {
            behindOneAddress = set.get("href").asText()
            set.put("href", ONE_ADDRESS)
        }
        setLink?.let { relinked(links, set, it) }
        return true
    }

    private fun relinked(links: ObjectNode, set: ObjectNode, link: SetLink) {
        links.remove(FORMS)
        links.set<JsonNode>(link.rel, set)
        val kept = (links.get("curies") as? ArrayNode)?.filterNot { it.path("name").asText() == HR }.orEmpty()
        val curies: ArrayNode = links.putArray("curies")
        kept.forEach { curies.add(it) }
        if (link.curie != null) {
            curies.addObject().put("name", link.curie).put("href", link.href).put("templated", true)
        }
    }

    companion object {
        const val ONE_ADDRESS = "/affordance-sets/the-one"
        private const val FORMS = "hr:forms"
        private const val HR = "hr"
        private const val WRONG_INTEGRITY = "sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    }
}
