Feature: A goal in steps, done step by step: the runner keeps the structure, the planner makes the moves
  spec/PLAN-GOAL-MARKDOWN.md. A goal file's list is its steps. The planner is
  shown one step at a time, where it stands among the steps, and the
  resources the steps named; it makes the moves that step allows: acts and a
  done for an intent, a wait for Wait:, a check for Verify:. A step's
  sub-steps start where its last opening act led; when they are done, the
  runner goes back to where the step began. The run ends with the last
  step. A step, and the run, have a limit of moves. The gateway and a small
  GitHub run in process; the planner and the reasoner are scripted.

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
    And the file "fix.goal.md" is
      """
      ---
      start: https://github.gateway.test/
      inputs:
        number:
      ---

      Fix the Safari issue.

      - Open issue {number} of acme/rockets `as issue`
        - Comment "Fixed in 1.2, see #{issue.number}."
      - On {issue}
        - Close it
        - Wait: it can be reopened
        - Verify: it is closed
      """

  Scenario: Each step in turn, the sub-steps inside what their step led to, and back where it began after
    The comment is a resource of its own: the act that makes it leads
    there, and when its step is done the runner goes back to the issue,
    then back to the start when the issue's step is done. No back move,
    no finish: the steps say where to go, and the run ends with the last.
    Given the planner will move
      | move  | words                                 | opens | relation     | facts               |
      | act   | open issue $number of acme/rockets    | yes   |              |                     |
      | done  |                                       |       |              |                     |
      | act   | comment on it: Fixed in 1.2, see #10. | yes   |              |                     |
      | done  |                                       |       |              |                     |
      | act   | open issue 10 of acme/rockets         | yes   |              |                     |
      | done  |                                       |       |              |                     |
      | act   | close it                              |       |              |                     |
      | done  |                                       |       |              |                     |
      | wait  |                                       |       | github:reopen |                    |
      | check |                                       |       |              | {"state": "closed"} |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:comment    | {"body": "Fixed in 1.2, see #10."}                       |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:close      | {}                                                       |
    When the goal "fix.goal.md" runs with ARTIE, captured in "fix.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    And issue 10 of "acme/rockets" is now "closed" on the small GitHub
    And the planner was shown these steps, move by move
      | step                                  | at                |
      | Open issue 10 of acme/rockets         | /                 |
      | Open issue 10 of acme/rockets         | /issues/10        |
      | Comment "Fixed in 1.2, see #10."      | /issues/10        |
      | Comment "Fixed in 1.2, see #10."      | comments          |
      | On https://github.gateway.test/repos/acme/rockets/issues/10 | /    |
      | On https://github.gateway.test/repos/acme/rockets/issues/10 | /issues/10 |
      | Close it                              | /issues/10        |
      | Close it                              | /issues/10        |
      | Wait: it can be reopened              | /issues/10        |
      | Verify: it is closed                  | /issues/10        |
    And the planner was last shown "issue" named, at an address ending "/issues/10"
    And at move 1 the planner was shown the steps
      | line                                               |
      | [now] Open issue {number} of acme/rockets (as issue) |
      | (1 sub-step comes next, from where this step ends) |
    And at move 5 the planner was shown the steps
      | line                                                  |
      | [done] Open issue {number} of acme/rockets (as issue) |
      | [now] On {issue}                                      |
      | (3 sub-steps come next, from where this step ends)    |
    And at move 7 the planner was shown the steps
      | line                                                  |
      | [done] Open issue {number} of acme/rockets (as issue) |
      | [in] On {issue}                                       |
      |   [now] Close it                                      |
    And the capture "fix.trace.yaml" keeps the planner's moves, the first
      | move                                                       |
      | open issue 10 of acme/rockets: acted github:issues-get, done, at https://github.gateway.test/repos/acme/rockets/issues/10 |
      | done: Open issue 10 of acme/rockets                        |

  Scenario: The script made from a goal in steps names values by the steps' names, and keeps a reasoned step's intent
    The issue's step is named issue, so its title, read where that step
    led, is issueTitle. Close it is marked reasoned: the model decides it on
    every run of the script, which says so at its top; every other step is
    fixed. The step kept is the person's words, not the planner's: those say
    how this run closed it, which is what the model is to decide each time.
    Every other act keeps its step's words too, a person's intent, each
    input the variable the script reads; the planner's own words for an act
    are kept only where no step's words are, as inferred.
    Given line 10 of "fix.goal.md" reads '- Comment "Fixed: {issue.title}."', indented 2
    And line 12 of "fix.goal.md" reads '- Close it `reasoned`', indented 2
    And the planner will move
      | move  | words                                         | opens | relation      | facts               |
      | act   | open issue $number of acme/rockets            | yes   |               |                     |
      | done  |                                               |       |               |                     |
      | act   | comment on it: Fixed: Login fails on Safari.  | yes   |               |                     |
      | done  |                                               |       |               |                     |
      | act   | open issue 10 of acme/rockets         | yes   |               |                     |
      | done  |                                       |       |               |                     |
      | act   | close it as completed                 |       |               |                     |
      | done  |                                       |       |               |                     |
      | wait  |                                       |       | github:reopen |                     |
      | check |                                       |       |               | {"state": "closed"} |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:comment    | {"body": "Fixed: Login fails on Safari."}                |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:close      | {}                                                       |
    When the goal "fix.goal.md" runs with ARTIE, captured in "fix.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    When artie generated "fix-gen.kts" from "fix.trace.yaml" and "fix.goal.md"
    Then the script "fix-gen.kts" nests its acts as
      | relation          | depth |
      | github:issues-get | 1     |
      | github:comment    | 2     |
      | github:issues-get | 1     |
    And the script "fix-gen.kts" has a line with "val issueTitle by kept(\"issue\") { text(\"title\") }"
    And the script "fix-gen.kts" has a line with "keepAs(\"issue\")"
    And the script "fix-gen.kts" has no line with "var "
    And the script "fix-gen.kts" has a line with "Fixed: $issueTitle."
    And the script "fix-gen.kts" has a line with "act(intent = \"Close it\")"
    And the script "fix-gen.kts" has no line with "as completed"
    And the script "fix-gen.kts" has a line with "intent = \"Open issue ${number} of acme/rockets\""
    And the script "fix-gen.kts" has a line with "intent = \"\"\"Comment \"Fixed: {issue.title}.\"\"\"\""
    And the script "fix-gen.kts" has no line with "intent = \"open issue"
    And the script "fix-gen.kts" has a line with "until(tries = 3) { offers(\"github:reopen\") }"
    And the script "fix-gen.kts" has a line with "// 1 step calls the model on every run: artie run needs --reasoner."

  Scenario: What an act led to is held until its step is done; the next step starts where that step began
    The comment is a resource of its own. Its act says nothing of a block,
    yet once its step is done, the run is back at the issue, where the
    next step checks the issue, not the comment.
    Given line 12 of "fix.goal.md" reads '- Comment "Thanks."', indented 2
    And line 13 of "fix.goal.md" reads '- Verify: it is open', indented 2
    And line 14 of "fix.goal.md" reads '', indented 0
    And the planner will move
      | move  | words                                 | opens | facts             |
      | act   | open issue $number of acme/rockets    | yes   |                   |
      | done  |                                       |       |                   |
      | act   | comment on it: Fixed in 1.2, see #10. |       |                   |
      | done  |                                       |       |                   |
      | act   | open issue 10 of acme/rockets         | yes   |                   |
      | done  |                                       |       |                   |
      | act   | comment on it: Thanks.                |       |                   |
      | done  |                                       |       |                   |
      | check |                                       |       | {"state": "open"} |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:comment    | {"body": "Fixed in 1.2, see #10."}                       |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:comment    | {"body": "Thanks."}                                      |
    When the goal "fix.goal.md" runs with ARTIE, captured in "fix.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    And the planner was last shown "issue" named, at an address ending "/issues/10"

  Scenario: Within a step, back goes as far as where the step began; the step's sub-steps start where it is when done
    The planner opens the issue, then a comment on it, which the step
    didn't need: it goes back to the issue, still within the step, and
    says it is done there, so the comment's step starts at the issue.
    Given the planner will move
      | move  | words                                 | opens | relation      | facts               | to        |
      | act   | open issue $number of acme/rockets    | yes   |               |                     |           |
      | act   | comment on it: First look.            | yes   |               |                     |           |
      | back  |                                       |       |               |                     | the issue |
      | done  |                                       |       |               |                     |           |
      | act   | comment on it: Fixed in 1.2, see #10. | yes   |               |                     |           |
      | done  |                                       |       |               |                     |           |
      | act   | open issue 10 of acme/rockets         | yes   |               |                     |           |
      | done  |                                       |       |               |                     |           |
      | act   | close it                              |       |               |                     |           |
      | done  |                                       |       |               |                     |           |
      | wait  |                                       |       | github:reopen |                     |           |
      | check |                                       |       |               | {"state": "closed"} |           |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:comment    | {"body": "First look."}                                  |
      | github:comment    | {"body": "Fixed in 1.2, see #10."}                       |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:close      | {}                                                       |
    When the goal "fix.goal.md" runs with ARTIE, captured in "fix.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    And the planner was told "back to the issue"
    And the planner was last shown "issue" named, at an address ending "/issues/10"

  Scenario Outline: A step allows only its own moves: <move> is refused at <step>, and changes nothing
    Given the planner will move
      | move   | words                              | opens | relation | facts               |
      | act    | open issue $number of acme/rockets | yes   |          |                     |
      | done   |                                    |       |          |                     |
      | act    | comment on it: Fixed.              | yes   |          |                     |
      | <move> | look again                         |       |          | {"state": "closed"} |
      | done   |                                    |       |          |                     |
      | stop   | enough                             |       |          |                     |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:comment    | {"body": "Fixed."}                                       |
    When the goal "fix.goal.md" runs with ARTIE, captured in "fix.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the goal run stopped, saying "enough"
    And the planner was told "<told>"

    Examples:
      | move   | step      | told                                                         |
      | back   | an intent | back: refused, only as far back as where this step began     |
      | finish | an intent | finish: refused, the run ends with the last step             |
      | check  | an intent | check: refused, this step is an intent: act, then done       |

  Scenario: A Verify: step takes a check, and a Wait: step a wait; anything else is refused
    Given the planner will move
      | move  | words                                 | opens | relation      | facts               |
      | act   | open issue $number of acme/rockets    | yes   |               |                     |
      | done  |                                       |       |               |                     |
      | act   | comment on it: Fixed in 1.2, see #10. | yes   |               |                     |
      | done  |                                       |       |               |                     |
      | act   | open issue 10 of acme/rockets         | yes   |               |                     |
      | done  |                                       |       |               |                     |
      | act   | close it                              |       |               |                     |
      | done  |                                       |       |               |                     |
      | act   | reopen it                             |       |               |                     |
      | wait  |                                       |       | github:reopen |                     |
      | done  |                                       |       |               |                     |
      | check |                                       |       |               | {"state": "closed"} |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:comment    | {"body": "Fixed in 1.2, see #10."}                       |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
      | github:close      | {}                                                       |
    When the goal "fix.goal.md" runs with ARTIE, captured in "fix.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the run finished
    And the planner was told "act: refused, this step is a Wait:, which takes a wait"
    And the planner was told "done: refused, this step is a Verify:, which takes a check"
    And issue 10 of "acme/rockets" is now "closed" on the small GitHub

  Scenario Outline: A goal in steps stops, before its harm is done, when <case>
    Given line <line> of "fix.goal.md" reads '<reads>', indented <indent>
    And the planner will move
      | move | words                              | opens |
      | act  | open issue $number of acme/rockets | yes   |
      | loop |                                    |       |
    And ARTIE's reasoner will choose, in order
      | form              | fields                                                   |
      | github:issues-get | {"owner": "acme", "repo": "rockets", "issue_number": 10} |
    When the goal "fix.goal.md" runs with ARTIE, captured in "fix.trace.yaml", given
      | name   | value |
      | number | 10    |
    Then the goal run stopped, saying "<said>"
    And issue 10 of "acme/rockets" is now "open" on the small GitHub

    Examples:
      | case                                   | line | indent | reads                                                       | said                                  |
      | a step makes more moves than it may    | 9    | 0      | - Open issue {number} of acme/rockets `as issue` `moves 3`   | line 9: more than 3 moves for one step |
      | every step may make fewer, by default  | 4    | 2      | number:\nlimits: { step: 4 }                                 | line 10: more than 4 moves for one step |
      | the run makes more moves than it may   | 4    | 2      | number:\nlimits: { moves: 5, step: 9 }                       | more than 5 moves                      |
