Feature: The trace keeps a run's shape: blocks, waits, how links were picked, judgement, and what later steps read
  spec/PLAN-LINE-BY-LINE-REPORT.md, step 2. A script generator nests its calls
  as the run did, waits where it waited, picks a link as it was picked, keeps
  a reason step where the reasoner chose something the words didn't say, and
  binds what a later step read from an earlier answer. So a step that opens a
  block says so; an until that held is a step, with what it waited for and how
  many reads it took; a followed link says whether it was picked by relation
  or by title; a reason step that chose a value its words didn't give is a
  judgement; and a step keeps the members of its state later steps read, with
  the steps that read them.

  Background:
    Given a service holds these resources
      | address    | title  | state |
      | /          | Garden |       |
      | /beds/1    | Bed 1  |       |
      | /issues/10 | Issue  | open  |
    And these forms
      | at      | relation   | leads to   | outcome |
      | /       | bed-get    | /beds/1    | done    |
      | /       | issues-get | /issues/10 | done    |
      | /beds/1 | sow        | /beds/1    | done    |
    And these links
      | at | relation | title    | to      |
      | /  | beds     | All beds | /beds/1 |
    And the resource at "/beds/1" holds
      """
      {"label": "Herbs", "number": 7}
      """

  Scenario: Blocks a step opens, and an until that waited
    Given "/issues/10" becomes "closed" after 2 more reads
    When this script runs, written to a trace
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10) {
              until(tries = 5) { state["/state"] == "closed" }
          }
      }
      """
    Then the script finished
    And the trace's steps are shaped
      | step | does   | line | opens | waited for                   | reads |
      | 1    | enter  | 1    | yes   |                              |       |
      | 2    | act    | 2    | yes   |                              |       |
      | 3    | return | 3    |       |                              |       |
      | 4    | return | 3    |       |                              |       |
      | 5    | until  | 3    |       | state["/state"] == "closed" | 2     |
      | 6    | return | 2    |       |                              |       |

  Scenario: How a link was picked, a judgement, and what later steps read
    Given the reasoner chooses
      | intent             | selection | relation | fields                               |
      | sow basil in bed 7 | act       | sow      | {"seedType": "basil", "depthCm": "3"} |
    When this script runs, allowed a reasoner
      """kotlin
      heart("scripted://service/") {
          follow("beds", title = "All beds")
          act(intent = "sow basil in bed ${state["number"]}")
      }
      """
    Then the script finished
    And trace step 2 picked its link by "title"
    And trace step 3 is a judgement
    And trace step 2 keeps these members for later steps
      | pointer | read by |
      | /number | 3       |

  Scenario: A reason step keeps what the model was asked and what it answered
    Given the reasoner chooses
      | intent             | selection | relation | fields                               |
      | sow basil in bed 7 | act       | sow      | {"seedType": "basil", "depthCm": "2"} |
    When this script runs, allowed a reasoner
      """kotlin
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          act(intent = "sow basil in bed ${state["number"]}")
      }
      """
    Then the script finished
    And trace step 3 keeps these model calls
      | model      | asked                      | answered   |
      | scripted-1 | intent: sow basil in bed 7 | chose: sow |
