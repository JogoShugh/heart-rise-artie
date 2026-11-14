Feature: A repair that worked is kept in the script held as data, when every value it sends is the script's own
  spec/ideas/self-repair-and-keep.md. A kept script held as data meets a form
  that changed since it was pinned. With on-change: intent, the step falls back
  to its words and the reasoner chooses from what is offered now. With keep:
  auto in the config, a repair that worked is written into the script and its
  lock file, so the next run needs no model. It is kept only where that is safe
  without a person: the run finished, the repair made the act the script was
  making, and every value it sent is one the script sent, written as the script
  wrote it, so an input stays an input. propose says what it would keep and
  writes nothing; never, the default, does neither.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has these issues on the small GitHub
      | number | title                 | state | author |
      | 7      | Login fails on Safari | open  | ada    |
    And "acme/lasers" has these issues on the small GitHub
      | number | title       | state | author |
      | 7      | Beam drifts | open  | ada    |
    And "acme/masers" has these issues on the small GitHub
      | number | title         | state | author |
      | 7      | Pump overheats | open  | ada    |
    And ARTIE talks to the GitHub gateway at "https://github.gateway.test" in front of the small GitHub
    And ARTIE holds the Bearer credential "ada-token" for "https://github.gateway.test"
    And the client works at rung L2
    And the script "close.kts" is
      """kotlin
      val owner by input()
      val repo by input()

      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to owner, "repo" to repo, "issue_number" to 7L) {
              act("github:comment", "body" to "Fixed in 1.2 for $repo.", intent = "Say it is fixed in 1.2") {}
              act("github:close", intent = "Close it")
          }
      }
      """
    And "close.kts" ran with ARTIE, captured in "run.trace.yaml", given
      | name  | value   |
      | owner | acme    |
      | repo  | rockets |
    And artie generated "close.heart.yaml" as data from "run.trace.yaml" and "close.kts"
    And the gateway's issue forms change: comment's body is renamed content

  Scenario: The repair is kept, and the next run, on another repository, needs no model
    Given the file "close.config.yaml" is
      """
      on-change: intent
      keep: auto
      """
    And ARTIE's reasoner will choose to act on "github:comment" with {"content": "Fixed in 1.2 for lasers."}
    When "close.heart.yaml" runs with ARTIE, held "lenient" to its lock file, captured in "repair.trace.yaml", given
      | name  | value  |
      | owner | acme   |
      | repo  | lasers |
    Then the run finished
    And about its kept script, the run said "kept github:comment: content (was body)"
    And "close.heart.yaml" changed only in these lines
      | was                              | now                                 |
      | body: Fixed in 1.2 for ${repo}. | content: Fixed in 1.2 for ${repo}. |
    When "close.heart.yaml" runs with ARTIE, held "lenient" to its lock file, captured in "next.trace.yaml", given
      | name  | value  |
      | owner | acme   |
      | repo  | masers |
    Then the run finished
    And issue 7 of "acme/masers" is now "closed" on the small GitHub
    And issue 7 of "acme/masers" now has these comments on the small GitHub
      | author | body                    |
      | ada    | Fixed in 1.2 for masers. |
    And ARTIE's reasoner was asked, in order
      | words                  |
      | Say it is fixed in 1.2 |

  Scenario Outline: What isn't kept without a person: <case>
    Given the file "close.config.yaml" is
      """
      on-change: intent
      keep: <keep>
      """
    And ARTIE's reasoner will choose to act on "github:comment" with <fields>
    When "close.heart.yaml" runs with ARTIE, held "lenient" to its lock file, captured in "repair.trace.yaml", given
      | name  | value  |
      | owner | acme   |
      | repo  | lasers |
    Then the run finished
    And about its kept script, the run said "<said>"
    And "close.heart.yaml" and its lock file are as artie generated them

    Examples:
      | case                              | keep    | fields                                 | said                                                                |
      | a value the script never sent     | auto    | {"content": "Done."}                   | not kept github:comment: content has a value the script never sent |
      | propose says it and keeps nothing | propose | {"content": "Fixed in 1.2 for lasers."} | would keep github:comment: content (was body)                       |
      | never, the default, does neither  | never   | {"content": "Fixed in 1.2 for lasers."} |                                                                     |
