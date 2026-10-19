Feature: A script run with ARTIE records the form or link each step used, as the server gave it
  spec/PLAN-LINE-BY-LINE-REPORT.md, step 2. ARTIE knows what it sent: the
  form's target as offered (a URI template at L2), the address it filled in,
  the method, and what a replay would guard on: the rung, the affordance set's
  address and integrity, the form's own integrity (HAL-SCHEMA-FORMS-HR Rule
  10), its schema's address and integrity, and the If-Match it sent. A reason
  step records the same for the choice the reasoner made.

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

  Scenario: An act records the form it used, as the gateway offered it, and what a replay guards on
    When this script runs with ARTIE
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10)
          act("github:close")
      }
      """
    Then the script finished
    And the run's step 3 used
      | what     | value                                                                  |
      | kind     | form                                                                   |
      | relation | github:close                                                           |
      | method   | POST                                                                   |
      | target   | /repos/{owner}/{repo}/issues/{issue_number}/actions/close              |
      | address  | https://github.gateway.test/repos/acme/rockets/issues/10/actions/close |
    And the run's step 3 guards on rung L2, and on the set, form and schema the gateway serves, with its If-Match

  Scenario: A reason step records the form the reasoner chose
    Given ARTIE's reasoner will choose to act on "github:comment" with {"body": "Thanks, fixed."}
    When this script runs with ARTIE, allowed its reasoner
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10)
          reason("thank them, and say it's fixed")
      }
      """
    Then the script finished
    And the run's step 3 used
      | what     | value                                                                    |
      | kind     | form                                                                     |
      | relation | github:comment                                                           |
      | method   | POST                                                                     |
      | address  | https://github.gateway.test/repos/acme/rockets/issues/10/actions/comment |
    And the run's step 3 guards on rung L2, and on the set, form and schema the gateway serves, with its If-Match
