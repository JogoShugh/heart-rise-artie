Feature: A script's inputs and questions
  spec/PLAN-ARTIE-SCRIPTS.md, "Inputs and questions". Scripts mostly run
  unattended: nothing asks a person unless the run is interactive, and
  whether a run can go unattended is known before its first request.
  Inputs are declared at the top; an inline question carries a default.

  Background:
    Given a service holds these resources
      | address    | title                 | state |
      | /          | Repositories          |       |
      | /issues/10 | Login fails on Safari | open  |
    And these forms
      | at         | relation   | leads to   | outcome |
      | /          | issues-get | /issues/10 | done    |
      | /issues/10 | close      | /issues/10 | done    |
    And the script is
      """kotlin
      val number by input()
      val comment by input(default = "Fixed in 1.2.")
      val reason by input(question = "Reason to close?")

      heart("scripted://service/") {
          act("issues-get", "number" to number)
          note("comment: $comment")
          act("close", "state_reason" to reason)
          note("label: " + ask("Label?", default = "triage"))
      }
      """

  Scenario Outline: What a run with <given> does, <mode>
    When the script runs <mode>, given
      | input  | value   |
      | <name> | <value> |
    Then <outcome>

    Examples:
      | given                    | mode          | name   | value     | outcome                                                                                |
      | only number              | unattended    | number | 10        | the script stopped before its first request: "reason needs --interactive or a value"   |
      | only reason              | unattended    | reason | completed | the script stopped before its first request: "missing input: number"                   |

  Scenario: Every input given: the run goes unattended, defaults where none was given
    When the script runs unattended, given
      | input  | value     |
      | number | 10        |
      | reason | completed |
    Then the script finished
    And the client was asked, in order
      | call  | what                                    |
      | enter | scripted://service/                     |
      | act   | issues-get {"number":"10"}              |
      | act   | close {"state_reason":"completed"}      |
    And the script noted, in order
      | note                    |
      | comment: Fixed in 1.2.  |
      | label: triage           |
    And the run says these defaults were used
      | question | default |
      | Label?   | triage  |

  Scenario: Interactive: missing inputs are asked before the run, questions when first read, inline questions when reached
    When the script runs interactively, given
      | input  | value |
      | number | 10    |
    And the person answers, in order
      | asked            | answer        |
      | Reason to close? | not_planned   |
      | Label?           | needs-info    |
    Then the script finished
    And the client was asked, in order
      | call  | what                                    |
      | enter | scripted://service/                     |
      | act   | issues-get {"number":"10"}              |
      | act   | close {"state_reason":"not_planned"}    |
    And the script noted, in order
      | note                    |
      | comment: Fixed in 1.2.  |
      | label: needs-info       |

  Scenario: The script's inputs, listed without running it
    When the script's inputs are listed
    Then they are
      | input   | kind     | default       | question         |
      | number  | required |               |                  |
      | comment | optional | Fixed in 1.2. |                  |
      | reason  | question |               | Reason to close? |
    And the client was asked nothing
