package heart.script

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.Instant

/**
 * What a watch did, kept as it goes (spec/PLAN-LISTENERS.md, slice 5): an append-only YAML list, one item a line,
 * each a flow mapping: a member seen as it was answered, a wait begun, a wait over, a resource left. Run again with
 * it, a watch answers no member it answered unchanged, and each wait resumes with the time it had left. Every
 * listener of a watch adds to the same journal.
 */
internal class Journal(val file: Path) {
    /** One thing the watch did, of [kind], with its [values], added at the end. */
    @Synchronized
    fun add(kind: String, vararg values: Pair<String, String>) {
        val item = mapOf("kind" to kind) + values
        Files.writeString(file, "- ${JsonObject(item.mapValues { JsonPrimitive(it.value) })}\n", CREATE, APPEND)
    }

    /** What the journal kept, done again on [watching], in order. */
    fun replay(watching: Watching) {
        if (!Files.exists(file)) return
        Files.readAllLines(file).filter { it.startsWith("- {") }.forEach { line ->
            val item = Json.parseToJsonElement(line.removePrefix("- ")).jsonObject
                .mapValues { it.value.jsonPrimitive.content }
            val key = item["key"].orEmpty()
            when (item["kind"]) {
                "seen" -> watching.seen[item.getValue("address")] = item.getValue("state")
                "wait" -> watching.waiting[key] = Instant.parse(item.getValue("since"))
                "over" -> watching.waiting.remove(key).also { watching.finished += key }
                "left" -> watching.left += item.getValue("address")
            }
        }
    }

    companion object {
        /** A short digest of [text]: the first 16 hex digits of its SHA-256. */
        fun digest(text: String): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }.take(DIGEST)

        private const val DIGEST = 16
    }
}
