// A bench sensor stand-in, or a grower: opens an alert, an issue labelled alert, for the watch to answer.
//   artie run sensor.kts owner=JogoShugh what="Bench 2 fell to 4 °C overnight"
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")
val what by input(question = "What did it find?")
val stamp by input(
    default = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")),
)

bare {
    call(
        "POST",
        "https://api.github.com/repos/$owner/$repo/issues",
        mapOf("title" to "$what ($stamp)", "body" to "Reported by the bench sensor stand-in.", "labels" to listOf("alert")),
    )
}
