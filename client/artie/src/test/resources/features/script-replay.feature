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
      | 12     | Docs typo             | open  | ada    |
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

  Scenario Outline: When the forms change in the middle of a run, the next step meets the new ones
    The comment is made with the forms as recorded; the gateway changes
    right after it. The close, the next step, is checked against the set the
    issue offers now, not the one it offered before the comment: a changed
    close stops both modes there, and a changed set stops strict there.
    Given the gateway's issue forms change once the comment is made: <change>
    When "gen.kts" runs with ARTIE, held "<guard>" to its lock file, captured in "gen.trace.yaml", given
      | name   | value   |
      | owner  | acme    |
      | repo   | rockets |
      | number | 11      |
    Then the run <ends>
    And issue 11 of "acme/rockets" is now "<issue>" on the small GitHub
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                   |
      | ada    | Fixed in 1.2, see #11. |

    Examples:
      | change                  | guard   | ends                               | issue  |
      | close takes a milestone | lenient | was stopped at step 3 by its guard | open   |
      | close takes a milestone | strict  | was stopped at step 3 by its guard | open   |
      | edit takes no body      | lenient | finished                           | closed |
      | edit takes no body      | strict  | was stopped at step 3 by its guard | open   |

  Scenario Outline: A form used once, then changed, is refused the second time it is used
    The script comments twice. The first comment is made with the comment
    form as recorded; the gateway then changes that same form. The second
    comment is checked against the form the issue offers now, so both modes
    stop there, and only the first comment is made. Its own issues, fresh:
    one to record on, one to run on.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title             | state | author |
      | 20     | Fuel gauge sticks | open  | ada    |
      | 21     | Hatch won't seal  | open  | ada    |
    And the script "twice.kts" is
      """kotlin
      val owner by input()
      val repo by input()
      val number by input()

      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to owner, "repo" to repo, "issue_number" to number.toLong()) {
              act("github:comment", "body" to "First pass.") {}
              act("github:comment", "body" to "Second pass.") {}
              act("github:close")
          }
      }
      """
    And "twice.kts" ran with ARTIE, captured in "twice.trace.yaml", given
      | name   | value   |
      | owner  | acme    |
      | repo   | rockets |
      | number | 20      |
    And artie generated "twice-gen.kts" from "twice.trace.yaml" and "twice.kts"
    And the gateway's issue forms change once the comment is made: comment takes no body
    When "twice-gen.kts" runs with ARTIE, held "<guard>" to its lock file, captured in "twice-gen.trace.yaml", given
      | name   | value   |
      | owner  | acme    |
      | repo   | rockets |
      | number | 21      |
    Then the run was stopped at step 3 by its guard
    And issue 21 of "acme/rockets" is now "open" on the small GitHub
    And issue 21 of "acme/rockets" now has these comments on the small GitHub
      | author | body        |
      | ada    | First pass. |

    Examples:
      | guard   |
      | lenient |
      | strict  |

  Scenario Outline: A lenient run past a changed set re-pins it, as the config beside the script says
    spec/ideas/replay-guards-lenient-strict.md, the walk's step 3: the forms
    the script used kept their integrity, but the set they were offered in
    changed. With repin: auto, the lock file then records the new set beside
    the old, so a strict run holds; propose says what it would re-pin and
    writes nothing; never, the default, and --no-repin do neither.
    Given the gateway's issue forms change: edit takes no body
    And the file "gen.config.yaml" is
      """
      repin: <repin>
      """
    When "gen.kts" runs with ARTIE, held "lenient" to its lock file<flag>, captured in "gen.trace.yaml", given
      | name   | value   |
      | owner  | acme    |
      | repo   | rockets |
      | number | 11      |
    Then the run finished
    And about its pins, the run said "<said>"
    When "gen.kts" runs with ARTIE, held "strict" to its lock file, captured in "strict.trace.yaml", given
      | name   | value   |
      | owner  | acme    |
      | repo   | rockets |
      | number | 12      |
    Then the run <strict>

    Examples:
      | repin   | flag            | said                                                                                                  | strict                             |
      | auto    |                 | re-pinned github:comment: offered in a new set; re-pinned github:close: offered in a new set          | finished                           |
      | propose |                 | would re-pin github:comment: offered in a new set; would re-pin github:close: offered in a new set    | was stopped at step 2 by its guard |
      | never   |                 |                                                                                                       | was stopped at step 2 by its guard |
      | auto    | , with no-repin |                                                                                                       | was stopped at step 2 by its guard |
