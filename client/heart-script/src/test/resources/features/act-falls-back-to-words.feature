Feature: An act that carries its words falls back to them when the form it was written for no longer holds
  spec/ideas/replay-guards-lenient-strict.md: "act(rel, …, intent = …) is
  pinned. It runs with no model while its pin holds, and falls back to its
  intent words when it doesn't." A kept script runs with no model while the
  service stays as it was recorded. When the service changes under it (a
  form gone, a form whose pin no longer holds, values that no longer fit),
  the client refuses the act before anything is sent. With the run set to
  fall back to the words (on-change: intent), that step is handed to the
  reasoner as an intent step instead, chosen from what is offered now: the
  model is called only where the service changed. An act with no words, or
  a run not set to fall back, stops as before.

  Background:
    Given a service holds these resources
      | address    | title                 | state  |
      | /          | Repositories          |        |
      | /issues/10 | Login fails on Safari | open   |
    And these forms
      | at         | relation    | leads to   | outcome |
      | /          | issues-get  | /issues/10 | done    |
      | /issues/10 | close-issue | /issues/10 | done    |
    And the reasoner chooses
      | intent   | selection | relation    | fields |
      | close it | act       | close-issue | {}     |

  Scenario Outline: A form no longer offered: <case>
    When this script runs, allowed a reasoner, falling back to its words: <falls back>
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          <act>
      }
      """
    Then <ended>

    Examples:
      | case                                 | falls back | act                               | ended                                                                                     |
      | the act carries its words: falls back | yes        | act("close", intent = "close it") | the script finished                                                                       |
      | the run doesn't fall back: it stops  | no         | act("close", intent = "close it") | the script stopped at step 2, "close": not offered, refused by the client: "no reason given" |
      | the act has no words: it stops       | yes        | act("close")                      | the script stopped at step 2, "close": not offered, refused by the client: "no reason given" |

  Scenario: Falling back, the reasoner chooses from what is offered now, and the act made is the new one
    When this script runs, allowed a reasoner, falling back to its words: yes
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act("close", intent = "close it")
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                     |
      | enter  | scripted://service/      |
      | act    | issues-get {"number":10} |
      | reason | close it                 |
      | act    | close-issue              |

  Scenario: A form the lock file no longer holds falls back to the words too
    Given these forms
      | at         | relation | leads to   | outcome |
      | /issues/10 | close    | /issues/10 | done    |
    And the client will refuse "close" at "/issues/10" by its guard: "the form changed since the script was recorded"
    When this script runs, allowed a reasoner, falling back to its words: yes
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act("close", intent = "close it")
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                     |
      | enter  | scripted://service/      |
      | act    | issues-get {"number":10} |
      | act    | close                    |
      | reason | close it                 |
      | act    | close-issue              |
