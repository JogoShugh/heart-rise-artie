Feature: Listeners, each on its own: a slow one never holds up another
  spec/PLAN-LISTENERS.md, slice 1. A watch with listeners runs each listener
  on a thread of its own, with a client of its own: each looks again and
  again, its own pause apart, from where the watch stands, until the watch's
  condition holds. A listener's slow server, or its slow handler, holds up
  only that listener. The watch's condition is read on the watch's own
  thread, at its own pace. What each listener did, and noted, joins the
  run's record once the watch is over. The gateway and a small GitHub run in
  process; the run's clock is the real one.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has these issues on the small GitHub
      | number | title         | state | author |
      | 10     | Veracode scan | open  | ada    |
    And "acme/rockets" has pull request 1 on the small GitHub, open, titled "Bump left-pad to 1.0.1"
    And "acme/rockets" has pull request 2 on the small GitHub, open, titled "Bump left-pad to 1.0.2"
    And ARTIE talks to the GitHub gateway at "https://github.gateway.test" in front of the small GitHub
    And ARTIE holds the Bearer credential "ada-token" for "https://github.gateway.test"
    And the client works at rung L2

  Scenario: One listener waits on a slow read, while another merges a pull request
    The small GitHub holds the first read of issue 10 until pull request 2
    is merged, or 5 seconds have passed. One listener reads issue 10; the
    other merges what can be merged. In one loop, the merge would come only
    after the read gave up waiting.
    Given the small GitHub holds its first answer to "/repos/acme/rockets/issues/10" until pull request 2 is merged, at most 5 seconds
    When this script runs with ARTIE
      """kotlin
      import kotlin.time.Duration.Companion.milliseconds

      heart("https://github.gateway.test/") {
          var looks = 0
          listeners(every = 200.milliseconds, until = { ++looks >= 4 }) {
              var said = false
              listen("scan", every = 100.milliseconds) {
                  act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10) {
                      if (!said) note("scan: issue 10 is ${state["state"]}")
                      said = true
                  }
              }
              listen("merge", every = 100.milliseconds) {
                  act("github:pulls-list", "owner" to "acme", "repo" to "rockets", "state" to "open") {
                      whenever {
                          once("github:merge") {
                              act("github:merge", "merge_method" to "squash") {}
                          }
                      }
                  }
              }
          }
      }
      """
    Then the script finished
    And the small GitHub answered "/repos/acme/rockets/issues/10" the first time only after pull request 2 was merged
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 2      |
      | 1      |
    And the run noted one note beginning "scan: issue 10 is open"
