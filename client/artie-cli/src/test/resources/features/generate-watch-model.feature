Feature: artie generate says when a goal run's watch asks the model
  A watch whose Otherwise is said in words asks the model each time it gives
  up, with the server's reason. The script made from that run is not free of
  the model, though every step before the watch is: its header says which
  act calls the model and how often, and its run line takes a reasoner.

  Scenario: A reasoned Otherwise in the watch is counted, and the run needs a reasoner
    Given the file "watch.trace.yaml" is
      """
      ---
      run:
        inputs:
          owner:
            value: acme
            given: given
            question: Whose repository?
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
                act("github:pulls-list", "owner" to owner, intent = "a pull request changes") {
                    whenever {
                        once("github:merge", giveUp = 3.minutes, otherwise = { why ->
                            act(intent = "say why it was not merged: ${why}")
                        }) {
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
    Then artie exits 0
    And the file "watch.kts" has these lines
      | line                                                                                                   |
      | // Every selection as that run made it, nested as it nested; an act it left to the model stays in words. |
      | // 1 act in the watch calls the model each time one is reached: artie run needs --reasoner.            |
      | //   artie run watch.kts owner=acme --reasoner claude                                                   |
