Feature: artie run a goal file: a goal in words, planned by the client's planner
  spec/PLAN-PLAIN-TEXT-RUN.md. artie run takes a goal file, NAME.goal.md,
  as it takes a script: its inputs from the command line, a line per step,
  the capture with --trace. The client's planner makes each move, and its
  reasoner each act, so a goal file needs --reasoner. artie generate then
  writes the script from the capture, the goal file named as its source.

  Background:
    Given a service holds these resources
      | address    | title                 | state |
      | /          | Repositories          |       |
      | /issues/10 | Login fails on Safari | open  |
    And these forms
      | at         | relation   | leads to   | outcome |
      | /          | issues-get | /issues/10 | done    |
      | /issues/10 | close      | /issues/10 | done    |
    And the file "close.goal.md" is
      """
      ---
      start: scripted://service/
      inputs:
        number:
      ---

      Open issue {number}. Close it.
      """

  Scenario: A goal file runs move by move, and artie generate writes its script
    Given the planner will move
      | move   | words              | sentence | opens |
      | act    | open issue $number | 1        | yes   |
      | done   |                    | 1        |       |
      | act    | close it           | 2        |       |
      | done   |                    | 2        |       |
      | finish |                    |          |       |
    And the reasoner chooses
      | intent        | selection | relation   | fields           |
      | open issue 10 | act       | issues-get | {"number": "10"} |
      | close it      | act       | close      | {}               |
    When artie is run with
      | argument        |
      | run             |
      | close.goal.md   |
      | number=10       |
      | --reasoner      |
      | claude          |
      | --trace         |
      | goal.trace.yaml |
    Then artie exits 0
    And artie prints
      | line                              |
      | 1 enter scripted://service/: done |
      | 2 open issue 10: done             |
      | 3 close it: done                  |
      | 4 return to /: done               |
      | finished, 4 steps                 |
    When artie is run with
      | argument        |
      | generate        |
      | goal.trace.yaml |
      | --out           |
      | gen.kts         |
      | --script        |
      | close.goal.md   |
    Then artie exits 0
    And the file "gen.kts" has these lines
      | line                                                    |
      | val number by input()                                   |
      | heart("scripted://service/") {                          |
      | act("issues-get", "number" to number, inferred = "open issue $number") { |
      | act("close", inferred = "close it")                       |

  Scenario: The capture keeps the client's count of model calls and tokens
    Given the planner will move
      | move   | words              | sentence | opens |
      | act    | open issue $number | 1        | yes   |
      | done   |                    | 1        |       |
      | act    | close it           | 2        |       |
      | done   |                    | 2        |       |
      | finish |                    |          |       |
    And the reasoner chooses
      | intent        | selection | relation   | fields           |
      | open issue 10 | act       | issues-get | {"number": "10"} |
      | close it      | act       | close      | {}               |
    And the client counted these model tokens
      | calls | input | output | cache_read | cache_write |
      | 6     | 5000  | 400    | 12000      | 3000        |
    When artie is run with
      | argument        |
      | run             |
      | close.goal.md   |
      | number=10       |
      | --reasoner      |
      | claude          |
      | --trace         |
      | goal.trace.yaml |
    Then artie exits 0
    And the trace "goal.trace.yaml" counts these model tokens
      | calls | input | output | cache_read | cache_write |
      | 6     | 5000  | 400    | 12000      | 3000        |

  Scenario: The capture names the model the client reasoned with, and where it answered
    spec/PLAN-LOCAL-MODEL.md, phase 1: beside what the calls took, which model
    took them, and at which address, so one trace can be set against another's.
    Given the planner will move
      | move   | words              | sentence | opens |
      | act    | open issue $number | 1        | yes   |
      | done   |                    | 1        |       |
      | act    | close it           | 2        |       |
      | done   |                    | 2        |       |
      | finish |                    |          |       |
    And the reasoner chooses
      | intent        | selection | relation   | fields           |
      | open issue 10 | act       | issues-get | {"number": "10"} |
      | close it      | act       | close      | {}               |
    And the client reasons with the model "qwen3:30b-a3b-instruct-2507-q4_K_M" at "http://localhost:11434"
    When artie is run with
      | argument        |
      | run             |
      | close.goal.md   |
      | number=10       |
      | --reasoner      |
      | claude          |
      | --trace         |
      | goal.trace.yaml |
    Then artie exits 0
    And the trace "goal.trace.yaml" names the model
      | name                               | url                    |
      | qwen3:30b-a3b-instruct-2507-q4_K_M | http://localhost:11434 |

  Scenario: A client with no planner says why, and where the model key goes
    Given the client has no planner, because "no model key: set ANTHROPIC_API_KEY, or add one for https://api.anthropic.com to ~/.artie/credentials"
    When artie is run with
      | argument        |
      | run             |
      | close.goal.md   |
      | number=10       |
      | --reasoner      |
      | claude          |
    Then artie exits 2
    And artie's last line is: stopped before the first request: no model key: set ANTHROPIC_API_KEY, or add one for https://api.anthropic.com to ~/.artie/credentials

  Scenario: A goal file needs a reasoner
    When artie is run with
      | argument        |
      | run             |
      | close.goal.md   |
      | number=10       |
    Then artie exits 2
    And artie's last line is: stopped before the first request: a goal needs --reasoner
