Feature: Off the rails: a run that reads only what it must, checks only what the server will, and trusts the rest
  spec/ideas/off-the-rails.md. --guard off-the-rails turns ARTIE's own care
  off, for speed where nothing changes, and to show what the care costs: a
  block is left with nothing read again, a resource just made is not read
  again, sync() reads nothing, a body is not checked against its form's
  schema before it is sent, the lock file is not consulted, and a 412 stops
  the run instead of being decided again. Each act still carries the last
  validator the run was given for its resource, and the server still checks
  what it checks: a body it refuses is refused by the server, not the client.
  Waits and checks are the task's, and stay.

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

  Scenario Outline: <guard>: the issue is read <reads> times
    Lenient reads the issue to act on it, again for sync(), and again when
    the comment's block is left; off the rails, only the first. The close
    comes first: a comment changes the issue, as GitHub counts its comments,
    and a close sent off the rails after it would carry a stale validator.
    Given ARTIE runs with the guard "<guard>"
    When this script runs with ARTIE
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10) {
              sync()
              act("github:close")
              act("github:comment", "body" to "Thanks.") {}
          }
      }
      """
    Then the run finished
    And issue 10 of "acme/rockets" is now "closed" on the small GitHub
    And the client read "https://github.gateway.test/repos/acme/rockets/issues/10" <reads> times

    Examples:
      | guard         | reads |
      | lenient       | 3     |
      | off-the-rails | 1     |

  Scenario Outline: <guard>: a body its form doesn't allow is refused by <whom>
    Given ARTIE runs with the guard "<guard>"
    When this script runs with ARTIE
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10) {
              act("github:comment", "body" to "Thanks.", "colour" to "red")
          }
      }
      """
    Then the script stopped at step 2, "github:comment": invalid input, refused by <whom>: <said>

    Examples:
      | guard         | whom       | said                                  |
      | lenient       | the client | "colour is not in the schema"         |
      | off-the-rails | the server | ": property 'colour' is not defined in the schema and the schema does not allow additional properties" |

  Scenario Outline: Validators <validators>, the gateway's switch <switch>: the comment is sent <how>
    By default each act carries the last validator the run was given. With
    --validators none it carries none: a blind write, which a HEART server
    takes only by its development switch, for a trial, and refuses otherwise.
    Given ARTIE runs with the guard "off-the-rails" and the validators "<validators>"
    And the GitHub gateway's switch for acts with no validator is <switch>
    When this script runs with ARTIE
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10) {
              act("github:comment", "body" to "Thanks.")
          }
      }
      """
    Then the client sent the comment <how>
    And the run's step 3 records the comment sent <how>
    And <outcome>

    Examples:
      | validators | switch | how            | outcome                                                          |
      | last       | off    | with If-Match  | the run finished                                                 |
      | none       | on     | with no If-Match | the run finished                                               |
      | none       | off    | with no If-Match | the script stopped at step 2, "github:comment": precondition required, refused by the server: "the act was sent with no validator" |
