// How long the watch took to react to each event of a live run (spec/PLAN-LISTENERS.md, slice 9), by GitHub's own
// times: from the event (the bot's comment, the alert opened) to the watch's answer (the label, its comment). Reads
// the events play.kts wrote, and GitHub, and writes them as YAML to out.
//   artie run reactions.kts owner=JogoShugh play=runs/play-l1.yaml out=runs/reactions-l1.yaml
import java.time.Duration
import java.time.Instant

val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val play by input(question = "The events play.kts wrote?")
val out by input(question = "Where to write the reactions?")

/** One event as play.kts wrote it: its second, what, the issue, and when GitHub made it. */
data class Event(val second: Int, val what: String, val issue: String, val at: Instant)

val events = java.io.File(play).readLines().filter { it.startsWith("- {") }.map { line ->
    fun field(name: String) = Regex("\\b$name: \"?([^\",}]*)").find(line)!!.groupValues[1]
    Event(field("second").toInt(), field("what"), field("issue"), Instant.parse(field("at")))
}

bare {
    val api = "https://api.github.com/repos/$owner/$repo/issues"
    /** Each comment on [issue] that says [words], when it was made. */
    fun said(issue: String, words: String): List<Instant> =
        (call("GET", "$api/$issue/comments?per_page=100") as kotlinx.serialization.json.JsonArray)
            .map { (it as kotlinx.serialization.json.JsonObject) }
            .filter { it["body"].toString().contains(words) }
            .map { Instant.parse(it["created_at"].toString().trim('"')) }
    // The label and its comment are made together; the comment says when.
    val held = said(events.first { it.what.startsWith("scan") }.issue, "Held for security")
    val lines = events.filter { it.what != "triage" }.map { event ->
        val answers = when (event.what) {
            "scan failed" -> held
            "alert" -> said(event.issue, "Seen by the watch")
            else -> emptyList()
        }
        val answer = answers.firstOrNull { !it.isBefore(event.at) }
        val took = answer?.let { Duration.between(event.at, it).seconds }
        "- {second: ${event.second}, what: \"${event.what}\", issue: ${event.issue}, at: \"${event.at}\", " +
            "answered: ${answer?.let { "\"$it\"" } ?: "null"}, seconds: ${took ?: "null"}, " +
            "answers: ${answers.size}}"
    }
    java.io.File(out).writeText("reactions:\n" + lines.joinToString("\n") + "\n")
    lines.forEach(::println)
}
