package heart

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What a domain tells the explorer: the search to run from the bookmark and
 * the item to follow, and example bodies for its acts, by relation, in the
 * order the explorer should prefer them. Never a URI, a method or an order of
 * steps: the server decides what is offered at each step.
 */
data class ExploreSamples(
    val search: Search,
    val acts: LinkedHashMap<String, List<JsonObject>>,
    val maxSteps: Int = DEFAULT_STEPS,
) {
    /** The search form the bookmark offers, the fields to fill it with, and the title of the item to follow. */
    data class Search(val rel: String, val fields: JsonObject, val item: String?)

    companion object {
        const val DEFAULT_STEPS = 50

        /** Samples as a domain keeps them, in JSON: `search`, `acts` and an optional `maxSteps`. */
        fun parse(json: String): ExploreSamples {
            val root = Json.parseToJsonElement(json).jsonObject
            val search = root.getValue("search").jsonObject
            val acts = root.getValue("acts").jsonObject.entries.associateTo(LinkedHashMap()) { (rel, bodies) ->
                rel to bodies.jsonArray.map { it.jsonObject }
            }
            return ExploreSamples(
                Search(
                    search.getValue("rel").jsonPrimitive.content,
                    search.getValue("fields").jsonObject,
                    search["item"]?.jsonPrimitive?.content,
                ),
                acts,
                root["maxSteps"]?.jsonPrimitive?.int ?: DEFAULT_STEPS,
            )
        }
    }
}

/** What an exploration did: the acts it took, in order, and why it stopped. */
data class Exploration(val acts: List<String>, val stopped: String)

/**
 * Explores any HEART server from its bookmark (Fielding's rule 6): reads the
 * bookmark, runs the samples' search, follows the item, then repeatedly takes
 * the first offered act, in the samples' order, that still has a body left.
 * When the bookmark itself offers a sampled act (a creation), it starts
 * there instead, and goes on from the resource the act made.
 * It chooses only among what the server offers now, so the same samples
 * explore any state the server leads it through.
 */
class Explorer(private val client: HeartRiseClient) {

    fun explore(base: String, samples: ExploreSamples): Exploration {
        client.discover("$base/")
        val bookmark = client.enter("$base/")
        val left = samples.acts.mapValuesTo(LinkedHashMap()) { (_, bodies) -> ArrayDeque(bodies) }
        // A bookmark that offers a sampled act (a creation) is where the exploration starts.
        var here = if (left.keys.any { bookmark.containsAffordance(it) }) {
            bookmark
        } else {
            client.act(samples.search.rel, samples.search.fields)
            client.follow("item", samples.search.item)
        }
        val acts = mutableListOf<String>()
        while (acts.size < samples.maxSteps) {
            val rel = left.keys.firstOrNull { here.containsAffordance(it) && left.getValue(it).isNotEmpty() }
                ?: return Exploration(acts, NOTHING_LEFT)
            here = client.act(rel, left.getValue(rel).removeFirst())
            acts += rel
        }
        return Exploration(acts, STEP_LIMIT)
    }

    companion object {
        const val NOTHING_LEFT = "no offered act has a sample left"
        const val STEP_LIMIT = "the step limit"
    }
}
