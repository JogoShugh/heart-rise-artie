Feature: A script's lock file guards each act before ARTIE sends it
  spec/PLAN-SCRIPT-GENERATOR.md, "Guards" (spec/ideas/replay-guards-lenient-strict.md).
  A generated script's lock file pins, by relation, each form's own integrity
  (HAL-SCHEMA-FORMS-HR Rule 10) and the affordance sets it was offered in.
  Before ARTIE sends an act, it checks the form it is about to use. A lock file
  pins every relation its script uses: here, the bookmark's forms as offered,
  and the one each scenario is about. Lenient, the default: the form must have its pinned integrity, in whatever set holds
  it now; a relation the lock doesn't pin goes on, marked unpinned. Strict: the
  set must also be one recorded for it, and an unpinned relation is refused.
  A refusal is the guard's, by the client: nothing is sent.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has these issues on the small GitHub
      | number | title                 | state | author |
      | 10     | Login fails on Safari | open  | ada    |
    And ARTIE talks to the GitHub gateway at "https://github.gateway.test" in front of the small GitHub
    And ARTIE holds the Bearer credential "ada-token" for "https://github.gateway.test"
    And the client works at rung L2

  Scenario Outline: The form a pinned act uses is checked, and with strict its set too
    Given ARTIE guards the run "<guard>", pinning "github:close" to its form "<form>" in a set "<set>"
    When this script runs with ARTIE
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10)
          act("github:close")
      }
      """
    Then the run <ends>
    And issue 10 of "acme/rockets" is now "<issue>" on the small GitHub

    Examples:
      | guard   | form       | set        | ends                          | issue  |
      | lenient | as offered | changed    | finished                      | closed |
      | lenient | changed    | as offered | was stopped at step 2 by its guard | open   |
      | strict  | as offered | changed    | was stopped at step 2 by its guard | open   |
      | strict  | as offered | as offered | finished                      | closed |

  Scenario Outline: A relation the lock file doesn't pin: lenient goes on, marked; strict refuses
    Given ARTIE guards the run "<guard>", pinning everything it offers but "github:close"
    When this script runs with ARTIE
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10)
          act("github:close")
      }
      """
    Then the run <ends>
    And issue 10 of "acme/rockets" is now "<issue>" on the small GitHub

    Examples:
      | guard   | ends                               | issue  |
      | lenient | finished, step 3 marked unpinned   | closed |
      | strict  | was stopped at step 2 by its guard | open   |
