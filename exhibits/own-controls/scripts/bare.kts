// A plain GitHub client, as one is usually written: read the issue, then write what it decided from that read.
// Through the owner's gateway, passed through; validators kept only when the arm says (VALIDATORS=true).
val gateway by input(default = "http://localhost:8098")
val validators by input(default = "false")

bare(validators = validators == "true", headers = mapOf("X-Gateway-Mode" to "pass-through")) {
    val issue = call("GET", "$gateway/repos/acme/web/issues/7")
    call("PATCH", "$gateway/repos/acme/web/issues/7", mapOf("title" to "${issue["title"].text} (triaged)"))
    call("PATCH", "$gateway/repos/acme/web/issues/7", mapOf("state" to "closed", "state_reason" to "completed"))
}
