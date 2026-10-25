package steps

import heart.HeartRepresentation
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** What heart-mcp holds in a view scenario, what it read for it, and what it showed, shared by the steps. */
class HeldView {
    lateinit var held: HeartRepresentation
    lateinit var view: JsonObject

    /** The plain JSON resources ARTIE can read, by address: where a form's choices may come from. */
    val readable = mutableMapOf<String, JsonElement>()
}
