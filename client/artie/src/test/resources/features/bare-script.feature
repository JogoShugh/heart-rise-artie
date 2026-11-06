Feature: ARTIE sends a bare script's calls as written, with its own credential and trace, and nothing else
  spec/ideas/bare-runs.md. A bare script is a recorded run brought down
  to the calls it made, sent straight to the service, as the native
  Python sends them. ARTIE sends each one as written: its credential for
  the origin, and its traceparent, so the run's capture keeps the
  exchanges as any run's. It adds nothing a HEART client would: no
  If-Match, no If-None-Match, no cached answer, no integrity check.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has these issues on the small GitHub
      | number | title                 | state | author |
      | 10     | Login fails on Safari | open  | ada    |
    And ARTIE talks straight to the small GitHub at "https://api.github.test"
    And ARTIE holds the Bearer credential "ada-token" for "https://api.github.test"
    And ARTIE keeps its exchanges

  Scenario: Each call reaches GitHub as written, read twice and written once, with no validator and no cache
    When this script runs with ARTIE
      """kotlin
      bare {
          val issue = call("GET", "https://api.github.test/repos/acme/rockets/issues/10")
          call("GET", "https://api.github.test/repos/acme/rockets/issues/10")
          call("PATCH", "https://api.github.test/repos/acme/rockets/issues/${issue["number"].text}", mapOf("state" to "closed"))
      }
      """
    Then the script finished
    And issue 10 of "acme/rockets" is now "closed" on the small GitHub
    And the run kept these exchanges
      | step | method | url                                                | status |
      | 1    | GET    | https://api.github.test/repos/acme/rockets/issues/10 | 200    |
      | 2    | GET    | https://api.github.test/repos/acme/rockets/issues/10 | 200    |
      | 3    | PATCH  | https://api.github.test/repos/acme/rockets/issues/10 | 200    |
    And every exchange the run kept sends its credential as "<token>" and a traceparent
    And no exchange the run kept sends "If-Match" or "If-None-Match"

  Scenario: A bare script's one reasoned line is written by ARTIE's reasoner, as the one field of a form of its own
    Given ARTIE's reasoner will choose to act on "write" with {"text": "Fixed in 1.2; thanks for the report."}
    When this script runs with ARTIE, allowed its reasoner
      """kotlin
      bare {
          call("POST", "https://api.github.test/repos/acme/rockets/issues/10/comments",
              mapOf("body" to write("Tell its author, in one friendly sentence, that it was fixed in 1.2")))
      }
      """
    Then the script finished
    And issue 10 of "acme/rockets" now has these comments on the small GitHub
      | author | body                                  |
      | ada    | Fixed in 1.2; thanks for the report.  |

  Scenario: An answer GitHub refuses stops the script, with GitHub's own words
    When this script runs with ARTIE
      """kotlin
      bare {
          call("GET", "https://api.github.test/repos/acme/rockets/branches/fix-blank-names")
      }
      """
    Then the script stopped at step 1, "GET https://api.github.test/repos/acme/rockets/branches/fix-blank-names": the server answered 404: {"message":"Branch not found"}
