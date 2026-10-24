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
