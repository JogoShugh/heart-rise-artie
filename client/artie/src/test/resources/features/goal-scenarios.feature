Feature: A goal's steps marked as scenarios, captured once, kept as scenarios
  A smoke test is scenarios: each step of its goal says one thing the service must do, and is
  marked `scenario NAME`. The goal runs once, its moves the planner's; artie generate keeps each
  marked step's acts as that scenario, so the script made from the run runs every scenario, a
  failing one failing alone, and says which passed. The model reasons once; every run after is the
  script, with no model.

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
    And the file "smoke.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        number:
      ---

      Check that issues work as a person would use them.

      - Open issue {number} of acme/rockets `scenario Open`
      - Comment on issue {number} of acme/rockets `scenario Comment`
      """

  Scenario: The script made from the run keeps each marked step as its scenario, and runs them all
    Given the planner will move
      | move | words                                     | opens | relation | facts |
      | act  | open issue $number of acme/rockets        |       |          |       |
      | done |                                           |       |          |       |
      | act  | open issue $number of acme/rockets        | yes   |          |       |
      | act  | comment on it: Seen.                      |       |          |       |
      | done |                                           |       |          |       |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:comment    | {"body": "Seen."}                                        |
    When the goal "smoke.goal.md" runs with ARTIE, captured in "smoke.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    When artie generated "smoke.heart.yaml" as data from "smoke.trace.yaml" and "smoke.goal.md"
    Then the script "smoke.heart.yaml" has a line with "- scenario: Open"
    And the script "smoke.heart.yaml" has a line with "- scenario: Comment"
    When "smoke.heart.yaml" runs with ARTIE, captured in "replay.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run's scenarios were
      | scenario | passed |
      | Open     | true   |
      | Comment  | true   |
