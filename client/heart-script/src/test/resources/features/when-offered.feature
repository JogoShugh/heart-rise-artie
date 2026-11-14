Feature: when-offered takes the first case whose act is offered here, else otherwise
  spec/PLAN-COST-FOLLOWS-STATES.md. One goal over many resources in a few
  states: what each resource offers says its state, and the server decided
  it. A step that branches on the offer reads that decision and adds none of
  its own: the first case whose act the resource offers now is made, and
  where none is offered, otherwise. A Kotlin script says the same with
  whenOffered, and a script held as data runs through it, so the two
  behave alike.

  Background:
    Given a service holds these resources
      | address | title        | state     |
      | /       | The Millers  |           |
      | /e/1    | Anna Miller  | working   |
      | /e/2    | James Miller | on notice |
      | /e/3    | Maria Miller | dismissed |
    And these links
      | at | relation | title        | to   |
      | /  | item     | Anna Miller  | /e/1 |
      | /  | item     | James Miller | /e/2 |
      | /  | item     | Maria Miller | /e/3 |
    And these forms
      | at   | relation    | leads to | outcome |
      | /e/1 | give-notice | /e/1     | done    |
      | /e/2 | dismiss     | /e/2     | done    |

  Scenario: A script held as data acts on each member by what it offers, and notes the one that offers neither
    When this script held as data runs
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - whenever:
            - when-offered:
                - act: give-notice
                  intent: Give notice to anyone working
                - act: dismiss
                  intent: Dismiss anyone on notice
              otherwise:
                - note: nothing to do for ${state.title}
      """
    Then the script finished
    And the script noted "nothing to do for Maria Miller"
    And the client was asked, in order
      | call   | what                  |
      | enter  | scripted://service/   |
      | follow | item "Anna Miller"    |
      | act    | give-notice           |
      | return | /                     |
      | follow | item "James Miller"   |
      | act    | dismiss               |
      | return | /                     |
      | follow | item "Maria Miller"   |
      | return | /                     |

  Scenario: The same in Kotlin, with whenOffered
    When this script runs
      """kotlin
      heart("scripted://service/") {
          whenever {
              whenOffered(otherwise = { note("nothing to do for ${state["/title"]}") }) {
                  case("give-notice") { act("give-notice", intent = "Give notice to anyone working") }
                  case("dismiss") { act("dismiss", intent = "Dismiss anyone on notice") }
              }
          }
      }
      """
    Then the script finished
    And the script noted "nothing to do for Maria Miller"
    And the client was asked, in order
      | call   | what                  |
      | enter  | scripted://service/   |
      | follow | item "Anna Miller"    |
      | act    | give-notice           |
      | return | /                     |
      | follow | item "James Miller"   |
      | act    | dismiss               |
      | return | /                     |
      | follow | item "Maria Miller"   |
      | return | /                     |

  Scenario Outline: A member offering an act no case names falls back to the step's words, where the run falls back: <falls back>
    The script grows by use (spec/ideas/branching-from-intent-runs.md):
    a member in a state no case was written for, which offers an act, is
    the step's words, chosen by the reasoner from what it offers, where the
    run falls back to words (on-change: intent). A member offering nothing,
    or a run that doesn't fall back, takes otherwise, with no model.
    Given the reasoner chooses
      | intent                               | selection | relation | fields |
      | Move each Miller one step to leaving | act       | dismiss  | {}     |
    When this script held as data runs, allowed a reasoner, falling back to its words: <falls back>
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - whenever:
            - when-offered:
                - act: give-notice
                  intent: Give notice to anyone working
              intent: Move each Miller one step to leaving
              otherwise:
                - note: nothing to do for ${state.title}
      """
    Then the script finished
    And the client's acts and reasons, joined, are "<acts>"
    And the script's notes, joined, are "<notes>"

    Examples:
      | falls back | acts                                                                       | notes                                                         |
      | yes        | act give-notice; reason Move each Miller one step to leaving; act dismiss  | nothing to do for Maria Miller                                |
      | no         | act give-notice                                                            | nothing to do for James Miller; nothing to do for Maria Miller |

  Scenario Outline: The same in Kotlin: whenOffered falls back to its words, where the run falls back: <falls back>
    Given the reasoner chooses
      | intent                               | selection | relation | fields |
      | Move each Miller one step to leaving | act       | dismiss  | {}     |
    When this script runs, allowed a reasoner, falling back to its words: <falls back>
      """kotlin
      heart("scripted://service/") {
          whenever {
              whenOffered(
                  intent = "Move each Miller one step to leaving",
                  otherwise = { note("nothing to do for ${state["/title"]}") },
              ) {
                  case("give-notice") { act("give-notice", intent = "Give notice to anyone working") }
              }
          }
      }
      """
    Then the script finished
    And the client's acts and reasons, joined, are "<acts>"
    And the script's notes, joined, are "<notes>"

    Examples:
      | falls back | acts                                                                       | notes                                                         |
      | yes        | act give-notice; reason Move each Miller one step to leaving; act dismiss  | nothing to do for Maria Miller                                |
      | no         | act give-notice                                                            | nothing to do for James Miller; nothing to do for Maria Miller |
