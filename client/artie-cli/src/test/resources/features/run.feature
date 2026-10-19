Feature: artie run: a script, run from the command line
  spec/PLAN-ARTIE-SCRIPTS.md, "Running a script". artie run takes a script,
  its inputs (name=value, or a JSON or YAML file, name=value winning) and
  options. It prints one line per step and how the run ended, writes the
  trace, and its exit says how the run ended, so a shell or CI can tell a
  failed test from a refusal, a client failure or a bug in the script.

  Background:
    Given a service holds these resources
      | address    | title                 | state |
      | /          | Repositories          |       |
      | /issues/10 | Login fails on Safari | open  |
      | /c/502     | Fixed in 1.2.         |       |
    And these forms
      | at         | relation   | leads to   | outcome      |
      | /          | issues-get | /issues/10 | done         |
      | /issues/10 | comment    | /c/502     | new resource |
      | /issues/10 | close      | /issues/10 | done         |
      | /issues/10 | lock       | /issues/10 | done         |
    And the client cannot reach the server for "lock" at "/issues/10": "connection refused"

  Scenario: Each step is a line, with its values, and the run's end is the last
    Given the script "issue.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act("close")
      }
      """
    When artie is run with
      | argument  |
      | run       |
      | issue.kts |
    Then artie exits 0
    And artie prints
      | line                               |
      | 1 enter scripted://service/: done  |
      | 2 act issues-get, number 10: done  |
      | 3 act close: done                  |
      | finished, 3 steps                  |

  Scenario Outline: The exit says how the run ended
    Given the script "issue.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          <step>
      }
      """
    When artie is run with
      | argument  |
      | run       |
      | issue.kts |
    Then artie exits <exit>
    And artie's last line is: <last>

    Examples:
      | step                                                       | exit | last                                                                                   |
      | act("close")                                               | 0    | finished, 3 steps                                                                      |
      | expect(Done) { act("comment") }                            | 1    | stopped at step 2, "expect": expected done, but it was new resource                    |
      | check(state["/state"] == "closed") { "should be closed" }  | 1    | stopped at step 2, "check": "should be closed"                                         |
      | check(state["/state"] == "closed")                         | 1    | stopped at step 2, "check": "check failed"                                             |
      | act("reopen")                                              | 3    | stopped at step 2, "reopen": not offered, refused by the client: "The issue is already open." |
      | follow("item", title = "nope")                             | 3    | stopped at step 2, "item": not offered, refused by the client: "no "item" link titled "nope""             |
      | act("lock")                                                | 4    | stopped at step 2, "lock": unreachable, refused by the client: "connection refused"    |
      | error("a bug in the script")                               | 5    | the script failed: a bug in the script                                                 |

  Scenario: A condition that fails is drawn above the run's end
    Given the script "issue.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          val state = state["/state"]
          check(state == "closed") { "should be closed" }
      }
      """
    When artie is run with
      | argument  |
      | run       |
      | issue.kts |
    Then artie exits 1
    And artie prints, exactly
      """
      1 enter scripted://service/: done
      2 act issues-get, number 10: done
      3 check: failed
      check(state == "closed") { "should be closed" }
            |     |
            |     false
            open
      stopped at step 2, "check": "should be closed"
      """

  Scenario Outline: Inputs come from the command line or a file, the command line winning
    Given the script "comment.kts" is
      """kotlin
      val number by input()
      val body by input(default = "Fixed.")
      heart("scripted://service/") {
          act("issues-get", "number" to number)
          act("comment", "body" to body)
      }
      """
    And the file "<file>" is
      """
      <content>
      """
    When artie is run with
      | argument        |
      | run             |
      | comment.kts     |
      | --inputs        |
      | <file>          |
      | body=From me.   |
    Then artie exits 0
    And the client was asked, in order
      | call  | what                         |
      | enter | scripted://service/          |
      | act   | issues-get {"number":"10"}   |
      | act   | comment {"body":"From me."}  |

    Examples:
      | file        | content                               |
      | inputs.json | {"number": "10", "body": "From file"} |
      | inputs.yaml | number: 10                            |

  Scenario: A missing input stops the run before its first request
    Given the script "comment.kts" is
      """kotlin
      val number by input()
      heart("scripted://service/") {
          act("issues-get", "number" to number)
      }
      """
    When artie is run with
      | argument    |
      | run         |
      | comment.kts |
    Then artie exits 2
    And artie's last line is: stopped before the first request: missing input: number
    And the client was asked nothing

  Scenario: A question is asked only of an interactive run
    Given the script "close.kts" is
      """kotlin
      val reason by input(question = "Why close it?")
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act("close", "reason" to reason)
      }
      """
    And the person will type
      | line      |
      | completed |
    When artie is run with
      | argument      |
      | run           |
      | close.kts     |
      | --interactive |
    Then artie exits 0
    And the client was asked, in order
      | call  | what                         |
      | enter | scripted://service/          |
      | act   | issues-get {"number":10}     |
      | act   | close {"reason":"completed"} |

  Scenario: The inputs of a script, listed without running it
    Given the script "close.kts" is
      """kotlin
      val number by input()
      val body by input(default = "Fixed.")
      val reason by input(question = "Why close it?")
      heart("scripted://service/") {
          act("issues-get", "number" to number)
          act("comment", "body" to ask("Anything to add?", default = body))
          act("close", "reason" to ask("Close as?", default = "completed"))
      }
      """
    When artie is run with
      | argument    |
      | run         |
      | close.kts   |
      | --inputs-of |
    Then artie exits 0
    And artie prints
      | line                                                  |
      | number: required                                      |
      | body: optional, default "Fixed."                      |
      | reason: a question, "Why close it?"                   |
      | 2 inline questions, each answered by its default unattended |
    And the client was asked nothing

  Scenario: Another entry than the script's
    Given the script "issue.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
      }
      """
    When artie is run with
      | argument            |
      | run                 |
      | issue.kts           |
      | --entry             |
      | scripted://staging/ |
    Then artie exits 0
    And the client was asked, in order
      | call  | what                     |
      | enter | scripted://staging/      |
      | act   | issues-get {"number":10} |

  Scenario Outline: The client keeps its exchanges for the run's capture only when asked
    spec/PLAN-LINE-BY-LINE-REPORT.md: with --exchanges, each step of the
    trace keeps the requests the client sent and their answers.
    Given the script "issue.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
      }
      """
    When artie is run with
      | argument   |
      | run        |
      | issue.kts  |
      | <argument> |
    Then artie exits 0
    And the client was made <keeping> its exchanges

    Examples:
      | argument    | keeping         |
      | --exchanges | to keep         |
      | --quiet     | not to keep     |

  Scenario Outline: The rung the client asks at, pinned by the run
    Given the script "issue.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
      }
      """
    When artie is run with
      | argument  |
      | run       |
      | issue.kts |
      | --rung    |
      | <rung>    |
    Then artie exits <exit>
    And the client was made to ask at rung "<asked>"

    Examples:
      | rung | exit | asked |
      | L0   | 0    | L0    |
      | L2   | 0    | L2    |
      | L9   | 2    |       |

  Scenario: A script that reasons is stopped before its first request, unless the run allows a reasoner
    Given the script "close.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          reason("we're done with this one")
      }
      """
    When artie is run with
      | argument  |
      | run       |
      | close.kts |
    Then artie exits 2
    And artie's last line is: stopped before the first request: reason(…) needs --reasoner
    And the client was asked nothing

  Scenario: With --reasoner, the client is made with that reasoner, and the reason step is an intent
    Given the script "close.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          reason("we're done with this one")
      }
      """
    And the reasoner chooses
      | intent                   | selection | relation | fields |
      | we're done with this one | act       | close    | {}     |
    When artie is run with
      | argument    |
      | run         |
      | close.kts   |
      | --reasoner  |
      | claude      |
    Then artie exits 0
    And the client was made with the reasoner "claude"
    And artie prints
      | line                                |
      | 1 enter scripted://service/: done   |
      | 2 act issues-get, number 10: done   |
      | 3 we're done with this one: done    |
      | finished, 3 steps                   |

  Scenario: A reasoner artie doesn't know stops the run before its first request
    Given the script "issue.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
      }
      """
    When artie is run with
      | argument   |
      | run        |
      | issue.kts  |
      | --reasoner |
      | oracle     |
    Then artie exits 2
    And artie's last line is: --reasoner is claude

  Scenario: The trace, written where the run is told
    Given the script "issue.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act("reopen")
      }
      """
    When artie is run with
      | argument   |
      | run        |
      | issue.kts  |
      | --trace    |
      | run.yaml   |
    Then artie exits 3
    And the trace "run.yaml" is YAML: one document for the run, then one for each step
    And the trace "run.yaml" has these steps
      | say                             | outcome |
      | enter {entry}                   | done    |
      | act {relation}, number {number} | done    |
      | act {relation}                  | refused |

  Scenario: The run report, written where the run is told, with the script in it
    Given the script "issue.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
          act("reopen")
      }
      """
    When artie is run with
      | argument  |
      | run       |
      | issue.kts |
      | --report  |
      | report    |
    Then artie exits 3
    And the run report "report/run.html" holds the script "issue.kts" and these steps
      | say                             | outcome |
      | enter {entry}                   | done    |
      | act {relation}, number {number} | done    |
      | act {relation}                  | refused |
    And the line-by-line report "report/index.html" shows the script "issue.kts" first, then these steps
      | say                             | outcome |
      | enter {entry}                   | done    |
      | act {relation}, number {number} | done    |
      | act {relation}                  | refused |

  Scenario Outline: One summary line, in words or JSON
    Given the script "issue.kts" is
      """kotlin
      heart("scripted://service/") {
          act("issues-get", "number" to 10)
      }
      """
    When artie is run with
      | argument  |
      | run       |
      | issue.kts |
      | <option>  |
      | <value>   |
    Then artie exits 0
    And artie prints
      | line    |
      | <line>  |

    Examples:
      | option   | value   | line                                         |
      | --quiet  |         | finished, 2 steps                            |
      | --output | json    | {"ended":"finished","exit":0,"steps":2}      |

  Scenario Outline: Bad arguments stop the run before its first request
    When artie is run with
      | argument   |
      | <first>    |
      | <second>   |
    Then artie exits 2
    And artie's last line is: <last>

    Examples:
      | first | second       | last                                   |
      | run   | missing.kts  | no script at missing.kts               |
      | walk  | issue.kts    | usage: artie run <script> [name=value …] [options] |
