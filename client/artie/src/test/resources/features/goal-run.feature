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
    And the file "close.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        owner: acme
        repo: rockets
        number:
          question: Which issue?
      ---

      Open issue {number} of {owner}/{repo}. Comment "Fixed in 1.2, see #{number}." on it, then close it.
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
    When the goal "close.goal.md" runs with ARTIE, captured in "goal.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    And issue 10 of "acme/rockets" is now "closed" on the small GitHub
    And the planner was shown these sentences
      | sentence                                                 |
      | Open issue 10 of acme/rockets.                           |
      | Comment "Fixed in 1.2, see #10." on it, then close it.   |
    When artie generated "goal-gen.kts" from "goal.trace.yaml" and "close.goal.md"
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
    When the goal "close.goal.md" runs with ARTIE, captured in "goal.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    When artie generated "goal-gen.kts" from "goal.trace.yaml" and "close.goal.md"
    Then the script "goal-gen.kts" nests its acts as
      | relation          | depth |
      | github:issues-get | 1     |
      | github:comment    | 2     |
      | github:close      | 2     |
    And the script "goal-gen.kts" has a line with "until"
    And the script "goal-gen.kts" has a line with "check("

  Scenario: A value an earlier step brought in is the generated script's, not the number the planner wrote
    In a goal run the words are the planner's, not the person's: a number
    it wrote that an earlier state holds is that state's, so the script
    reads it there and works on the next issue opened. An input with a
    default { now: PATTERN } is fresh in the generated script too.
    Given the file "open.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        stamp:
          default: { now: yyyy }
      ---

      In acme/rockets, open an issue titled "Beam drifts {stamp}". Then get that issue again and close it.
      """
    And the planner will move
      | move   | words                                    | sentence | opens | to                           |
      | act    | get the repository acme/rockets          | 1        | yes   |                              |
      | act    | open an issue titled "Beam drifts $stamp" | 1        | yes   |                              |
      | done   |                                          | 1        |       |                              |
      | back   |                                          |          |       | https://github.gateway.test/ |
      | act    | get issue 12 of acme/rockets             | 2        | yes   |                              |
      | act    | comment on it: Fixed in 2.12 for #12 in 2026. | 2   | yes   |                              |
      | back   |                                          |          |       | the issue                    |
      | act    | close it                                 | 2        |       |                              |
      | done   |                                          | 2        |       |                              |
      | finish |                                          |          |       |                              |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:repos-get  | {"owner": "acme", "repo": "rockets"}                     |
      | github:open-issue | {"title": "Beam drifts 2026"}                            |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 12} |
      | github:comment    | {"body": "Fixed in 2.12 for #12 in 2026."}               |
      | github:close      | {}                                                       |
    When the goal "open.goal.md" runs with ARTIE, captured in "open.trace.yaml", given
      | name | value |
    Then the run finished
    And issue 12 of "acme/rockets" is now "closed" on the small GitHub
    When artie generated "open-gen.kts" from "open.trace.yaml" and "open.goal.md"
    Then the script "open-gen.kts" has a line with "{ long(\"number\") }"
    And the script "open-gen.kts" has no line with "var "
    And the script "open-gen.kts" has no line with "to 12"
    And the script "open-gen.kts" has no line with "#12"
    And the script "open-gen.kts" has a line with "\"Fixed in 2.12 for #$number in $stamp.\""
    And the script "open-gen.kts" has a line with "inferred = \"comment on it: Fixed in 2.12 for #${number} in ${stamp}.\""
    And the script "open-gen.kts" has a line with "ofPattern(\"yyyy\")"
    And the run's script kept in "open.trace.yaml" has a line with 'act(intent = """open an issue titled "Beam drifts $stamp"""")'
    And the run's script kept in "open.trace.yaml" has no escaped quote
    And the script "open-gen.kts" has a line with 'inferred = """open an issue titled "Beam drifts $stamp""""'
    And the script "open-gen.kts" has no escaped quote

  Scenario: A detour the planner took is left out of the script made from its run
    The planner looks at issue 11 first, which the goal never asked for, and
    reads nothing there that a later step uses; then it does the goal. The
    script made from the run does only what the goal needed. A read a later
    step takes a value from stays.
    Given the planner will move
      | move   | words                              | sentence | opens | to                           |
      | act    | look at issue 11 of acme/rockets   | 1        | yes   |                              |
      | back   |                                    |          |       | https://github.gateway.test/ |
      | act    | open issue $number of $owner/$repo | 1        | yes   |                              |
      | done   |                                    | 1        |       |                              |
      | act    | close it                           | 2        |       |                              |
      | done   |                                    | 2        |       |                              |
      | finish |                                    |          |       |                              |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                 |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 11} |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:close      | {}                                                     |
    When the goal "close.goal.md" runs with ARTIE, captured in "goal.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    When artie generated "goal-gen.kts" from "goal.trace.yaml" and "close.goal.md"
    Then the script "goal-gen.kts" nests its acts as
      | relation          | depth |
      | github:issues-get | 1     |
      | github:close      | 2     |
    And the script "goal-gen.kts" has no line with "look at issue 11"

  Scenario: An act refused before anything is sent is told to the planner, which tries another way
    The reasoner picks merge, which an issue doesn't offer; ARTIE refuses it
    before sending. The planner is told, and closes the issue instead. The
    refused act was never done, so the capture leaves it out, and the
    script generated from it has only the acts that were.
    Given the planner will move
      | move   | words                              | sentence | opens |
      | act    | open issue $number of $owner/$repo | 1        | yes   |
      | done   |                                    | 1        |       |
      | act    | finish it off                      | 2        |       |
      | act    | close it                           | 2        |       |
      | done   |                                    | 2        |       |
      | finish |                                    |          |       |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                 |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:merge      | {}                                                     |
      | github:close      | {}                                                     |
    When the goal "close.goal.md" runs with ARTIE, captured in "goal.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    And the planner was told "finish it off: refused"
    And issue 10 of "acme/rockets" is now "closed" on the small GitHub
    When artie generated "goal-gen.kts" from "goal.trace.yaml" and "close.goal.md"
    Then the script "goal-gen.kts" nests its acts as
      | relation          | depth |
      | github:issues-get | 1     |
      | github:close      | 2     |

  Scenario: A wait that names no form is told to the planner, and waits for nothing
    Given the planner will move
      | move   | words                              | sentence | opens | relation     |
      | act    | open issue $number of $owner/$repo | 1        | yes   |              |
      | wait   |                                    |          |       |              |
      | wait   |                                    |          |       | github:close |
      | act    | close it                           | 2        |       |              |
      | done   |                                    | 1        |       |              |
      | done   |                                    | 2        |       |              |
      | finish |                                    |          |       |              |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                 |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:close      | {}                                                     |
    When the goal "close.goal.md" runs with ARTIE, captured in "goal.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    And the planner was told "wait: refused, a wait names a form"

  Scenario: The planner is shown what each form offered is for
    Given the planner will move
      | move   | words                              | sentence | opens |
      | act    | open issue $number of $owner/$repo | 1        | yes   |
      | done   |                                    | 1        |       |
      | done   |                                    | 2        |       |
      | finish |                                    |          |       |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                 |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
    When the goal "close.goal.md" runs with ARTIE, captured in "goal.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the planner was last shown "github:close" described as "Close"

  Scenario Outline: A goal run stops, before its harm is done, when <case>
    Given the planner will move
      | move   | words                              | sentence | opens | question     |
      | act    | open issue $number of $owner/$repo | 1        | yes   |              |
      | <last> |                                    |          |       | <question>   |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                 |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
    When the goal "close.goal.md" runs with ARTIE, captured in "goal.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the goal run stopped, saying "<said>"
    And issue 10 of "acme/rockets" is now "open" on the small GitHub

    Examples:
      | case                                | last    | question            | said                                         |
      | it finishes with sentences not done | finish  |                     | not every sentence is done: 1, 2             |
      | the planner asks, and no one is there | clarify | Which comment text? | the planner asks: Which comment text?        |
      | it moves more than it may           | loop    |                     | more than 50 moves                           |
