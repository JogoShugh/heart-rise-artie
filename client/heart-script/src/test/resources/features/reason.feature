Feature: A script's reason step: a model chooses one selection from what is offered
  spec/PLAN-ARTIE-SCRIPTS.md. act(intent = "…") hands the words to the client's
  reasoner, which chooses one selection from what the held representation
  offers: a form with its fields, or a link. The client checks the choice
  and makes it, and the script holds what it led to, as after any act or
  follow. The trace records it as an intent step: the words as said, and
  the choice as its values. A run allows it only when told to. An act given
  both a relation and an intent makes the act as written, and keeps the
  words in the trace, so a script pinned by artie generate says what each
  act was for (spec/ideas/replay-guards-lenient-strict.md).

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
    And these links
      | at         | relation | title | to     |
      | /issues/10 | comments |       | /c/502 |
    And the reasoner chooses
      | intent                       | selection | relation | fields                       |
      | thank them and say it's done | act       | comment  | {"body": "Thanks, fixed."}   |
      | we're done with this one     | act       | close    | {}                           |

  Scenario: The reasoner's choice is made, and traced as an intent
    When this script runs, allowed a reasoner
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act(intent = "thank them and say it's done") {
              check(state["/title"] == "Fixed in 1.2.")
          }
          act(intent = "we're done with this one")
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

  Scenario: An act or a follow with its intent is made as written, with no reasoner, and keeps its words
    When this script runs, written to a trace
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10) {
              follow("comments", intent = "read what they said") {}
              act("close", intent = "we're done with this one")
          }
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                     |
      | enter  | scripted://service/      |
      | act    | issues-get {"number":10} |
      | follow | comments                 |
      | return | /issues/10               |
      | act    | close                    |
      | return | /                        |
    And trace step 3 keeps the intent "read what they said"
    And trace step 5 keeps the intent "we're done with this one"

  Scenario: Words a model inferred are kept apart from a person's intent, and reasoned on alike
    An intent is a person's words, written in a goal or a script. Words a
    model wrote while it ran, its reading of a goal's line, are inferred:
    worth keeping beside the act, for a reader and for the reasoner, but not
    the person's. A script says which by its argument; the trace keeps them
    apart. With no relation, inferred words are reasoned on, as an intent is.
    A model's words may say which of a person's they serve: fromIntent, kept
    beside them as where they came from, never reasoned on alone.
    When this script runs, allowed a reasoner
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10, inferred = "get issue 10", fromIntent = "On issue 10") {
              act("close", intent = "we're done with this one")
          }
          act(inferred = "we're done with this one")
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                     |
      | enter  | scripted://service/      |
      | act    | issues-get {"number":10} |
      | act    | close                    |
      | return | /                        |
      | reason | we're done with this one |
      | act    | close                    |
    And the trace's steps keep these words
      | step | intent                   | inferred     | fromIntent  |
      | 2    |                          | get issue 10 | On issue 10 |
      | 3    | we're done with this one |              |             |

  Scenario: A run not allowed a reasoner stops at the reason step
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act(intent = "we're done with this one")
      }
      """
    Then the script stopped at step 2, "intent": "act(intent = …) needs a reasoner, and this run has none"
