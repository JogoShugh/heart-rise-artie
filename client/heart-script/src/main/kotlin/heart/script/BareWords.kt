package heart.script

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * A bare watch's guards and words (spec/ideas/bare-runs.md, "Rails"): what a goal's If and its reasoned line
 * become where there are no facts to read, only answers.
 */

/** Guard: the rest of this member's turn runs only where [condition] holds. */
fun Bare.has(condition: Boolean) {
    if (!condition) throw MemberLeft()
}

/**
 * Guard: where [condition] holds, the member is left, and the run notes "Left <member>: [words]" the first time it
 * leaves that member, as a goal's If says so once.
 */
fun Bare.leaveWhere(words: String, condition: Boolean) {
    if (condition) {
        val left = member ?: "the script"
        if (run.watching.leave(left)) note("Left $left: $words")
        throw MemberLeft()
    }
}

/** The words the reasoner writes as [intent] asks: the one line a goal marked reasoned. A run must allow it. */
fun Bare.write(intent: String): String {
    val writer = navigator as? Writes
    val words = if (run.reasoning) writer?.write(intent) else null
    return words ?: throw ScriptStopped(
        run.step + 1,
        "\"write\"",
        when {
            !run.reasoning -> "\"write(…) needs a reasoner, and this run has none\""
            writer == null -> "\"this client's reasoner doesn't write\""
            else -> "\"the reasoner wrote nothing\""
        },
    )
}

/** Whether this answer is a list holding [name]: a string equal to it, or an object whose `name` is it. */
infix fun JsonElement?.has(name: String): Boolean = (this as? JsonArray).orEmpty().any { item ->
    val said = (item as? JsonObject)?.get("name") ?: item
    (said as? JsonPrimitive)?.content == name
}

/** A remark, kept in the run's record. */
fun Bare.note(text: String) {
    run.notes += text
    run.onNote(text)
}
