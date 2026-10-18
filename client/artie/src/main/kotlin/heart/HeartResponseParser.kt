package heart

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * Reads a Layer 1 representation in either dialect (SPEC §6.1). It must say it
 * conforms to the dialect's profile (HTTP: `Link: rel="profile"`; the old
 * media-type parameter is not accepted). Plain target hrefs are resolved
 * against the URL it came from; templated ones are kept as templates for the
 * act to expand.
 */
object HeartResponseParser {

    /** HAL-SCHEMA-FORMS-HR Rule 1. */
    private const val TEMPLATE_POINTERS = "hr:templatePointers"

    fun parse(received: Received, url: String, dialect: Dialect = Dialect.HAL_SCHEMA_FORMS): HeartRepresentation {
        val profiles = received.profiles
        require(dialect.profile in profiles) {
            "response from $url does not name the profile ${dialect.profile} (Link profiles: $profiles)"
        }
        val root = Json.parseToJsonElement(received.body).jsonObject
        // An act's response comes from the action URL; its self link says which resource it is.
        val self = linkHref(root, "self")
        return HeartRepresentation(
            url = self?.let { URI(url).resolve(it).toString() } ?: url,
            state = stateOf(root),
            manifest = if (dialect == Dialect.HAL_FORMS) {
                Manifest(HalFormsTemplates.forms(root, url), base = url)
            } else {
                manifest(root, url)
            },
            etag = received.validator,
            forms = linkObject(root, FORMS_REL)?.let { link(it, url) },
            related = related(root, url),
        )
    }

    /** SPEC §6.1: the state is the body without exactly these four; any other member is state. */
    private val CONTROLS = setOf(LINKS, EMBEDDED, FORMS, HalFormsTemplates.TEMPLATES)

    private val stateOf: (JsonObject) -> JsonObject = { body -> JsonObject(body.filterKeys { it !in CONTROLS }) }

    /**
     * A resource object's links and its embedded resources, each by the rel the server wrote: one
     * object or an array of them. An embedded object without a self link names no resource, so it is skipped.
     */
    private fun related(root: JsonObject, base: String): Related {
        fun resource(obj: JsonObject): HeartEmbedded? {
            val self = linkObject(obj, "self")?.let { link(it, base) } ?: return null
            return HeartEmbedded(self.href, self.title, stateOf(obj), related(obj, base))
        }
        val embedded = root[EMBEDDED]?.jsonObject.orEmpty().mapValues { (_, value) ->
            when (value) {
                is JsonArray -> value.mapNotNull { (it as? JsonObject)?.let(::resource) }
                is JsonObject -> listOfNotNull(resource(value))
                else -> emptyList()
            }
        }
        return Related(links(root, base), embedded)
    }

    /**
     * The manifest a document carries: its `_forms`, its explanation link
     * (SPEC §6.12) and its CURIEs. Layer 1 at L0; the affordance set at L1, L2.
     */
    fun manifest(root: JsonObject, base: String): Manifest =
        Manifest(forms(root, base), linkObject(root, UNAVAILABLE_REL)?.let { link(it, base) }, Curies.of(root), base)

    /** Every link but `curies`, by the rel the server wrote. A rel with one link or an array of them. */
    private fun links(root: JsonObject, base: String): Map<String, List<HeartLink>> =
        root[LINKS]?.jsonObject.orEmpty().filterKeys { it != "curies" }.mapValues { (_, value) ->
            when (value) {
                is JsonArray -> value.mapNotNull { (it as? JsonObject)?.let { obj -> link(obj, base) } }
                is JsonObject -> listOfNotNull(link(value, base))
                else -> emptyList()
            }
        }

    private fun link(obj: JsonObject, base: String): HeartLink? {
        val href = obj["href"]?.jsonPrimitive?.content ?: return null
        return HeartLink(
            href = URI(base).resolve(href).toString(),
            title = obj["title"]?.jsonPrimitive?.content,
            integrity = obj["integrity"]?.jsonPrimitive?.content,
        )
    }

    /** The `_forms` of a Layer 1 body (L0) or of a Layer 2 affordance set (L1, L2). */
    fun forms(root: JsonObject, base: String): Map<String, AffordanceForm> {
        val curies = Curies.of(root)
        return root[FORMS]?.jsonObject.orEmpty().mapValues { (rel, form) ->
            form(rel, form.jsonObject, base).copy(relation = Curies.relation(rel, curies, base))
        }
    }

    /** The link object whose rel, after CURIE expansion, is [rel]. */
    private fun linkObject(root: JsonObject, rel: String): JsonObject? {
        val links = root[LINKS]?.jsonObject ?: return null
        val curies = Curies.of(root)
        return links.keys.firstOrNull { Curies.expand(it, curies) == rel }?.let { links[it] as? JsonObject }
    }

    private fun linkHref(root: JsonObject, rel: String): String? =
        root[LINKS]?.jsonObject?.get(rel)?.jsonObject?.get("href")?.jsonPrimitive?.content

    private fun form(rel: String, obj: JsonObject, base: String): AffordanceForm {
        val target = obj[LINKS]?.jsonObject?.get("target")?.jsonObject ?: error("affordance '$rel' has no target link")
        val href = target["href"]?.jsonPrimitive?.content ?: error("affordance '$rel' target has no href")
        val templated = target["templated"]?.jsonPrimitive?.booleanOrNull == true
        return AffordanceForm(
            rel = rel,
            target = if (templated) href else URI(base).resolve(href).toString(),
            templated = templated,
            method = obj["method"]?.jsonPrimitive?.content ?: error("affordance '$rel' has no method"),
            contentType = obj["contentType"]?.jsonPrimitive?.content ?: "application/json",
            schema = obj["schema"]?.jsonObject ?: error("affordance '$rel' has no schema"),
            templatePointers = (obj[TEMPLATE_POINTERS] as? JsonObject).orEmpty()
                .mapValues { (_, pointer) -> pointer.jsonPrimitive.content },
            title = obj["title"]?.jsonPrimitive?.content,
            description = obj["description"]?.jsonPrimitive?.content,
        )
    }
}
