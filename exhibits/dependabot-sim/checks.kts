// The required check and the rival for the Dependabot stand-in (setup.kts first). The token must be allowed to write
// workflows and commit statuses; a fine-grained token without them is refused with 403.
//   - deps-check: an Actions check on every pull request into deps-main; it fails when deps.txt says "broken".
//   - deps-auto-merge: the rival, GitHub's own way: on the label dependabot-actions, gh pr merge --auto.
//   - deps-main is protected: deps-check must pass, for admins too, so nothing merges before it does.
//   artie run checks.kts owner=JogoShugh
val owner by input(question = "Whose repository?")
val repo by input(default = "heart-github-test", question = "Which repository?")

val api = "https://api.github.com/repos/$owner/$repo"

val check = """
    name: deps-check
    on:
      pull_request:
        branches: [deps-main]
    jobs:
      deps-check:
        runs-on: ubuntu-latest
        steps:
          - uses: actions/checkout@v4
          - run: sleep 20
          - name: The bumped version must not be broken
            run: "! grep -q broken deps.txt"
""".trimIndent() + "\n"

val rival = """
    name: deps-auto-merge
    on:
      pull_request:
        types: [labeled]
        branches: [deps-main]
    permissions:
      contents: write
      pull-requests: write
    jobs:
      auto-merge:
        if: github.event.label.name == 'dependabot-actions'
        runs-on: ubuntu-latest
        steps:
          - run: gh pr merge --auto --squash "${'$'}PR" --repo "${'$'}GITHUB_REPOSITORY"
            env:
              GH_TOKEN: ${'$'}{{ github.token }}
              PR: ${'$'}{{ github.event.pull_request.html_url }}
""".trimIndent() + "\n"

bare {
    // Each file as it should be: written over when it is there, with the sha GitHub asks for.
    fun put(path: String, content: String, message: String) {
        val now = runCatching { call("GET", "$api/contents/$path?ref=deps-main") }.getOrNull()
        val body = mapOf("message" to message, "content" to b64(content), "branch" to "deps-main") +
            (now?.let { mapOf("sha" to it["sha"].text) } ?: emptyMap())
        call("PUT", "$api/contents/$path", body)
    }
    put(".github/workflows/deps-check.yml", check, "The check every bump into deps-main must pass")
    put(".github/workflows/deps-auto-merge.yml", rival, "GitHub's own auto-merge, on the label dependabot-actions")
    // gh pr merge --auto needs the repository to allow it.
    call("PATCH", api, mapOf("allow_auto_merge" to true))
    call("PUT", "$api/branches/deps-main/protection", mapOf(
        "required_status_checks" to mapOf("strict" to false, "contexts" to listOf("deps-check")),
        "enforce_admins" to true,
        "required_pull_request_reviews" to null,
        "restrictions" to null,
    ))
}
