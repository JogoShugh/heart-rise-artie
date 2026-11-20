Feature: A test script expects what a step answers
  spec/PLAN-ARTIE-SCRIPTS.md, heart-script-test. A conformance or
  demonstration script provokes outcomes and refusals on purpose: expect
  names what the steps in its block must answer, by meaning. Met, the script
  goes on, even past a refusal it expected. Not met, the script stops, as a
  test failure rather than a refusal. The trace marks each expected step.

  Background:
    Given a service holds these resources
      | address    | title                 | state |
      | /          | Repositories          |       |
      | /issues/10 | Login fails on Safari | open  |
      | /c/502     | Fixed in 1.2.         |       |
    And these forms
      | at         | relation   | leads to   | outcome      |
      | /          | issues-get | /issues/10 | done         |
      | /issues/10 | comment    | /c/502     | new resource |
      | /issues/10 | close      | /issues/10 | done         |
      | /issues/10 | lock       | /issues/10 | done         |
    And the server will refuse "lock" at "/issues/10" as stale: "The issue changed since you read it."

  Scenario Outline: An expectation met lets the script go on
    When this test script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          expect(<expected>) { act("<relation>") }
          note("went on")
      }
      """
    Then the script finished
    And the script noted "went on"
    And trace step 3 expected "<words>"

    Examples:
      | expected    | relation | words        |
      | NewResource | comment  | new resource |
      | Done        | close    | done         |
      | Unchanged   | close    | unchanged    |
      | NotOffered  | reopen   | not offered  |
      | Stale       | lock     | stale        |

  Scenario Outline: An expectation not met fails the test at its step
    When this test script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          expect(<expected>) { act("<relation>") }
          note("went on")
      }
      """
    Then the test failed at step 2: <reason>
    And the script noted nothing

    Examples:
      | expected    | relation | reason                                                                                   |
      | Done        | comment  | expected done, but it was new resource                                                   |
      | Unchanged   | comment  | expected unchanged, but it was new resource                                              |
      | NotOffered  | close    | expected not offered, but it was done                                                    |
      | NewResource | reopen   | expected new resource, but it was not offered, refused by the client: "The issue is already open." |
      | NotOffered  | lock     | expected not offered, but it was stale, refused by the server: "The issue changed since you read it." |

  Scenario: A refusal after the expected step still stops the script
    When this test script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          expect(Done) {
              act("close")
              act("reopen")
          }
          note("went on")
      }
      """
    Then the script stopped at step 3, "reopen": not offered, refused by the client: "The issue is already open."
    And the script noted nothing

  Scenario: Done is not unchanged when the client then holds something else
    When this test script runs
      """kotlin
      heart("scripted://service/") {
          expect(Unchanged) { act("issues-get", "number" to 10) }
      }
      """
    Then the test failed at step 1: expected unchanged, but it was done

  Scenario: An expectation with no step in it fails the test
    When this test script runs
      """kotlin
      heart("scripted://service/") {
          expect(Done) { note("nothing asked") }
      }
      """
    Then the test failed at step 1: expected done, but no step was taken
