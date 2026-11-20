Feature: A HEART script drives GitHub issues through the gateway, with ARTIE as its client
  spec/PLAN-ARTIE-SCRIPTS.md, step 2. ARTIE implements heart-script's
  contract (heart.script.Navigator), so a script runs on it unchanged: its
  selections become ARTIE's forms and links, its blocks ARTIE's visits and
  re-syncs, and ARTIE's refusals the DSL's kinds. The gateway and a small
  GitHub run in process. The script names the bookmark and nothing else.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has these issues on the small GitHub
      | number | title                 | state | author |
      | 10     | Login fails on Safari | open  | ada    |
    And ARTIE talks to the GitHub gateway at "https://github.gateway.test" in front of the small GitHub
    And ARTIE holds the Bearer credential "ada-token" for "https://github.gateway.test"

  Scenario: Comment, edit the comment, find it by the issue's link, close the issue
    When this script runs with ARTIE
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10)
          act("github:comment", "body" to "Fixed in 1.2.") {
              act("github:edit", "body" to "Fixed in 1.2. (edited)")
          }
          follow("github:issues-list-comments") {
              follow("item", title = "Fixed in 1.2. (edited)") {
                  check(state["/body"] == "Fixed in 1.2. (edited)")
              }
          }
          act("github:close")
          check(state["/state"] == "closed")
      }
      """
    Then the script finished
    And issue 10 of "acme/rockets" is now "closed" on the small GitHub
    And issue 10 of "acme/rockets" now has these comments on the small GitHub
      | author | body                   |
      | ada    | Fixed in 1.2. (edited) |

  Scenario: An act no longer offered stops the script, refused by ARTIE from the manifest, with the server's reason
    When this script runs with ARTIE
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10)
          act("github:close")
          act("github:close")
      }
      """
    Then the script stopped at step 3, "github:close": not offered, refused by the client: "The issue is already closed."
    And issue 10 of "acme/rockets" is now "closed" on the small GitHub

  Scenario: A reason step: ARTIE's reasoner chooses, ARTIE checks the choice and makes it
    Given ARTIE's reasoner will choose to act on "github:comment" with {"body": "Thanks, fixed."}
    When this script runs with ARTIE, allowed its reasoner
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10)
          act(intent = "thank them, and say it's fixed") {
              check(state["/body"] == "Thanks, fixed.")
          }
      }
      """
    Then the script finished
    And issue 10 of "acme/rockets" now has these comments on the small GitHub
      | author | body           |
      | ada    | Thanks, fixed. |

  Scenario: A choice the manifest doesn't offer is refused by ARTIE, and nothing is sent
    Given ARTIE's reasoner will choose to act on "github:reopen" with {}
    When this script runs with ARTIE, allowed its reasoner
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10)
          act(intent = "open it again")
      }
      """
    Then the script stopped at step 2, "intent": not offered, refused by the client: "github:reopen is not in the current manifest"
    And issue 10 of "acme/rockets" is now "open" on the small GitHub

  Scenario: A server ARTIE can't reach stops the script as the client's failure, not a bug in the script
    Given the gateway can't be reached for "/repos/acme/rockets/issues/10"
    When this script runs with ARTIE
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10)
      }
      """
    Then the script stopped at step 1, "github:issues-get": unreachable, refused by the client: "could not reach the server"

  Scenario: An entry no binding of ARTIE's serves is refused before anything is sent
    When this script runs with ARTIE
      """kotlin
      heart("gopher://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10)
      }
      """
    Then the script stopped before its first request: no binding, refused by the client: "no binding for the scheme gopher: gopher://github.gateway.test/ was refused, and nothing was sent"

  Scenario: A script host finds ARTIE as its client on the classpath, as JDBC finds a driver
    When a script host looks for a HEART client
    Then it finds ARTIE
