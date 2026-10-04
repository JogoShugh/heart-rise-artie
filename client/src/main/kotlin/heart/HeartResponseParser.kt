package heart

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import java.net.URI

/**
 * Reads a hal-schema-forms Layer 1 response (SPEC §6.1). The profile must be
 * named in `Link: rel="profile"`; the old media-type parameter is not
 * accepted. Plain target hrefs are resolved against the URL the response
 * came from; templated ones are kept as templates for the act to expand.
 */
object HeartResponseParser {

    private val profileLink = Regex("""<([^>]*)>\s*;\s*rel="?profile"?""")

    fun parse(response: HeartResponse, url: String): HeartRepresentation {
        val profiles = profileLink.findAll(response.header("Link").orEmpty()).map { it.groupValues[1] }.toList()
        require(HAL_SCHEMA_FORMS_PROFILE in profiles) {
            "response from $url does not name the profile $HAL_SCHEMA_FORMS_PROFILE (Link profiles: $profiles)"
        }
        val root = Json.parseToJsonElement(response.body).jsonObject
        val forms = root[FORMS]?.jsonObject.orEmpty().mapValues { (rel, form) -> form(rel, form.jsonObject, url) }
        return HeartRepresentation(
            url = url,
            state = JsonObject(root.filterKeys { it != FORMS && it != LINKS }),
            affordances = forms,
            etag = response.header("ETag"),
        )
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
