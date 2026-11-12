Feature: A goal that crosses parties and waits on their answers: At, asking to be told, Once
  A release's goal says, in a person's words, where to go and what to wait for. `At {party}, …`
  goes to that party's entry and does the rest of the step there. A step that asks to be told
  (`ask to be told …`, `asking to be told …`), named `as NAME`, makes its act with a reply the
  host keeps: the act carries the reply in its envelope, and the reply is kept by that name.
  `Once {NAME} is answered, …` waits on that reply, with no model, then does the rest of the step
  holding it. The run is the planner's once; artie generate keeps each of these as the script's own
  step (`enter`, `ask`, `once-answered`), so every run after is the script, with no model.

  Background:
    Given the offer watch and the desk's replies run in this process, the watch answering through ARTIE
    And the file "watch.goal.md" is
      """
      ---
      start: "{desk}"
      inputs:
        desk:
        watcher:
      ---

      Wait until the desk offers to release, then go to it.

      - At {watcher}, ask to be told when {desk} offers desk:release `as watch`
      - Once {watch} is answered, follow it to the resource it watched `as watched`
      """

  Scenario: A step runs the script a resource links, on another resource it links, with no model
    A build links the smoke script it archived and the deployment it made: `Run the script it links
    as REL, on the deployment it links as REL, with NAME = {value}, … as NAME` runs that script on
    that deployment, as written, and keeps its outcome by the step's name, so later steps read it.
    The script made from the run does the same.
    Given the desk serves a build that links its smoke script and its deployment
    And the file "smoke-run.goal.md" is
      """
      ---
      start: "{build}"
      inputs:
        build:
        book: { default: "Matilda" }
      ---

      Smoke test what the build made.

      - Run the script it links as ci:smoke-test, on the deployment it links as ci:deployment, with book = {book} `as smoke`
      """
    When the goal "smoke-run.goal.md" runs with ARTIE in the desk, captured in "smoke-run.trace.yaml", given
      | name  | value                              |
      | build | http://localhost:8160/test-build   |
    Then the run finished
    And the run kept "smoke" with "passed" true
    When artie generated "smoke-run.heart.yaml" as data from "smoke-run.trace.yaml" and "smoke-run.goal.md"
    Then the script "smoke-run.heart.yaml" has a line with "- run-script: ci:smoke-test"
    And the script "smoke-run.heart.yaml" has a line with "book: ${book}"
    And the script "smoke-run.heart.yaml" has no line with "act: library"

  Scenario: The goal runs once with the planner, and the script made from it runs with none
    Given the planner will move
      | move | words                                                          | opens |
      | act  | ask to be told when http://localhost:8160/ offers desk:release | yes   |
      | done |                                                                |       |
      | act  | follow it to the watch                                         | yes   |
      | act  | follow it to the resource it watched                           | yes   |
      | done |                                                                |       |
    And ARTIE's reasoner will choose, in order
      | form          | follow         | fields                                                           |
      | watcher:watch |                | {"resource": "http://localhost:8160/", "offers": "desk:release"} |
      |               | hr:in-reply-to |                                                                  |
      |               | about          |                                                                  |
    When the goal "watch.goal.md" runs with ARTIE in the desk, captured in "watch.trace.yaml", given
      | name    | value                  |
      | desk    | http://localhost:8160/ |
      | watcher | http://localhost:8099/ |
    Then the run finished
    And the watch was asked with the desk's reply, signed by the desk
    When artie generated "watch.heart.yaml" as data from "watch.trace.yaml" and "watch.goal.md"
    Then the script "watch.heart.yaml" has a line with "- enter: ${watcher}"
    And the script "watch.heart.yaml" has a line with "- ask: watcher:watch"
    And the script "watch.heart.yaml" has a line with "reply: watch"
    And the script "watch.heart.yaml" has a line with "- once-answered:"
    When "watch.heart.yaml" runs with ARTIE in the desk, given
      | name    | value                  |
      | desk    | http://localhost:8160/ |
      | watcher | http://localhost:8099/ |
    Then the replay finished, having followed "about" to "http://localhost:8160/", with no model
