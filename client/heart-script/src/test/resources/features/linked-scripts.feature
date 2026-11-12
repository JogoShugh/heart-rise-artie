Feature: A script enters other parties, and runs the scripts their links hand over
  A release crosses parties, each its own service with its own entry: `enter` goes to another
  party's entry and runs its steps there, then comes back. A party can hand over a kept script by
  link, as a build links the smoke test it archived: `run-script` reads the script the link leads
  to, runs it with the inputs given, and keeps its outcome by name. A smoke test is scenarios:
  `scenario` runs its steps, and a check that fails there fails the scenario, not the script, so
  every scenario runs and the outcome says which passed. An address a party hands over in a
  result's links may be read, as any link's.

  Background:
    Given a service holds these resources
      | address   | title   | state     |
      | /         | Builds  |           |
      | /builds/1 | Build 1 | succeeded |
      | /preview  | Preview |           |
    And these links
      | at        | relation      | title   | to              |
      | /         | item          | Build 1 | /builds/1       |
      | /builds/1 | ci:smoke-test |         | /builds/1/smoke |

  Scenario: A script runs the kept script a link hands over, and keeps which scenarios passed
    Given the server answers, in order
      | method | url             | status | body                                                                                                                                                                                                       |
      | GET    | /builds/1/smoke | 200    | {heart: 1, kind: kept, inputs: {deployed: {}}, start: "${deployed}", steps: [{scenario: S1, steps: [{check: [{field: title, is: Builds}]}]}, {scenario: S2, steps: [{check: [{field: title, is: Gone}]}]}]} |
    When this script held as data runs
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - follow: item
          title: Build 1
        - run-script: ci:smoke-test
          inputs: {deployed: "scripted://service/preview"}
          as: smoke
        - note: "passed: ${smoke.passed}; failed: ${smoke.failed}"
      """
    Then the script finished
    And the script noted "passed: false; failed: S2"
    And the client was asked, in order
      | call   | what                                    |
      | enter  | scripted://service/                     |
      | follow | item "Build 1"                          |
      | send   | GET /builds/1/smoke                     |
      | enter  | scripted://service/preview              |
      | return | /builds/1                               |

  Scenario: A script enters another party's entry, runs its steps there, and comes back
    When this script held as data runs
      """yaml
      heart: 1
      kind: kept
      start: scripted://service/
      steps:
        - follow: item
          title: Build 1
        - enter: scripted://service/preview
          steps:
            - note: "at ${state.title}"
        - note: "back at ${state.title}"
      """
    Then the script finished
    And the script noted, in order
      | note             |
      | at Builds        |
      | back at Build 1  |
