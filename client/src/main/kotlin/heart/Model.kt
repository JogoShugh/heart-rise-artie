package heart

import kotlinx.serialization.json.JsonObject

const val FORMS = "_forms"
const val LINKS = "_links"
const val PROPERTIES = "properties"
const val REQUIRED = "required"

/** SPEC §6.1: the composite profile ARTIE speaks, and the dialect's media type. */
const val HAL_SCHEMA_FORMS_PROFILE = "https://github.com/jogoshugh/heart-rise/hal-schema-forms"
const val HAL_JSON = "application/hal+json"

/**
 * One affordance from the manifest. [target] is absolute when the server
 * sent a plain href, and an RFC 6570 template when [templated] is true.
 */
data class AffordanceForm(
    val rel: String,
    val target: String,
    val templated: Boolean,
    val method: String,
    val contentType: String,
    val schema: JsonObject,
)
