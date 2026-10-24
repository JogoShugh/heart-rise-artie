Feature: Each step records the affordance it used, and what was offered where it chose
  spec/PLAN-LINE-BY-LINE-REPORT.md, step 2. A run report shows, for each
  line, the form or link chosen and the values passed; a script generator and
  a replay need the same, and the identities a replay guards on
  (spec/ideas/replay-guards-lenient-strict.md). The client knows them, since
  it made the request: heart-script records what the client says it used,
  and what the representation it chose from offered. Values sent are the
  step's own; a field the form asks for and the step left out is "not sent".

  Background:
    Given a service holds these resources
      | address  | title  | state |
      | /        | Garden |       |
      | /beds/1  | Bed 1  |       |
    And these forms
      | at      | relation | leads to | outcome |
      | /       | bed-get  | /beds/1  | done    |
      | /beds/1 | water    | /beds/1  | done    |
    And these links
      | at | relation | title    | to      |
      | /  | beds     | All beds | /beds/1 |
    And the form "water" at "/beds/1" asks for these fields
      | field       |
      | milliliters |
      | depthCm     |

  Scenario: An act records the form it used, its guard, and what was offered
    When this script runs, written to a trace
      """kotlin
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          act("water", "milliliters" to 250)
      }
      """
    Then the script finished
    And trace step 3 used
      | what     | value                 |
      | kind     | form                  |
      | relation | water                 |
      | method   | POST                  |
      | target   | /beds/{id}/actions/water |
      | address  | /beds/1/actions/water |
      | not sent | depthCm               |
    And trace step 3's guard is
      | what     | value        |
      | form     | sha256-water |
      | if-match | "/beds/1"    |
    And trace step 3 was offered
      | kind | relation |
      | form | water    |

  Scenario: A step keeps the exchanges the client made for it, when the client keeps them
    Given the client keeps its exchanges
    When this script runs, written to a trace
      """kotlin
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          act("water", "milliliters" to 250)
      }
      """
    Then the script finished
    And trace step 3 kept these exchanges
      | method | url                             | status | request body         |
      | POST   | scripted://service/beds/1/actions/water | 200    | {"milliliters":250}  |

  Scenario: A follow records the link it used
    When this script runs, written to a trace
      """kotlin
      heart("scripted://service/") {
          follow("beds", title = "All beds")
      }
      """
    Then the script finished
    And trace step 2 used
      | what     | value   |
      | kind     | link    |
      | relation | beds    |
      | method   | GET     |
      | target   | /beds/1 |
      | address  | /beds/1 |
    And trace step 2 was offered
      | kind | relation |
      | form | bed-get  |
      | link | beds     |
