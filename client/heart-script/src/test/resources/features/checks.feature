Feature: Checks as facts: a member and a value, or a member and a matcher
  spec/ideas/script-checks-and-reason-blocks.md, decided 2026-10-18. A check may
  name facts about the state the script holds: a member and the value it should
  have, or a member and a matcher, a test that carries its own words. Every fact
  is tested; a failure stops the script saying, for each fact that failed, what
  was expected and what was held. Numbers compare as numbers, whatever JSON
  number they arrived as. A member is named as it is; a leading slash makes it a
  JSON Pointer, for nested state.

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
      {"label": "Herbs", "status": "PLANTED", "moisture": 40, "ratio": 0.75,
       "labels": ["herb", "sunny"], "closed_at": null, "plot": {"soil": "loam"}, "a/b": "slashed"}
      """

  Scenario: Facts that hold, in every form, and the script goes on
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          check("label" eq "Herbs", "moisture" above 0, "ratio" below 1, "moisture" eq 40)
          check("labels" has "herb", "label" has "Her", "status" oneOf listOf("PLANTED", "GROWING"))
          check("closed_at" to present, "missing" to absent, "/plot/soil" eq "loam", "a/b" eq "slashed")
          check("status" to "PLANTED", "moisture" to between(10, 50))
          check(mapOf("label" to "Herbs", "moisture" to atLeast(40), "ratio" to atMost(0.75)))
          check("label" matching Regex("H.+s"), "status" isNot "EMPTY")
      }
      """
    Then the script finished

  Scenario: Every fact that fails is told, with what was held
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          check("label" eq "Herbs", "moisture" above 50, "labels" has "shady", "missing" to present)
      }
      """
    Then the script stopped at step 2, "check": "moisture: expected above 50, was 40; labels: expected to contain shady, was [herb, sunny]; missing: expected present, was absent"

  Scenario: A member is named as it is; a leading slash reads nested state
    When this script runs
      """kotlin
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          note("${state["label"]}, ${state["/label"]}, ${state["/plot/soil"]}, ${state["a/b"]}")
      }
      """
    Then the script noted "Herbs, Herbs, loam, slashed"

  Scenario: The trace keeps each fact as data
    When this script runs, written to a trace
      """kotlin
      heart("scripted://service/") {
          act("bed-get", "id" to 1)
          check("label" eq "Herbs", "moisture" above 0)
      }
      """
    Then trace step 3 checked these facts
      | member   | test        | held  | holds |
      | label    | eq Herbs    | Herbs | true  |
      | moisture | above 0     | 40    | true  |
