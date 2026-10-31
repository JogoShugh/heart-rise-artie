package heart

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * What a run's model calls took, as the Claude API counts them in each answer's `usage`: the calls, and the tokens
 * read, written, read from the prompt cache and written to it. The reasoner and the planner it makes share one, so
 * one count says what the run took.
 */
class ModelUsage {
    private var calls = 0L
    private var input = 0L
    private var output = 0L
    private var cacheRead = 0L
    private var cacheWrite = 0L

    /** One call, its answer's [usage] (null when the answer gave none). */
    @Synchronized
    fun add(usage: JsonObject?) {
        calls++
        fun count(name: String) = usage?.get(name)?.jsonPrimitive?.longOrNull ?: 0L
        input += count("input_tokens")
        output += count("output_tokens")
        cacheRead += count("cache_read_input_tokens")
        cacheWrite += count("cache_creation_input_tokens")
    }

    @Synchronized
    fun counts(): Map<String, Long> = linkedMapOf(
        "calls" to calls, "input" to input, "output" to output, "cache_read" to cacheRead, "cache_write" to cacheWrite,
    )
}
