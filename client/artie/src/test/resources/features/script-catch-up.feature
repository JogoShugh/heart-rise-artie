Feature: Before its guard refuses a form for its set, ARTIE reads again while the server is catching up
  An upstream behind a gateway may still be working a resource out when
  ARTIE reads it: GitHub works out a new pull request's mergeability in the
  background, and its replicas lag its writes. For a second or two, the
  resource offers its forms in a set its lock file never recorded (merge
  absent: "GitHub is still checking"), and its ETag is about to change.

  So where the form ARTIE is about to use is offered unchanged, but a strict
  lock file doesn't record its set, ARTIE reads the resource again, a few
  times, a moment apart, before refusing; and after a 412, when the form is
  still offered, it does the same before deciding again. Once the form is
  offered as the lock file holds it, it acts, with the new validator; after
  those few tries, it refuses, as before.

  A form not offered at all is refused at once, as before: whether an absence
  will pass is something HEART doesn't yet say other than in prose, and a
  client never acts on prose (SPEC §6.2). A long wait (CI checks that take
  minutes) is not catching up either: a script that means to wait says so.

  Background:
    Given a small GitHub knows these callers
      | token     | caller |
      | ada-token | ada    |
    And "acme/rockets" has pull request 482 on the small GitHub, open, titled "Fix the login bug"
    And ARTIE talks to the GitHub gateway at "https://github.gateway.test" in front of the small GitHub
    And ARTIE holds the Bearer credential "ada-token" for "https://github.gateway.test"
    And the client works at rung L2

  Scenario Outline: GitHub is still working out mergeability when ARTIE reads; it <settles>: <guard>, <relation>
    Given ARTIE guards the run "<guard>", pinning "<relation>" as pull request 482 offers it once GitHub has worked it out
    And GitHub is still working out whether pull request 482 can be merged, and <settles>
    When this script runs with ARTIE
      """kotlin
      heart("https://github.gateway.test/") {
          act("github:pulls-get", "owner" to "acme", "repo" to "rockets", "pull_number" to 482)
          act("<relation>"<fields>)
      }
      """
    Then the run <ends>

    Examples:
      | guard   | relation     | fields                         | settles                               | ends                                   |
      | strict  | github:edit  | , "title" to "Fix the login"   | works it out once the gateway read it | finished                               |
      | strict  | github:edit  | , "title" to "Fix the login"   | never works it out                    | was stopped at step 2 by its guard     |
      | lenient | github:merge |                                | works it out once the gateway read it | was stopped at step 2, as not offered  |
