Feature: A generated script makes the same requests, holds to its lock file, and works on other resources
  spec/PLAN-SCRIPT-GENERATOR.md, "The proof". A script runs with ARTIE against
  the GitHub gateway in front of the small GitHub; artie generate makes a
  script and its lock file from that run's capture; the generated script then
  runs with ARTIE: on another issue, on another repository, and after the
  gateway's forms change, held to its lock file lenient or strict.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has these issues on the small GitHub
      | number | title                 | state | author |
      | 10     | Login fails on Safari | open  | ada    |
      | 11     | Crash on start        | open  | ada    |
    And "acme/lasers" has these issues on the small GitHub
      | number | title       | state | author |
      | 7      | Beam drifts | open  | ada    |
    And ARTIE talks to the GitHub gateway at "https://github.gateway.test" in front of the small GitHub
    And ARTIE holds the Bearer credential "ada-token" for "https://github.gateway.test"
    And the client works at rung L2
    And the script "close.kts" is
      """kotlin
      val owner by input()
      val repo by input()
      val number by input()

      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to owner, "repo" to repo, "issue_number" to number.toLong()) {
              act("github:comment", "body" to "Fixed in 1.2, see #$number.") {}
              act("github:close")
          }
      }
      """
    And "close.kts" ran with ARTIE, captured in "run.trace.yaml", given
      | name   | value   |
      | owner  | acme    |
      | repo   | rockets |
      | number | 10      |
    And artie generated "gen.kts" from "run.trace.yaml" and "close.kts"

  Scenario: The lock file is recorded at the rung the run read at, not the bookmark's
    The bookmark answers at L0 whatever the client asks; the issue was read at
    L2, its forms in an affordance set, so the lock file pins L2.
    Then the lock file "gen.pins.yaml" pins rung "L2"

  Scenario: The generated script makes the same selections, and works on another repository's issue
    When "gen.kts" runs with ARTIE, captured in "gen.trace.yaml", given
      | name   | value   |
      | owner  | acme    |
      | repo   | rockets |
      | number | 11      |
    Then the run captured in "gen.trace.yaml" made the selections the run captured in "run.trace.yaml" made, with these values
      | was | now |
      | 10  | 11  |
    When "gen.kts" runs with ARTIE, captured in "lasers.trace.yaml", given
      | name   | value  |
      | owner  | acme   |
      | repo   | lasers |
      | number | 7      |
    Then the run finished
    And issue 7 of "acme/lasers" is now "closed" on the small GitHub
    And issue 7 of "acme/lasers" now has these comments on the small GitHub
      | author | body                  |
      | ada    | Fixed in 1.2, see #7. |

  Scenario Outline: When the gateway's forms change, the generated script holds to its lock file
    Closing an issue that takes a milestone is another form, refused in
    both modes before the close is sent; an edit that takes no body is
    another form of the same set, which lenient goes past and strict refuses
    at the first form it offers, the comment.
    Given the gateway's issue forms change: <change>
    When "gen.kts" runs with ARTIE, held "<guard>" to its lock file, captured in "gen.trace.yaml", given
      | name   | value   |
      | owner  | acme    |
      | repo   | rockets |
      | number | 11      |
    Then the run <ends>
    And issue 11 of "acme/rockets" is now "<issue>" on the small GitHub

    Examples:
      | change                  | guard   | ends                               | issue  |
      | close takes a milestone | lenient | was stopped at step 3 by its guard | open   |
      | close takes a milestone | strict  | was stopped at step 2 by its guard | open   |
      | edit takes no body      | lenient | finished                           | closed |
      | edit takes no body      | strict  | was stopped at step 2 by its guard | open   |
      | nothing                 | strict  | finished                           | closed |
