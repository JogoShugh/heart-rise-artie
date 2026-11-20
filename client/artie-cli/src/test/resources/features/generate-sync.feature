Feature: A script made from a run reads a resource it just made again before acting in it
  spec/PLAN-SCRIPT-GENERATOR.md. An act that makes a resource answers with it,
  and its validator. A service may change a resource it has just made on its
  own (GitHub works out whether a new pull request can be merged a moment
  after it opens), so an act made at once on what the 201 said is stale:
  live, the edit of a new pull request was refused, 412. A run with a
  reasoner read it again before each act; the script made from it does too,
  once, at the top of the block of an act that made a resource, when that
  block acts. A block that only reads, or that a read opened, needs none.

  Background:
    Given the file "run.trace.yaml" is
      """
      ---
      run:
        inputs: {}
      ---
      mode: script
      say: enter {entry}
      line: 1
      params:
        entry: http://localhost:8392/
      opens: true
      outcome: done
      ---
      mode: script
      say: act {relation}
      line: 2
      within: 1
      params:
        relation: open-pull-request
        title: Fix it
      opens: true
      outcome: new resource
      ---
      mode: script
      say: act {relation}
      line: 3
      within: 2
      params:
        relation: edit
        body: Better words.
      outcome: done
      ---
      mode: script
      say: act {relation}
      line: 4
      within: 1
      params:
        relation: issues-get
        issue_number: 1
      opens: true
      outcome: done
      ---
      mode: script
      say: act {relation}
      line: 5
      within: 4
      params:
        relation: close
      outcome: done
      """

  Scenario: The block of an act that made a resource begins with a read of it; a read's block doesn't
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.kts        |
    Then artie exits 0
    And the file "gen.kts" has these lines, in order
      | line                                        |
      | act("open-pull-request", "title" to "Fix it") { |
      | sync()                                      |
      | act("edit", "body" to "Better words.")      |
      | act("issues-get", "issue_number" to 1) {    |
      | act("close")                                |
    And the file "gen.kts" has 1 line that reads "sync()"
