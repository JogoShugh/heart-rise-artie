Feature: Each value a step sends says where it came from, and a reason step its template
  spec/PLAN-LINE-BY-LINE-REPORT.md, step 2. A script generator turns a run into
  a script: a value from the script's inputs becomes an input, a value read
  from an earlier answer is read again at run time, never written down, and
  only what is left is a constant. So the trace says, for every value sent,
  every source it has: an input with that value, a member of an earlier
  step's state (the nearest step first, by JSON Pointer), the reason step's
  words, or, when no whole source fits, the parts of it that do. A value with
  none is a constant. A reason step also keeps its template, the words as the
  script wrote them before Kotlin filled them in, and each slot's value and
  source. The run keeps its inputs, and how each was given.

  Background:
    Given a service holds these resources
      | address  | title  | state |
      | /        | Garden |       |
      | /beds/1  | Bed 1  |       |
    And these forms
      | at      | relation | leads to | outcome |
      | /       | bed-get  | /beds/1  | done    |
      | /beds/1 | sow      | /beds/1  | done    |
    And the resource at "/beds/1" holds
      """
      {"label": "Herbs", "number": 7}
      """

  Scenario: A script step's values: an input, a part of an earlier answer, a constant
    Given the script is
      """kotlin
      val crop by input()
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          act("sow", "seedType" to crop, "note" to "bed #${state["number"]}", "depthCm" to 2)
      }
      """
    When the script runs unattended, given
      | input | value |
      | crop  | basil |
    Then the script finished
    And trace step 3's values came from
      | field    | value  | from                         |
      | seedType | basil  | input crop                   |
      | note     | bed #7 | parts: 7 from step 2 /number |
      | depthCm  | 2      | constant                     |
    And the run's inputs are
      | input | value | given |
      | crop  | basil | given |

  Scenario: A reason step's values, and its template with each slot's source
    Given the reasoner chooses
      | intent                                  | selection | relation | fields                               |
      | sow basil about 2 cm deep in bed 7      | act       | sow      | {"seedType": "basil", "depthCm": "2"} |
    And the script is
      """kotlin
      val crop by input()
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          reason("sow $crop about 2 cm deep in bed ${state["number"]}")
      }
      """
    When the script runs unattended, allowed a reasoner, given
      | input | value |
      | crop  | basil |
    Then the script finished
    And trace step 3's values came from
      | field    | value | from              |
      | seedType | basil | input crop, words |
      | depthCm  | 2     | words             |
    And trace step 3's reason template is 'sow $crop about 2 cm deep in bed ${state["number"]}', with these slots
      | slot            | value | from           |
      | crop            | basil | input crop     |
      | state["number"] | 7     | step 2 /number |
