package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What a step's trace keeps of the run's shape, beside what it asked and what it held
 * (spec/PLAN-LINE-BY-LINE-REPORT.md): where its values came from, a reason step's template, whether it opens a
 * block, what an until waited for, whether a reason step made a judgement, the affordance it used and its
 * guard, and what was offered where it chose.
 */
internal fun TraceStep.shape(): Map<String, JsonElement> = buildMap {
    val step = this@shape
    step.values?.takeIf { it.isNotEmpty() }?.let { put("values", it) }
    reason?.let { put("reason", it) }
    if (opens) put("opens", JsonPrimitive(true))
    waited?.let { put("waited", it) }
    // A reason step chose a value its words didn't give: the reasoner's judgement, not the words'.
    if (asked.mode == "intent") put("judgement", JsonPrimitive(Shape.chose(step.values)))
    if (model.isNotEmpty()) {
        put("model", JsonArray(model.map { call ->
            buildJsonObject {
                call.model?.let { put("model", it) }
                put("asked", call.asked)
                put("answered", call.answered)
            }
        }))
    }
    used?.let { used ->
        put("chose", chose(used))
        used.guard?.let { put("guard", guard(it)) }
    }
    offered?.let { from ->
        put("offered", buildJsonObject {
            put("forms", JsonArray(from.offered.sorted().map { JsonPrimitive(it) }))
            put("links", JsonArray(from.links.keys.sorted().map { JsonPrimitive(it) }))
        })
    }
}

/** The affordance a step used; a field the form asks for that the step left out is "not sent". */
private fun TraceStep.chose(used: Used): JsonObject = buildJsonObject {
    put("kind", used.kind)
    put("relation", used.relation)
    used.title?.let { put("title", it) }
    put("method", used.method)
    put("target", used.target)
    put("address", used.address)
    // A link with a title was picked by it, among the relation's links; a form, by its relation alone.
    put("picked", if (used.kind == "link" && used.title != null) "title" else "relation")
    val left = used.fields.filter { it !in asked.params }
    if (left.isNotEmpty()) put("not sent", JsonArray(left.map { JsonPrimitive(it) }))
}

/** What a replay of this step would check before acting. */
private fun guard(guard: Guard): JsonObject = buildJsonObject {
    guard.rung?.let { put("rung", it) }
    guard.set?.let { put("set", pinned(it)) }
    guard.form?.let { put("form", it) }
    guard.schema?.let { put("schema", pinned(it)) }
    guard.ifMatch?.let { put("if-match", it) }
}

private fun pinned(at: Pinned): JsonObject = buildJsonObject {
    put("href", at.href)
    at.integrity?.let { put("integrity", it) }
}

/** What the trace knows of a run's shape only once it is over, or across its steps. */
internal object Shape {
    private const val CHOSEN = "chosen"

    /** Whether any of a step's [values] is the reasoner's own choice: no input, no earlier state, not its words. */
    fun chose(values: JsonObject?): Boolean = values.orEmpty().values.any { value ->
        val from = (value as? JsonObject)?.get("from") as? JsonArray
        from.orEmpty().any { (it as? JsonPrimitive)?.content == CHOSEN }
    }

    /**
     * For each step of [steps] (as the trace writes them), the members of its state later steps read: by
     * pointer, the steps that read it, from their values' and slots' sources.
     */
    fun keeps(steps: List<JsonObject>): List<Map<String, List<Int>>> {
        val keeps = steps.map { linkedMapOf<String, MutableList<Int>>() }
        steps.forEachIndexed { i, step ->
            sourcesIn(step).forEach { source ->
                val from = (source["step"] as? JsonPrimitive)?.content?.toIntOrNull()
                val pointer = (source["pointer"] as? JsonPrimitive)?.content
                val readers = if (from == null || pointer == null) null else keeps.getOrNull(from - 1)
                    ?.getOrPut(pointer) { mutableListOf() }
                if (readers != null && i + 1 !in readers) readers += i + 1
            }
        }
        return keeps
    }

    /** Every step-and-pointer source in [step]'s values and reason slots, parts included. */
    private fun sourcesIn(step: JsonObject): List<JsonObject> {
        val slots = (step["reason"] as? JsonObject)?.get("slots") as? JsonObject
        val groups = listOfNotNull(step["values"] as? JsonObject, slots)
        return groups.flatMap { it.values }.flatMap { ((it as? JsonObject)?.get("from") as? JsonArray).orEmpty() }
            .flatMap { source ->
                val parts = ((source as? JsonObject)?.get("parts") as? JsonObject)?.values.orEmpty()
                listOfNotNull(source as? JsonObject) + parts.mapNotNull { it as? JsonObject }
            }
    }
}
