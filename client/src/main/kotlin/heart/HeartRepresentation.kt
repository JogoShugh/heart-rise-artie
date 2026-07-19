package heart

import kotlinx.serialization.json.JsonObject

class HeartRepresentation(
    val state: JsonObject,
    private val forms: Map<String, AffordanceForm>
) {
    fun containsAffordance(rel: String): Boolean = forms.containsKey(rel)
    fun affordance(rel: String): AffordanceForm? = forms[rel]
}
