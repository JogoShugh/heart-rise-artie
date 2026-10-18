Feature: A script's reason step: a model chooses one selection from what is offered
  spec/PLAN-ARTIE-SCRIPTS.md. reason("…") hands the words to the client's
  reasoner, which chooses one selection from what the held representation
  offers: a form with its fields, or a link. The client checks the choice
  and makes it, and the script holds what it led to, as after any act or
  follow. The trace records it as an intent step: the words as said, and
  the choice as its values. A run allows it only when told to.

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
    And the reasoner chooses
      | intent                       | selection | relation | fields                       |
      | thank them and say it's done | act       | comment  | {"body": "Thanks, fixed."}   |
      | we're done with this one     | act       | close    | {}                           |

  Scenario: The reasoner's choice is made, and traced as an intent
    When this script runs, allowed a reasoner
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          reason("thank them and say it's done") {
              check(state["/title"] == "Fixed in 1.2.")
          }
          reason("we're done with this one")
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                           |
      | enter  | scripted://service/            |
      | act    | issues-get {"number":10}       |
      | reason | thank them and say it's done   |
      | act    | comment {"body":"Thanks, fixed."} |
      | return | /issues/10                     |
      | reason | we're done with this one       |
      | act    | close                          |
    And the trace has these steps
      | step | say                          | outcome      | at         |
      | 1    | enter {entry}                | done         | /          |
      | 2    | act {relation}, number {number} | done      | /issues/10 |
      | 3    | thank them and say it's done | new resource | /c/502     |
      | 4    | check                        | held         | /c/502     |
      | 5    | return to {address}          | done         | /issues/10 |
      | 6    | we're done with this one     | done         | /issues/10 |
    And trace step 3 is an intent, whose values are
      | name     | value          |
      | relation | comment        |
      | body     | Thanks, fixed. |

  Scenario: A run not allowed a reasoner stops at the reason step
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          reason("we're done with this one")
      }
      """
    Then the script stopped at step 2, "reason": "reason(…) needs a reasoner, and this run has none"
