package heart

import kotlinx.serialization.json.JsonObject

const val FORMS = "_forms"
const val LINKS = "_links"
const val PROPERTIES = "properties"
const val REQUIRED = "required"

/** SPEC §6.1: the composite profile ARTIE speaks, and the dialect's media type. */
const val HAL_SCHEMA_FORMS_PROFILE = "https://github.com/jogoshugh/heart-rise/hal-schema-forms"
const val HAL_JSON = "application/hal+json"

/** SPEC §6.1 (0.5): the rel of the Layer 1 link to the affordance set. A server
 *  may write it as a HAL CURIE ("hr:forms"); see Curies.expand. */
const val FORMS_REL = "https://github.com/jogoshugh/heart-rise/rels/forms"

/** SPEC §6.12: the rel of the link to why absent acts are absent. */
const val UNAVAILABLE_REL = "https://github.com/jogoshugh/heart-rise/rels/unavailable"

/**
 * The manifest: the acts legal right now ([forms], by key as written), the
 * link to why the others are absent (SPEC §6.12), and the CURIEs the
 * manifest document declares, with the URL they resolve against, so a
 * relation named by CURIE can be expanded. It comes from Layer 1 at L0 and
 * from the affordance set at L1 and L2.
 */
data class Manifest(
    val forms: Map<String, AffordanceForm> = emptyMap(),
    val unavailable: HeartLink? = null,
    val curies: Map<String?, String?> = emptyMap(),
    val base: String = "",
) {
    /** [name] as a relation: a CURIE expanded and resolved, anything else as written. */
    fun relationOf(name: String): String = Curies.relation(name, curies, base)
}

/**
 * One affordance from the manifest. [target] is absolute when the server
 * sent a plain href, and an RFC 6570 template when [templated] is true.
 * [templatePointers] maps template variables to JSON Pointers into the
 * owning resource's state (HAL-SCHEMA-FORMS-HR Rule 1).
 */
data class AffordanceForm(
    val rel: String,
    val target: String,
    val templated: Boolean,
    val method: String,
    val contentType: String,
    val schema: JsonObject,
    val templatePointers: Map<String, String> = emptyMap(),
    /** The form's key as a link relation: a CURIE expanded and resolved, a URI as is, a plain name unchanged. */
    val relation: String = rel,
    val title: String? = null,
    val description: String? = null,
)
