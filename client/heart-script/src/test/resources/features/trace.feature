Feature: A script's run, written as a trace for its run report
  spec/PLAN-ARTIE-SCRIPTS.md and the heart-run-report skill. Every request a
  script makes is a step of its trace, in order, with when it began and how
  long it took: what was asked, as a sentence with its values marked, the
  outcome by meaning, where the client then was, and what it held there.
  Checks are steps too. A stop is the last step, with its reason. The trace
  names no protocol: a binding's own exchange may be added as evidence.

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

  Scenario: Each request and check is a step, in order
    When this script runs, written to a trace
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act("comment", "body" to "Fixed in 1.2.") {
              check(state["/title"] == "Fixed in 1.2.")
          }
          act("close")
      }
      """
    Then the trace has these steps
      | step | say                              | outcome      | at         |
      | 1    | enter {entry}                    | done         | /          |
      | 2    | act {relation}, number {number}  | done         | /issues/10 |
      | 3    | act {relation}, body {body}      | new resource | /c/502     |
      | 4    | check                            | held         | /c/502     |
      | 5    | return to {address}              | done         | /issues/10 |
      | 6    | act {relation}                   | done         | /issues/10 |
    And trace step 2's values are
      | name     | value      |
      | relation | issues-get |
      | number   | 10         |
    And trace step 2 holds 2 data fields, 2 forms and 0 links
    And every trace step has when it began and how long it took

  Scenario: A stop is the trace's last step, with its reason
    When this script runs, written to a trace
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act("reopen", "reason" to "still broken")
      }
      """
    Then the trace has these steps
      | step | say                             | outcome | at         |
      | 1    | enter {entry}                   | done    | /          |
      | 2    | act {relation}, number {number} | done    | /issues/10 |
      | 3    | act {relation}, reason {reason} | refused | /issues/10 |
    And trace step 3's values are
      | name     | value        |
      | relation | reopen       |
      | reason   | still broken |
    And trace step 3 was refused as "not offered", by "the client": "The issue is already open."
