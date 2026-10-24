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

  Scenario: The model is shown what the held resource embeds, by name, and that a check may name it
    A pull request's labels are resources it embeds, not members of its
    state: a model shown only the state can't tell what it is labelled, and
    makes a check on something else.
    Given the held resource embeds under "github:labels" resources named
      | name       |
      | dependabot |
      | hold       |
    And the Claude API will answer with the move {"move": "finish"}
    When the planner is asked its next move
    Then the planner's request says
      | part                                   |
      | <embedded>                             |
      | github:labels: dependabot, hold        |
    And the planner's system prompt says
      | part                                                               |
      | or a relation in <embedded>, whose value is the names listed there |

  Scenario: Each call's tokens are counted as the Claude API says them, the reasoner's and the planner's together
    A run's cost is its model calls and their tokens. The planner the
    reasoner makes shares its count, so one count says what the run took.
    Given the Claude API will answer, using {"input_tokens": 120, "output_tokens": 30, "cache_read_input_tokens": 900, "cache_creation_input_tokens": 40}, with the move {"move": "finish"}
    When the planner is asked its next move
    Then the planner counted
      | calls | input | output | cache_read | cache_write |
      | 1     | 120   | 30     | 900        | 40          |

  Scenario: The planner is told a follow moves it and makes nothing, so from a list it follows on to the item
    Live, told only that following a link to a list makes no branch, the
    planner went back from the branch list each time it got there, never on
    to main, until the step ran out of moves.
    Given the Claude API will answer with the move {"move": "finish"}
    When the planner is asked its next move
    Then the planner's system prompt says
      | part                                                                            |
      | A follow moves you and makes nothing                                            |
      | from a list, follow on to the item you need (a branch, an issue), then act there |

  Scenario: The planner gives its reason first, then the move; the reason is kept with the move
    Live, the planner at a list of branches went back about one time in four
    instead of on to main; asked to say why first, it went on to main every
    time. The move tool asks for the reason before anything else.
    Given the Claude API will answer with the move {"why": "the branch starts from main", "move": "act", "words": "open the main branch", "opens": true}
    When the planner is asked its next move
    Then the move is act "open the main branch" for sentence null, opening a block
    And the planner's tool asks first for
      | property |
      | why      |
      | move     |
    And the move's reason is "the branch starts from main"

  Scenario: In a goal in steps, the model is shown the steps, the step now and the names, and may make only its moves
    spec/PLAN-GOAL-MARKDOWN.md. The runner keeps the structure: the model
    sees the steps as a tree, the step now marked, the resources the steps
    named, and the context the person wrote; its tool offers only the moves
    the step now allows, so a wrong kind of move can't be chosen.
    Given the goal is in steps, now at "Verify: it is closed", with "issue" named at "https://github.gateway.test/repos/acme/rockets/issues/10"
    And the Claude API will answer with the move {"move": "check", "facts": {"state": "closed"}}
    When the planner is asked its next move
    Then the move is check {state=closed}
    And the planner's request says
      | part                                                             |
      | <context>                                                        |
      | Fix the Safari issue.                                            |
      | [done] Open issue 10 of acme/rockets (as issue)                  |
      | [now] Verify: it is closed                                       |
      | issue = https://github.gateway.test/repos/acme/rockets/issues/10 |
    And the planner's tool offers only the moves
      | move    |
      | check   |
      | clarify |
      | stop    |
