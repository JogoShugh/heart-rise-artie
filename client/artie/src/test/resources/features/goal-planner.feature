Feature: Claude plans a goal run, one move at a time
  spec/PLAN-PLAIN-TEXT-RUN.md, "A planning loop". The planner on the Claude
  Messages API, through ARTIE's own transport, like the Reason step: one
  "move" tool, a system prompt and tool that never change (a cached prefix),
  and one user message with what changes: the goal sentence by sentence,
  each marked done or not; the inputs, by name; the moves made so far; the
  path of resources the run is in; and what is held, its state, the forms it
  offers and its links. An answer in words alone is a question for the
  person, never a move.

  Background:
    Given the planner is Claude, through the scripted Claude API
    And the planner holds "https://github.gateway.test/repos/acme/rockets/issues/10" with the state {"number": 10, "state": "open"}, offering
      | form           |
      | github:comment |
      | github:close   |
    And the goal is "Open issue $number of $owner/$repo. Close it." with the input number = 10, the first sentence done

  Scenario Outline: The model's tool call is the move: <move>
    Given the Claude API will answer with the move <input>
    When the planner is asked its next move
    Then the move is <move>

    Examples:
      | input                                                                    | move                                  |
      | {"move": "act", "words": "close it", "sentence": 2, "opens": false}     | act "close it" for sentence 2         |
      | {"move": "act", "words": "comment: done", "sentence": 2, "opens": true} | act "comment: done" for sentence 2, opening a block |
      | {"move": "back", "to": "the issue"}                                      | back to "the issue"                   |
      | {"move": "wait", "relation": "github:close"}                             | wait until "github:close" is offered  |
      | {"move": "check", "facts": {"state": "closed"}}                          | check {state=closed}                  |
      | {"move": "done", "sentence": 2}                                          | sentence 2 done                       |
      | {"move": "finish"}                                                       | finished                              |
      | {"move": "stop", "why": "the issue is locked"}                           | stopped: the issue is locked          |
      | {"move": "clarify", "question": "Which issue?"}                          | a question: Which issue?              |

  Scenario: An answer in words alone is a question for the person, never a move
    Given the Claude API will answer with the text "Should I close it as completed or as not planned?"
    When the planner is asked its next move
    Then the move is a question: Should I close it as completed or as not planned?

  Scenario: The model is shown the goal by sentence, the inputs, the path and what is held
    Given the Claude API will answer with the move {"move": "finish"}
    When the planner is asked its next move
    Then the planner's request says
      | part                                                             |
      | 1. [done] Open issue 10 of acme/rockets.                          |
      | 2. [ ] Close it.                                                  |
      | $number = 10                                                      |
      | https://github.gateway.test/repos/acme/rockets/issues/10          |
      | github:comment                                                    |
      | github:close                                                      |
      | "state":"open"                                                    |
    And the planner's request caches its system prompt and tool
