Feature: A script that keeps watching: watch, whenever and once, the goal words in heart-script
  spec/ideas/goals-that-work-alongside-others.md, "Watching for a long time".
  watch looks again and again, a pause apart, until its condition holds:
  each look reads the held resource again and runs its block; a look that
  fails is noted, and the next one tries again. whenever visits the members
  of the held list that are new or changed since the last look, and those a
  once under it still waits on. once does its block when the act is offered,
  one time for each resource; in a watch, a wait that isn't over is left for
  the next look, so one resource never holds up the others; outside a watch,
  it reads the resource again until the wait is over. When the wait outlasts
  its give-up, once does otherwise, told why the act isn't offered, in the
  server's words. The run's clock here moves only when the run pauses. The
  gateway and a small GitHub run in process.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has pull request 1 on the small GitHub, open, titled "Bump left-pad to 1.0.1"
    And "acme/rockets" has pull request 2 on the small GitHub, open, titled "Bump left-pad to 1.0.2"
    And ARTIE talks to the GitHub gateway at "https://github.gateway.test" in front of the small GitHub
    And ARTIE holds the Bearer credential "ada-token" for "https://github.gateway.test"
    And the client works at rung L2
    And the run's clock moves only when the run pauses

  Scenario: A watch merges each pull request at the look it can be merged, and stops when its condition holds
    Pull request 1 is a draft at the first look, and marked ready just after;
    pull request 2 can be merged at once. Each is merged once, at its look.
    Given pull request 1 is a draft on the small GitHub until it is next read
    When this script runs with ARTIE
      """kotlin
      import kotlin.time.Duration.Companion.seconds

      heart("https://github.gateway.test/") {
          var looks = 0
          act("github:pulls-list", "owner" to "acme", "repo" to "rockets", "state" to "open") {
              watch(every = 10.seconds, until = { ++looks == 3 }) {
                  whenever {
                      once("github:merge") {
                          act("github:merge", "merge_method" to "squash") {}
                      }
                  }
              }
          }
      }
      """
    Then the script finished
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 2      |
      | 1      |

  Scenario: A wait that outlasts its give-up does otherwise, told why in the gateway's words, and never merges
    Given pull request 1 is a draft on the small GitHub
    When this script runs with ARTIE
      """kotlin
      import kotlin.time.Duration.Companion.seconds

      heart("https://github.gateway.test/") {
          var looks = 0
          act("github:pulls-list", "owner" to "acme", "repo" to "rockets", "state" to "open") {
              watch(every = 10.seconds, until = { ++looks == 4 }) {
                  whenever {
                      once("github:merge", giveUp = 15.seconds, otherwise = { why -> note("#${state["number"]}: $why") }) {
                          act("github:merge", "merge_method" to "squash") {}
                      }
                  }
              }
          }
      }
      """
    Then the script finished
    And the run noted
      | note                                                         |
      | #1: A draft can't be merged; mark it ready for review first. |
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 2      |

  Scenario: A look that fails is noted, and the next look tries again
    Given the small GitHub answers the 2nd read of "/repos/acme/rockets/pulls" with 500
    When this script runs with ARTIE
      """kotlin
      import kotlin.time.Duration.Companion.seconds

      heart("https://github.gateway.test/") {
          var looks = 0
          act("github:pulls-list", "owner" to "acme", "repo" to "rockets", "state" to "open") {
              watch(every = 10.seconds, until = { ++looks == 2 }) {
                  whenever {
                      once("github:merge") {
                          act("github:merge", "merge_method" to "squash") {}
                      }
                  }
              }
          }
      }
      """
    Then the script finished
    And the run noted one note beginning "A look failed:"
    And the small GitHub was asked to merge these pull requests, in order
      | number |
      | 2      |
      | 1      |

  Scenario: A watch that remembers doesn't do again, run again, what it did before
    The first run gives up on the draft and says so; the second, keeping
    the same memory, knows that wait is over and says nothing again.
    Given pull request 1 is a draft on the small GitHub
    And the run remembers in "watch.yaml"
    And the script is
      """kotlin
      import kotlin.time.Duration.Companion.seconds

      heart("https://github.gateway.test/") {
          var looks = 0
          act("github:pulls-list", "owner" to "acme", "repo" to "rockets", "state" to "open") {
              watch(every = 10.seconds, until = { ++looks == 3 }) {
                  whenever {
                      once("github:merge", giveUp = 15.seconds, otherwise = { why -> note("#${state["number"]}: $why") }) {
                          act("github:merge", "merge_method" to "squash") {}
                      }
                  }
              }
          }
      }
      """
    When the script runs with ARTIE
    Then the run noted
      | note                                                         |
      | #1: A draft can't be merged; mark it ready for review first. |
    When the script runs with ARTIE again
    Then the script finished
    And the run noted
      | note |
      |      |

  Scenario Outline: Outside a watch, once reads the resource again until the wait is over: <case>
    Given pull request 1 is <draft>
    When this script runs with ARTIE
      """kotlin
      import kotlin.time.Duration.Companion.seconds

      heart("https://github.gateway.test/") {
          act("github:pulls-get", "owner" to "acme", "repo" to "rockets", "pull_number" to 1) {
              once("github:merge", giveUp = 5.seconds, otherwise = { why -> note("not merged: $why") }) {
                  act("github:merge", "merge_method" to "squash") {}
              }
          }
      }
      """
    Then the script finished
    And the run noted
      | note   |
      | <note> |
    And the small GitHub was asked to merge these pull requests, in order
      | number   |
      | <merged> |

    Examples:
      | case                           | draft                                            | note                                                                 | merged |
      | it is marked ready, and merged | a draft on the small GitHub until it is next read |                                                                      | 1      |
      | it stays a draft, and gives up | a draft on the small GitHub                      | not merged: A draft can't be merged; mark it ready for review first. |        |

  Scenario: at reads again a resource the server handed over earlier, there runs its block, and comes back
    spec/PLAN-LISTENERS.md, slice 8: the watch's Until reads the issue the
    watch opened, by the address the server gave it. The watch ends when the
    issue is closed, at its third look, and notes nothing.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title          | state | author |
      | 10     | The bench week | open  | ada    |
    And issue 10 of "acme/rockets" is closed on the small GitHub, whatever happens, once its pull requests are listed 3 times
    When this script runs with ARTIE
      """kotlin
      import kotlin.time.Duration.Companion.seconds

      heart("https://github.gateway.test/") {
          var watched = ""
          act("github:issues-get", "owner" to "acme", "repo" to "rockets", "issue_number" to 10) { watched = held.address }
          act("github:pulls-list", "owner" to "acme", "repo" to "rockets", "state" to "open") {
              watch(every = 10.seconds, until = {
                  var closed = false
                  at(watched) { closed = holds(mapOf("state" to "closed")) }
                  closed
              }) {
                  whenever {
                      once("github:merge") {
                          act("github:merge", "merge_method" to "squash") {}
                      }
                  }
              }
          }
      }
      """
    Then the script finished
    And the run noted
      | note |

  Scenario: holds says whether facts hold where the script stands, as a watch's checks do, and stops nothing
    spec/PLAN-LISTENERS.md, slice 8: the script a decided watch amounts to
    checks as the watch did. A fact names a member of the state, or a
    relation embedded, its value among the names of what is embedded, or a
    relation linked to, its value words a link's title says. Unlike check,
    a fact that fails stops nothing: holds says false, and the script goes on.
    Given "acme/rockets" has these issues on the small GitHub
      | number | title                          | state | author |
      | 11     | Bench 2 fell to 4 °C overnight | open  | ada    |
      | 12     | Damping-off on tray 14         | open  | ada    |
    And issue 11 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 12 of "acme/rockets" is labelled "alert" on the small GitHub
    And issue 11 of "acme/rockets" has these comments on the small GitHub
      | id | author | body                     |
      | 1  | ada    | Seen by the bench watch. |
    When this script runs with ARTIE
      """kotlin
      import kotlin.time.Duration.Companion.seconds

      heart("https://github.gateway.test/") {
          var looks = 0
          act("github:issues-list-for-repo", "owner" to "acme", "repo" to "rockets", "state" to "open", "labels" to "alert") {
              watch(every = 10.seconds, until = { ++looks == 2 }) {
                  whenever {
                      if (holds(mapOf("github:labels" to listOf("alert"), "state" to "open"))) {
                          var said = false
                          follow("github:issues-list-comments") { said = holds(mapOf("item" to "Seen by the bench watch.")) }
                          if (!said) act("github:comment", "body" to "Seen by the bench watch.") {}
                      }
                  }
              }
          }
      }
      """
    Then the script finished
    And issue 11 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
    And issue 12 of "acme/rockets" now has these comments on the small GitHub
      | author | body                     |
      | ada    | Seen by the bench watch. |
