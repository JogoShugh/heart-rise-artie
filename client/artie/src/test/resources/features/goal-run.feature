Feature: A goal in plain words, planned move by move, becomes a script with no model
  spec/PLAN-PLAIN-TEXT-RUN.md. A goal file names where to start, the inputs,
  and the goal in words. A planner, given the goal sentence by sentence and
  what is held, makes one move at a time: an act described in words, opening
  a block or not; back to a resource visited; a wait until a relation is
  offered; a check of the state; a sentence done; finished. Each act is a
  reason step: ARTIE's reasoner chooses the form and values, as for
  act(intent = …). The run's capture is a reasoned run's, nested as the
  planner nested it, so artie generate writes the script from it. The gateway
  and a small GitHub run in process; the planner and the reasoner are
  scripted, so no model is called.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has these issues on the small GitHub
      | number | title                 | state | author |
      | 10     | Login fails on Safari | open  | ada    |
      | 11     | Crash on start        | open  | ada    |
    And ARTIE talks to the GitHub gateway at "https://github.gateway.test" in front of the small GitHub
    And ARTIE holds the Bearer credential "ada-token" for "https://github.gateway.test"
    And the client works at rung L2
    And the file "close.goal.yaml" is
      """
      start: https://github.gateway.test/
      inputs:
        owner: acme
        repo: rockets
        number:
          question: Which issue?
      goal: |
        Open issue $number of $owner/$repo. Comment "Fixed in 1.2, see #$number." on it, then close it.
      """

  Scenario: The goal is done move by move, and the script generated from it does it again with no model
    A comment is a resource of its own, so the act that makes it leads there;
    the planner goes back to the issue to close it.
    Given the planner will move
      | move   | words                                       | sentence | opens | to        |
      | act    | open issue $number of $owner/$repo          | 1        | yes   |           |
      | done   |                                             | 1        |       |           |
      | act    | comment on it: Fixed in 1.2, see #$number.  | 2        | yes   |           |
      | back   |                                             |          |       | the issue |
      | act    | close it                                    | 2        |       |           |
      | done   |                                             | 2        |       |           |
      | finish |                                             |          |       |           |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                 |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:comment    | {"body": "Fixed in 1.2, see #10."}                     |
      | github:close      | {}                                                     |
    When the goal "close.goal.yaml" runs with ARTIE, captured in "goal.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    And issue 10 of "acme/rockets" is now "closed" on the small GitHub
    And the planner was shown these sentences
      | sentence                                                 |
      | Open issue 10 of acme/rockets.                           |
      | Comment "Fixed in 1.2, see #10." on it, then close it.   |
    When artie generated "goal-gen.kts" from "goal.trace.yaml" and "close.goal.yaml"
    Then the script "goal-gen.kts" nests its acts as
      | relation          | depth |
      | github:issues-get | 1     |
      | github:comment    | 2     |
      | github:close      | 2     |
    When "goal-gen.kts" runs with ARTIE, captured in "gen.trace.yaml", given
      | name   | value   |
      | owner  | acme    |
      | repo   | rockets |
      | number | 11      |
    Then the run captured in "gen.trace.yaml" made the selections the run captured in "goal.trace.yaml" made, with these values
      | was | now |
      | 10  | 11  |
    And issue 11 of "acme/rockets" is now "closed" on the small GitHub

  Scenario: Back to a resource visited closes the blocks opened since, and a wait and a check are steps of their own
    Given the planner will move
      | move   | words                              | sentence | opens | to                | relation     | facts                |
      | act    | open issue $number of $owner/$repo | 1        | yes   |                   |              |                      |
      | act    | comment on it: Fixed.              | 2        | yes   |                   |              |                      |
      | back   |                                    |          |       | the issue         |              |                      |
      | wait   |                                    |          |       |                   | github:close |                      |
      | act    | close it                           | 2        |       |                   |              |                      |
      | check  |                                    |          |       |                   |              | {"state": "closed"}  |
      | done   |                                    | 1        |       |                   |              |                      |
      | done   |                                    | 2        |       |                   |              |                      |
      | finish |                                    |          |       |                   |              |                      |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                 |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:comment    | {"body": "Fixed."}                                     |
      | github:close      | {}                                                     |
    When the goal "close.goal.yaml" runs with ARTIE, captured in "goal.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    When artie generated "goal-gen.kts" from "goal.trace.yaml" and "close.goal.yaml"
    Then the script "goal-gen.kts" nests its acts as
      | relation          | depth |
      | github:issues-get | 1     |
      | github:comment    | 2     |
      | github:close      | 2     |
    And the script "goal-gen.kts" has a line with "until"
    And the script "goal-gen.kts" has a line with "check("

  Scenario Outline: A goal run stops, before its harm is done, when <case>
    Given the planner will move
      | move   | words                              | sentence | opens | question     |
      | act    | open issue $number of $owner/$repo | 1        | yes   |              |
      | <last> |                                    |          |       | <question>   |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                 |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
    When the goal "close.goal.yaml" runs with ARTIE, captured in "goal.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the goal run stopped, saying "<said>"
    And issue 10 of "acme/rockets" is now "open" on the small GitHub

    Examples:
      | case                                | last    | question            | said                                         |
      | it finishes with sentences not done | finish  |                     | not every sentence is done: 1, 2             |
      | the planner asks, and no one is there | clarify | Which comment text? | the planner asks: Which comment text?        |
      | it moves more than it may           | loop    |                     | more than 40 moves                           |

  Scenario Outline: A goal file that isn't one stops the run before its first request: <case>
    Given the file "bad.goal.yaml" is
      """
      <text>
      """
    And the planner will move
      | move   | words | sentence | opens |
      | finish |       |          |       |
    When the goal "bad.goal.yaml" runs with ARTIE, captured in "bad.trace.yaml", given
      | name | value |
    Then the goal run stopped, saying "<said>"

    Examples:
      | case                 | text                                                    | said                                              |
      | an unknown key       | start: https://github.gateway.test/\ngoal: x\nrung: L2 | bad.goal.yaml: no key rung                        |
      | no start             | goal: x                                                 | bad.goal.yaml: start names the bookmark           |
      | no goal              | start: https://github.gateway.test/                     | bad.goal.yaml: goal says what to do               |
