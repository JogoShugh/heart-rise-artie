package heart

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/**
 * HAL CURIEs (SPEC §6.1, §7.5). A document declares prefixes in
 * `_links.curies`; a link key or form key written `prefix:ref` names the
 * relation the prefix's template gives for `ref`.
 */
object Curies {

    /** The prefix templates a document declares, by name. */
    fun of(root: JsonObject): Map<String?, String?> =
        (root["_links"]?.jsonObject?.get("curies") as? JsonArray).orEmpty().map { it.jsonObject }
            .associate { it["name"]?.jsonPrimitive?.content to it["href"]?.jsonPrimitive?.content }

    /**
     * "hr:forms" with a curies entry {name: hr, href: ".../rels/{rel}"}
     * names ".../rels/forms". A key that is already a URI, or whose prefix has
     * no curies entry, is returned unchanged.
     */
    fun expand(key: String, curies: Map<String?, String?>): String {
        val prefix = key.substringBefore(':', "")
        val template = curies[prefix] ?: return key
        return template.replace("{rel}", key.substringAfter(':'))
    }

    /**
     * HAL-SCHEMA-FORMS-HR Rule 6: a form key is a link relation. A CURIE is
     * expanded and resolved against the document's URL, so the relation is
     * absolute; a key with no declared prefix is kept as written.
     */
    fun relation(key: String, curies: Map<String?, String?>, base: String): String {
        val expanded = expand(key, curies)
        return if (expanded == key) key else URI(base).resolve(expanded).toString()
    }
}
