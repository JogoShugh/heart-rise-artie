Feature: An intent step reaches an act not offered yet by the acts offered toward it
  exhibits/mcp-over-openapi, the notice change. The service starts asking
  for notice before a dismissal: "dismiss him" is one act no longer, and
  dismiss is not offered until notice is given. An intent step made one
  act, so it stopped with the server's reason. Now, when what the words
  ask is not offered, the reasoner is asked again, told the server's
  reason, to choose an act offered now that is a step toward it. The step
  holds what that act led to and asks the person's words again there. If
  nothing offered leads toward it, the reasoner asks, or explains again,
  and the step stops as before: words that only ask why are never acted
  on. At most three acts toward the words; then the refusal stops it.

  Background:
    Given a service holds these resources
      | address                 | title         | state     |
      | /                       | Staff         |           |
      | /people/bilbo           | Bilbo Baggins | working   |
      | /people/bilbo-on-notice | Bilbo Baggins | on notice |
      | /people/bilbo-gone      | Bilbo Baggins | dismissed |
    And these forms
      | at                      | relation    | leads to                | outcome |
      | /                       | find        | /people/bilbo           | done    |
      | /people/bilbo           | give-notice | /people/bilbo-on-notice | done    |
      | /people/bilbo           | note        | /people/bilbo           | done    |
      | /people/bilbo-on-notice | dismiss     | /people/bilbo-gone      | done    |
    And the server says why "dismiss" is not offered: "Notice must be given first."
    And the reasoner chooses, by the state it holds
      | intent      | holding   | selection | relation | fields |
      | dismiss him | working   | act       | dismiss  | {}     |
      | dismiss him | on notice | act       | dismiss  | {}     |
      | retire him  | working   | act       | dismiss  | {}     |
      | let him go  | working   | act       | dismiss  | {}     |
    And the reasoner, told the words are not offered yet, chooses
      | intent      | holding | selection | relation    | fields |
      | dismiss him | working | act       | give-notice | {}     |
      | let him go  | working | act       | note        | {}     |
    And the reasoner, told the words are not offered yet, asks
      | intent     | holding | question                                                 |
      | retire him | working | Nothing offered here retires him; give notice first?     |

  Scenario: Not offered yet, the act toward it is made, then the words are asked again
    When this script runs, allowed a reasoner
      """kotlin
      heart("scripted://service/") {
          act("find")
          act(intent = "dismiss him")
      }
      """
    Then the script finished
    And the client was asked, in order
      | call   | what                |
      | enter  | scripted://service/ |
      | act    | find                |
      | reason | dismiss him         |
      | reason | dismiss him         |
      | act    | give-notice         |
      | reason | dismiss him         |
      | act    | dismiss             |
    And the reasoner was told, besides the words, in order
      | told                                                                                                                                                                                  |
      | What these words ask is not offered here now: Notice must be given first. If an act offered here now is a step toward it, choose that act; these words are asked again after it. If none is, ask. |

  Scenario Outline: How an intent step not offered yet ends: <case>
    When this script runs, allowed a reasoner
      """kotlin
      heart("scripted://service/") {
          act("find")
          act(intent = "<intent>")
      }
      """
    Then <ended>

    Examples:
      | case                                          | intent      | ended                                                                                                                        |
      | an act offered leads to it: done              | dismiss him | the script finished                                                                                                          |
      | nothing offered leads to it: it asks          | retire him  | the script stopped at step 2, "intent": unclear to the reasoner, refused by the client: "Nothing offered here retires him; give notice first?" |
      | three acts toward it, still not offered: stop | let him go  | the script stopped at step 2, "intent": not offered, refused by the server: "Notice must be given first."                   |
