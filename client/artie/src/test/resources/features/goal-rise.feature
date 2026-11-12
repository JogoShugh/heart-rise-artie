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
