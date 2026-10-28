Feature: artie generate --intents writes a goal run's watch in words too
  A goal run that watched keeps its watch as the script its decisions amount
  to, each act with the goal's words beside it. With --intents, the watch's
  acts are written as those words alone, as the steps before it are: the
  watch's shape stays code (until, whenever, its facts, leave where, once with
  give up and otherwise), and the reasoner chooses each act on every run.
  What an act waits for (once) stays named, as the fact it is.

  Scenario: The watch's acts as their words, its shape kept
    Given the file "watch.trace.yaml" is
      """
      ---
      run:
        inputs:
          owner:
            value: acme
            given: given
            question: Whose repository?
          label:
            value: deps
            given: default
            default: deps
            question: Which label marks a bump?
        source: |-
          act(intent = "open an issue titled \"Merge watch\"")
        marks:
          '1':
            as: watch
            intent: Open an issue titled "Merge watch"
        watch:
          from: 3
          script: |-
            watch(until = { at("watch") { "state" eq "closed" } }) {
                act("github:pulls-list", "owner" to owner, "labels" to label, inferred = "list pull requests of ${owner} labelled ${label}", fromIntent = "a pull request labelled ${label} changes") {
                    whenever {
                        has { "github:labels" has label }
                        once("github:merge", giveUp = 3.minutes) {
                            act("github:merge", "merge_method" to "squash", intent = "merge it by squashing.") {}
                        }
                    }
                }
            }
      ---
      mode: script
      say: enter {entry}
      params:
        entry: https://localhost:8392/
      outcome: done
      ---
      mode: intent
      say: open an issue titled "Merge watch"
      line: 1
      opens: true
      params:
        relation: github:open-issue
      outcome: new resource
      """
    When artie is run with
      | argument         |
      | generate         |
      | watch.trace.yaml |
      | --out            |
      | watch.kts        |
      | --intents        |
    Then artie exits 0
    And the file "watch.kts" has these lines
      | line                                                                  |
      | act(intent = """Open an issue titled "Merge watch"""") {              |
      | watch(until = { at("watch") { "state" eq "closed" } }) {               |
      | act(inferred = "list pull requests of ${owner} labelled ${label}", fromIntent = "a pull request labelled ${label} changes") { |
      | has { "github:labels" has label }                                     |
      | once("github:merge", giveUp = 3.minutes) {                            |
      | act(intent = "merge it by squashing.") {}                             |
