// A person, by the account the run is given, moving a pull request the watch (watch.kts) is looking after:
// add=hold objects to it, remove=hold takes the objection back;
// close=yes closes it (closing the watch's own issue ends the watch).
//   artie run person.kts owner=JogoShugh number=681 add=hold
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val number by input(question = "Which pull request?")
val add by input(default = "", question = "Which label to add?")
val remove by input(default = "", question = "Which label to remove?")
val close by input(default = "no", question = "Close it?")

val api = "https://api.github.com/repos/$owner/$repo/issues/$number/labels"

bare {
    if (add.isNotEmpty()) call("POST", api, mapOf("labels" to listOf(add)))
    if (remove.isNotEmpty()) call("DELETE", "$api/$remove")
    if (close == "yes") call("PATCH", api.removeSuffix("/labels"), mapOf("state" to "closed"))
}
