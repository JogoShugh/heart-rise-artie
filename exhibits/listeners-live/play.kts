// What happens while a live run watches (spec/PLAN-LISTENERS.md, slice 9), at set times from the start: a stand-in
// veracode-bot reports failed and passed scans, alerts open, someone asks for triage again and again; then the
// watch's issue is closed, which ends the watch. Each event is written to out, with the time GitHub gave it.
//   artie run play.kts owner=JogoShugh watch=801 scan=802 triage=803 out=runs/play-listeners.yaml
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val watch by input(question = "The watch's issue?")
val scan by input(question = "The scan issue?")
val triage by input(question = "The triage issue?")
val out by input(question = "Where to write what happened?")
val stamp by input(
    default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmmss")),
)

/** Each event: its second from the start, what it is, and the issue it is on ("" for a new alert). */
val timeline = listOf(
    Triple(10, "triage", triage),
    Triple(20, "scan failed", scan),
    Triple(40, "triage", triage),
    Triple(50, "alert", ""),
    Triple(70, "triage", triage),
    Triple(80, "scan passed", scan),
    Triple(100, "triage", triage),
    Triple(110, "alert", ""),
    Triple(130, "triage", triage),
    Triple(140, "scan failed", scan),
    Triple(160, "triage", triage),
)

bare {
    val api = "https://api.github.com/repos/$owner/$repo/issues"
    val began = System.currentTimeMillis()
    val events = mutableListOf<String>()
    fun field(json: Any?, name: String) = Regex("\"$name\":\"?([^\",}]*)").find(json.toString())!!.groupValues[1]
    timeline.forEach { (second, what, on) ->
        Thread.sleep((began + second * 1000L - System.currentTimeMillis()).coerceAtLeast(0))
        val made = when (what) {
            "scan failed" -> call("POST", "$api/$on/comments", mapOf("body" to "veracode-bot: Veracode scan FAILED: 2 high."))
            "scan passed" -> call("POST", "$api/$on/comments", mapOf("body" to "veracode-bot: Veracode scan passed."))
            "alert" -> call("POST", api, mapOf(
                "title" to "Bench 3 fell to 4 °C ($stamp-$second)", "body" to "Reported by the sensor stand-in.",
                "labels" to listOf("live-alert"),
            ))
            else -> call("POST", "$api/$on/comments", mapOf("body" to "Please triage this again ($second s)."))
        }
        val number = if (what == "alert") field(made, "number") else on
        events += "- {second: $second, what: \"$what\", issue: $number, id: ${field(made, "id")}, " +
            "at: \"${field(made, "created_at")}\"}"
        println("$second s: $what on #$number")
    }
    Thread.sleep((began + 200_000L - System.currentTimeMillis()).coerceAtLeast(0))
    call("PATCH", "$api/$watch", mapOf("state" to "closed"))
    java.io.File(out).writeText("events:\n" + events.joinToString("\n") + "\n")
    println("closed the watch's issue #$watch")
}
