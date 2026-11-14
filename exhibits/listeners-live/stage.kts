// The stage of a live run (spec/PLAN-LISTENERS.md, slice 9): the watch's own issue, which ends the watch when closed;
// the scan issue a stand-in veracode-bot reports on; and the triage issue whose new comments are slow to handle.
//   artie run stage.kts owner=JogoShugh
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val stamp by input(
    default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")),
)

bare {
    val api = "https://api.github.com/repos/$owner/$repo/issues"
    fun opened(title: String, body: String): String {
        val issue = call("POST", api, mapOf("title" to "$title ($stamp)", "body" to body))
        return issue.toString().let { Regex("\"number\":(\\d+)").find(it)!!.groupValues[1] }
    }
    val watch = opened("Listeners watch", "The live run's watch: closing it ends the watch.")
    val scan = opened("Veracode scan", "A stand-in veracode-bot reports its scans here.")
    val triage = opened("Triage queue", "Each new comment here is slow to handle, as a model call is.")
    println("watch=$watch scan=$scan triage=$triage")
}
