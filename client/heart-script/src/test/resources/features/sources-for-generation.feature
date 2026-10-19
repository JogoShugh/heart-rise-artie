Feature: Value sources a script generator can bind to, without guessing
  spec/PLAN-SCRIPT-GENERATOR.md, "Capture changes first". A generated script
  reads a value again from the step that brought it into the run, so only a
  step that selected something is a source, the earliest first: never a
  check, an until, or a block end's re-read of what was already held. A
  check's facts are what it tested, not values it sent. Embedded resources'
  state is searched too, by pointer into _embedded. Words match whatever their
  case and ending; a value that only paraphrases them is marked so, and is
  still the words: no judgement, so a generator writes it as the constant it
  was (decided 2026-10-18: as few reason steps as there can be). Only a value
  the words don't give at all is the reasoner's judgement. Each value keeps
  its type from the form's schema; each
  input keeps its default and its question as the script declared them.

  Background:
    Given a service holds these resources
      | address    | title  | state |
      | /          | Garden |       |
      | /beds/1    | Bed 1  |       |
      | /users/ada | Ada    |       |
    And these forms
      | at      | relation | leads to | outcome |
      | /       | bed-get  | /beds/1  | done    |
      | /beds/1 | sow      | /beds/1  | done    |
    And the resource at "/beds/1" holds
      """
      {"label": "Herbs", "number": 7}
      """
    And the resource at "/users/ada" holds
      """
      {"login": "ada"}
      """
    And these embedded resources
      | at      | relation | resource   |
      | /beds/1 | gardener | /users/ada |
    And the form "sow" at "/beds/1" asks for these fields, typed
      | field    | type    |
      | seedType | string  |
      | depthCm  | integer |
      | gardener | string  |
      | method   | string  |

  Scenario: Only the step that brought a value in is its source; checks are no values; embedded state is searched
    When this script runs, written to a trace
      """kotlin
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          check("label" eq "Herbs")
          sync()
          act("sow", "seedType" to "basil", "depthCm" to 7, "gardener" to "ada")
      }
      """
    Then the script finished
    And trace step 3 sends no values
    And trace step 5's values came from
      | field    | value | from                             |
      | seedType | basil | constant                         |
      | depthCm  | 7     | step 2 /number                   |
      | gardener | ada   | step 2 /_embedded/gardener/0/login |
    And trace step 5's values are typed
      | field    | type    |
      | seedType | string  |
      | depthCm  | integer |
      | gardener | string  |

  Scenario: Words match whatever their case and ending; a paraphrase is marked, and is no judgement
    Given the reasoner chooses
      | intent                    | selection | relation | fields                                |
      | sow basil, pressing it in | act       | sow      | {"seedType": "Basil", "method": "press"} |
    When this script runs, allowed a reasoner
      """kotlin
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          act(intent = "sow basil, pressing it in")
      }
      """
    Then the script finished
    And trace step 3's values came from
      | field    | value | from       |
      | seedType | Basil | paraphrase |
      | method   | press | paraphrase |
    And trace step 3 is no judgement

  Scenario: Each input keeps how the script declared it
    Given the script is
      """kotlin
      val crop by input(default = "basil")
      val depth by input(question = "How deep?")
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          act("sow", "seedType" to crop, "depthCm" to depth)
      }
      """
    When the script runs unattended, given
      | input | value |
      | depth | 2     |
    Then the script finished
    And the run's inputs are declared as
      | input | value | given   | default | question  |
      | crop  | basil | default | basil   |           |
      | depth | 2     | given   |         | How deep? |
