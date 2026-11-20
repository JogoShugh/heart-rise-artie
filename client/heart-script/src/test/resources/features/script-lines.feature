Feature: Each step names the script line it came from, and the line of the block it is in
  spec/PLAN-LINE-BY-LINE-REPORT.md, step 1. A run report shows the script
  first, then what each line did; a script generator nests its calls as the
  run did. So every step of a run's trace names the line of the script that
  made it, and the line of the block it ran in. Lines are the script's own, as
  its author numbers them: the host's imports add none. A block's end, the
  return to where the block began, names the line that opened the block.

  Background:
    Given a service holds these resources
      | address  | title  | state |
      | /        | Garden |       |
      | /beds/1  | Bed 1  |       |
    And these forms
      | at | relation | leads to | outcome |
      | /  | bed-get  | /beds/1  | done    |
    And the resource at "/beds/1" holds
      """
      {"label": "Herbs"}
      """

  Scenario: A step's line, and the line of the block it ran in
    When this script runs, written to a trace
      """kotlin
      heart("scripted://service/") {
          act("bed-get", "id" to 1) {
              check("label" eq "Herbs")
          }
          act("bed-get", "id" to 1)
      }
      """
    Then the script finished
    And the trace's steps name these lines
      | step | does   | line | within |
      | 1    | enter  | 1    |        |
      | 2    | act    | 2    | 1      |
      | 3    | check  | 3    | 2      |
      | 4    | return | 2    | 1      |
      | 5    | act    | 5    | 1      |
