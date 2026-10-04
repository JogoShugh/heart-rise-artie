package heart

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * Reads a Layer 1 response in either dialect (SPEC §6.1). The profile must be
 * named in `Link: rel="profile"`; the old media-type parameter is not
 * accepted. Plain target hrefs are resolved against the URL the response
 * came from; templated ones are kept as templates for the act to expand.
 */
object HeartResponseParser {

    private val profileLink = Regex("""<([^>]*)>\s*;\s*rel="?profile"?""")

    fun parse(response: HeartResponse, url: String, dialect: Dialect = Dialect.HAL_SCHEMA_FORMS): HeartRepresentation {
        val profiles = profileLink.findAll(response.header("Link").orEmpty()).map { it.groupValues[1] }.toList()
        require(dialect.profile in profiles) {
            "response from $url does not name the profile ${dialect.profile} (Link profiles: $profiles)"
        }
        val root = Json.parseToJsonElement(response.body).jsonObject
        // An act's response comes from the action URL; its self link says which resource it is.
        val self = linkHref(root, "self")
        return HeartRepresentation(
            url = self?.let { URI(url).resolve(it).toString() } ?: url,
            state = JsonObject(root.filterKeys { it !in setOf(FORMS, LINKS, HalFormsTemplates.TEMPLATES) }),
            affordances = if (dialect == Dialect.HAL_FORMS) HalFormsTemplates.forms(root, url) else forms(root, url),
            etag = response.header("ETag"),
            formsLink = linkHref(root, FORMS_REL)?.let { URI(url).resolve(it).toString() },
        )
    }

    /** The `_forms` of a Layer 1 body (L0) or of a Layer 2 affordance set (L1, L2). */
    fun forms(root: JsonObject, base: String): Map<String, AffordanceForm> =
        root[FORMS]?.jsonObject.orEmpty().mapValues { (rel, form) -> form(rel, form.jsonObject, base) }

    private fun linkHref(root: JsonObject, rel: String): String? =
        root[LINKS]?.jsonObject?.get(rel)?.jsonObject?.get("href")?.jsonPrimitive?.content

    /** The `violations` list of a 422 body, or its `error` text, or nothing. */
    fun violations(response: HeartResponse): List<String> {
        val body = runCatching { Json.parseToJsonElement(response.body).jsonObject }.getOrNull() ?: return emptyList()
        val listed = (body["violations"] as? JsonArray)?.map { it.jsonPrimitive.content }
        return listed ?: listOfNotNull(body["error"]?.jsonPrimitive?.content)
    }

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
        )
    }
}
