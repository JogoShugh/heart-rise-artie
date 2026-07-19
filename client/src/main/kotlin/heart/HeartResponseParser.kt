package heart

import kotlinx.serialization.json.*

object HeartResponseParser {

    private const val HAL_SCHEMA_FORMS = "https://github.com/jbadeau/hal-schema-forms"
    private const val HEART_RISE = "https://github.com/jogoshugh/heart-rise"

    fun parse(body: String, contentType: String): HeartRepresentation {
        require(contentType.contains(HAL_SCHEMA_FORMS)) { "Content-Type missing hal-schema-forms profile" }
        require(contentType.contains(HEART_RISE)) { "Content-Type missing heart-rise profile" }

        val root = Json.parseToJsonElement(body).jsonObject
        val forms = root[FORMS]?.jsonObject?.entries?.associate { (rel, el) ->
            val obj = el.jsonObject
            rel to AffordanceForm(
                rel = rel,
                href = obj[HREF]?.jsonPrimitive?.content
                    ?: error("Affordance '$rel' missing '$HREF'"),
                method = obj[METHOD]?.jsonPrimitive?.content
                    ?: error("Affordance '$rel' missing '$METHOD'"),
                hash = obj[HASH]?.jsonPrimitive?.content
                    ?: error("Affordance '$rel' missing '$HASH'"),
                schema = obj[SCHEMA]?.jsonObject
                    ?: error("Affordance '$rel' missing '$SCHEMA'")
            )
        } ?: emptyMap()

        return HeartRepresentation(
            state = JsonObject(root.filterKeys { it != FORMS }),
            forms = forms
        )
    }
}
