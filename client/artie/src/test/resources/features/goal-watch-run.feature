Feature: A goal that keeps watching runs: each step decided once, then replayed with no model
  spec/PLAN-WATCH-GOALS.md, slice 4a. A watch goal runs as any goal in steps
  until its Whenever. From there each step is decided by the planner, and an
  act's form and values by the reasoner, the first time the run needs it,
  live, on a real resource; every later time, for every other resource and
  every later look, the decision is replayed with no model. The Whenever's
  list is reached as the planner reached it; which members match is the
  check the planner made at the first; a Once waits for the act the planner
  named; Otherwise is decided the first time a wait gives up; the Until is
  the check the planner made on the resource it names. A value the reasoner
  chose is read again, where it came from, for each resource. The gateway
  and a small GitHub run in process; the planner and the reasoner are
  scripted, and count as the model.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has these issues on the small GitHub
      | number | title            | state | author |
      | 10     | Auto-merge watch | open  | ada    |
    And "acme/rockets" has pull request 1 on the small GitHub, open, titled "Bump left-pad to 1.0.1"
    And "acme/rockets" has pull request 2 on the small GitHub, open, titled "Bump left-pad to 1.0.2"
    And "acme/rockets" has pull request 3 on the small GitHub, open, titled "Bump left-pad to 1.0.3"
    And "acme/rockets" has pull request 4 on the small GitHub, open, titled "Refuse a bed with no name"
    And pull request 1 is labelled "dependabot" on the small GitHub
    And pull request 2 is labelled "dependabot" on the small GitHub
    And pull request 3 is labelled "dependabot" on the small GitHub
    And pull request 1 is a draft on the small GitHub
    And issue 10 of "acme/rockets" is closed on the small GitHub once pull request 1 is closed
    And ARTIE talks to the GitHub gateway at "https://github.gateway.test" in front of the small GitHub
    And ARTIE holds the Bearer credential "ada-token" for "https://github.gateway.test"
    And the client works at rung L2
    And the run's clock moves only when the run pauses
    And the file "merge.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        label: dependabot
      ---

      Merge the pull requests labelled {label}.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever a pull request labelled {label} changes `as pr`
        - Once it can be merged, merge it by squashing.
          Or give up after 15 seconds.
          Otherwise, close it.
      - Until {watch} is closed
      """

  Scenario: Each step is decided once, the first time it is needed, and replayed for every other pull request
    Pull request 4 is the first member read, so the match is decided there,
    and it doesn't match. Pull request 3 decides the wait, and, being ready,
    the merge; pull request 2 is merged by the replay, with no model.
    Pull request 1 is a draft: its wait gives up at the second look, which
    decides Otherwise. Closing it closes the watch's issue, and the Until,
    decided after the first look, ends the watch.
    Given the planner will move
      | move  | words                                        | opens | relation     | facts                             |
      | act   | open issue 10 of acme/rockets                | yes   |              |                                   |
      | done  |                                              |       |              |                                   |
      | act   | list the open pull requests of acme/rockets  | yes   |              |                                   |
      | check |                                              |       |              | {"github:labels": ["dependabot"]} |
      | wait  |                                              |       | github:merge |                                   |
      | act   | merge it by squashing                        |       |              |                                   |
      | done  |                                              |       |              |                                   |
      | check |                                              |       |              | {"state": "closed"}               |
      | act   | close it                                     |       |              |                                   |
      | done  |                                              |       |              |                                   |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:close      | {}                                                       |
    When the goal "merge.goal.md" runs with ARTIE, captured in "merge.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 3      |
      | 2      |
    And pull request 1 of "acme/rockets" is closed and not merged on the small GitHub
    And pull request 4 of "acme/rockets" is open on the small GitHub
    And the planner was asked 10 times
    And ARTIE's reasoner was asked 4 times
    And at move 3 the planner was told the step now is "whenever: a pull request labelled dependabot changes: from here, reach the list to watch, by a form or a link, or go back to the start if it isn't reached from here; when a form can narrow the list to what the Whenever says (a filter), narrow it, so each look reads only those; it is read again at every look"

  Scenario: If is checked as decided once; a pull request it holds for is left alone, and said so once
    Pull request 2 is labelled hold too. The If's check is decided at the
    first member that matches, pull request 3, where it doesn't hold; at
    pull request 2 it holds, so its other steps are left, and a note says
    so. The planner is shown where it is among the steps, inside the
    Whenever.
    Given pull request 2 is labelled "hold" on the small GitHub
    And the file "hold.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        label: dependabot
      ---

      Merge the pull requests labelled {label}, unless someone holds them.

      - Open issue 10 of acme/rockets `as watch`
      - Whenever a pull request labelled {label} changes `as pr`
        - If it's labelled hold, leave it, and say so once.
        - Once it can be merged, merge it by squashing.
          Or give up after 15 seconds.
          Otherwise, close it.
      - Until {watch} is closed
      """
    And the planner will move
      | move  | words                                        | opens | relation     | facts                             |
      | act   | open issue 10 of acme/rockets                | yes   |              |                                   |
      | done  |                                              |       |              |                                   |
      | act   | list the open pull requests of acme/rockets  | yes   |              |                                   |
      | check |                                              |       |              | {"github:labels": ["dependabot"]} |
      | check |                                              |       |              | {"github:labels": ["hold"]}       |
      | wait  |                                              |       | github:merge |                                   |
      | act   | merge it by squashing                        |       |              |                                   |
      | done  |                                              |       |              |                                   |
      | check |                                              |       |              | {"state": "closed"}               |
      | act   | close it                                     |       |              |                                   |
      | done  |                                              |       |              |                                   |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:close      | {}                                                       |
    When the goal "hold.goal.md" runs with ARTIE, captured in "hold.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 3      |
    And pull request 2 of "acme/rockets" is open on the small GitHub
    And the run noted one note beginning "Left https://github.gateway.test/repos/acme/rockets/pulls/2:"
    And the planner was asked 11 times
    And at move 5 the planner was shown the steps
      | line                                                             |
      | [done] Open issue 10 of acme/rockets (as watch)                  |
      | [in] Whenever a pull request labelled {label} changes (as pr)    |
      | [now] If it's labelled hold, leave it, and say so once.          |

  Scenario: A planner that stops while a watch decides ends the run, saying why, rather than looking again
    A failed look is tried again at the next; a decision the planner won't
    make would only be asked again, and again.
    Given the planner will move
      | move  | words                                        | opens | relation | facts |
      | act   | open issue 10 of acme/rockets                | yes   |          |       |
      | done  |                                              |       |          |       |
      | act   | list the open pull requests of acme/rockets  | yes   |          |       |
      | stop  | I can't tell which pull requests match       |       |          |       |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
    When the goal "merge.goal.md" runs with ARTIE, captured in "merge.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the goal run stopped, saying "I can't tell which pull requests match"
    And the planner was asked 4 times

  Scenario: A move the part being decided doesn't take is refused, and the planner told which it takes
    While the match is decided, the planner tries an act; it is told only a
    check or a stop is taken there, and makes the check.
    Given the planner will move
      | move  | words                                        | opens | relation     | facts                             |
      | act   | open issue 10 of acme/rockets                | yes   |              |                                   |
      | done  |                                              |       |              |                                   |
      | act   | list the open pull requests of acme/rockets  | yes   |              |                                   |
      | act   | see its labels                               |       |              |                                   |
      | check |                                              |       |              | {"github:labels": ["dependabot"]} |
      | wait  |                                              |       | github:merge |                                   |
      | act   | merge it by squashing                        |       |              |                                   |
      | done  |                                              |       |              |                                   |
      | check |                                              |       |              | {"state": "closed"}               |
      | act   | close it                                     |       |              |                                   |
      | done  |                                              |       |              |                                   |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:close      | {}                                                       |
    When the goal "merge.goal.md" runs with ARTIE, captured in "merge.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And the planner was told "act: refused, here only check, stop"
    And ARTIE's reasoner was asked 4 times

  Scenario: An act refused while a step is decided is told to the planner, which decides again
    The reasoner first chooses a form the entry doesn't offer, so the
    client refuses it before anything is sent; the planner is told, and its
    next act reaches the list.
    Given the planner will move
      | move  | words                                        | opens | relation     | facts                             |
      | act   | open issue 10 of acme/rockets                | yes   |              |                                   |
      | done  |                                              |       |              |                                   |
      | act   | list the open pull requests of acme/rockets  | yes   |              |                                   |
      | act   | search the pull requests of acme/rockets     | yes   |              |                                   |
      | check |                                              |       |              | {"github:labels": ["dependabot"]} |
      | wait  |                                              |       | github:merge |                                   |
      | act   | merge it by squashing                        |       |              |                                   |
      | done  |                                              |       |              |                                   |
      | check |                                              |       |              | {"state": "closed"}               |
      | act   | close it                                     |       |              |                                   |
      | done  |                                              |       |              |                                   |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:close      | {}                                                       |
    When the goal "merge.goal.md" runs with ARTIE, captured in "merge.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 3      |
      | 2      |
    And the planner was asked 11 times
    And ARTIE's reasoner was asked 5 times

  Scenario: A Whenever whose list isn't reached from where it stands goes back to the start, and watches from there
    The Whenever is under the issue, which leads to no list of pull
    requests; the planner goes back to the start, where the list's form is.
    Given line 10 of "merge.goal.md" reads '  - Whenever a pull request labelled {label} changes `as pr`', indented 0
    And line 11 of "merge.goal.md" reads '    - Once it can be merged, merge it by squashing.', indented 0
    And line 12 of "merge.goal.md" reads '      Or give up after 15 seconds.', indented 0
    And line 13 of "merge.goal.md" reads '      Otherwise, close it.', indented 0
    And the planner will move
      | move  | words                                        | opens | relation     | facts                             | to        |
      | act   | open issue 10 of acme/rockets                | yes   |              |                                   |           |
      | done  |                                              |       |              |                                   |           |
      | back  |                                              |       |              |                                   | the start |
      | act   | list the open pull requests of acme/rockets  | yes   |              |                                   |           |
      | check |                                              |       |              | {"github:labels": ["dependabot"]} |           |
      | wait  |                                              |       | github:merge |                                   |           |
      | act   | merge it by squashing                        |       |              |                                   |           |
      | done  |                                              |       |              |                                   |           |
      | check |                                              |       |              | {"state": "closed"}               |           |
      | act   | close it                                     |       |              |                                   |           |
      | done  |                                              |       |              |                                   |           |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:pulls-list | {"owner": "acme", "repo": "rockets", "state": "open"}    |
      | github:merge      | {"merge_method": "squash"}                               |
      | github:close      | {}                                                       |
    When the goal "merge.goal.md" runs with ARTIE, captured in "merge.trace.yaml", given
      | name  | value      |
      | label | dependabot |
    Then the run finished
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 3      |
      | 2      |
    And the planner was asked 11 times

  Scenario: A fact of Done when the planner can't check as facts is left unchecked, and noted
    "Every pull request was merged or says why" is no member of one
    resource: the planner says so with done, and the run notes it.
    Given the file "close.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Close the watch issue.

      ## The work

      - Open issue 10 of acme/rockets `as watch`
        - Close it

      ## Done when

      - Every pull request was merged, or says why not
      """
    And the planner will move
      | move  | words                         | opens |
      | act   | open issue 10 of acme/rockets | yes   |
      | done  |                               |       |
      | act   | close it                      |       |
      | done  |                               |       |
      | done  |                               |       |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:close      | {}                                                       |
    When the goal "close.goal.md" runs with ARTIE, captured in "close.trace.yaml", given
      | name | value |
    Then the run finished
    And the run noted one note beginning "Not checked: Every pull request was merged, or says why not"
    And the planner was last told the step now is "done when: Every pull request was merged, or says why not: check it only when members of the resource held say it; otherwise done, saying why"

  Scenario Outline: A goal in sections tells the planner its manners, and ends with the checks Done when names: <case>
    Given the file "close.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      ---

      Close the watch issue.

      ## Working alongside others

      Leave what others changed.

      - Do not reopen what someone closed.

      ## The work

      - Open issue 10 of acme/rockets `as watch`
        - Close it

      ## Done when

      - {watch} is closed
      """
    And the planner will move
      | move  | words                         | opens | relation | facts   |
      | act   | open issue 10 of acme/rockets | yes   |          |         |
      | done  |                               |       |          |         |
      | act   | close it                      |       |          |         |
      | done  |                               |       |          |         |
      | check |                               |       |          | <facts> |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:close      | {}                                                       |
    When the goal "close.goal.md" runs with ARTIE, captured in "close.trace.yaml", given
      | name | value |
    Then <ended>
    And the planner was shown the goal with "Do not reopen what someone closed."
    And the planner was last shown "watch" named, at an address ending "/issues/10"

    Examples:
      | case                         | facts               | ended                                                          |
      | the check holds              | {"state": "closed"} | the run finished                                               |
      | the check fails, and says so | {"state": "open"}   | the goal run stopped, saying "state: expected open, was closed" |
