Feature: artie generate says how it derived each value, and shows it beside the run's script
  spec/PLAN-SCRIPT-GENERATOR.md. Beside the script and its lock file, artie
  generate writes NAME.derivation.yaml: each derivation, numbered in the order
  the generated script first shows it, with its kind, the rule that bound it,
  and where it stands in the run's script and in the generated one (line and
  column, from 1); and where each step of the run begins in both, so the two
  can be read side by side, row by row. Steps keep the capture's numbers, so
  a block end's re-read (steps 3 and 6 below) begins no row. A value used again under the same
  binding keeps its number. With --report, the derivation report is built from
  them: the run's script beside the generated one, each derivation numbered on
  both sides.

  Background:
    Given a service holds these resources
      | address    | title                 | state |
      | /          | Repositories          |       |
      | /issues/10 | Login fails on Safari |       |
      | /c/502     | Seen in #10.          |       |
    And these forms
      | at         | relation   | leads to   | outcome      |
      | /          | open-issue | /issues/10 | new resource |
      | /          | issues-get | /issues/10 | done         |
      | /issues/10 | comment    | /c/502     | new resource |
      | /issues/10 | close      | /issues/10 | done         |
    And the resource at "/issues/10" holds
      """
      {"number": 10, "title": "Login fails on Safari"}
      """
    And the form "issues-get" at "/" asks for these fields, typed
      | field  | type    |
      | number | integer |

  Scenario: A reasoned run's derivations: inputs, answers, the relations chosen, strings with names, the reasoner's choice
    Given the script "close.kts" is
      """kotlin
      val title by input()

      heart("scripted://service/") {
          var issue = 0L
          act(intent = "open an issue titled $title") {
              issue = state.long("number")
          }
          act(intent = "open issue $issue") {
              act(intent = "comment on it: Seen in #$issue.") {}
              act(intent = "close it, since it's done")
          }
      }
      """
    And the reasoner chooses
      | intent                                     | selection | relation   | fields                             |
      | open an issue titled Login fails on Safari | act       | open-issue | {"title": "Login fails on Safari"} |
      | open issue 10                              | act       | issues-get | {"number": "10"}                   |
      | comment on it: Seen in #10.                | act       | comment    | {"body": "Seen in #10."}           |
      | close it, since it's done                  | act       | close      | {"reason": "completed"}            |
    When artie is run with
      | argument                    |
      | run                         |
      | close.kts                   |
      | title=Login fails on Safari |
      | --reasoner                  |
      | claude                      |
      | --trace                     |
      | run.trace.yaml              |
    Then artie exits 0
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.kts        |
      | --script       |
      | close.kts      |
      | --report       |
      | derivation     |
    Then artie exits 0
    And artie prints
      | line                                            |
      | wrote gen.kts: 1 value to review                |
      | wrote gen.pins.yaml: 4 forms and 0 links pinned |
      | wrote gen.derivation.yaml: 5 derivations        |
      | wrote derivation/index.html                     |
    And the derivation "gen.derivation.yaml" has these derivations
      | n | kind     | rule   | title                                       | source                                                                                | generated                                                                                 |
      | 1 | input    | 1, 2.1 | title, an input                             | 1:1 val title by input(); 5:40 $title                                                 | 6:1 val title by input(); 11:34 title                                                     |
      | 2 | answer   | 2.3    | issue, read from step 2's answer            | 4:5 var issue = 0L; 6:9 issue = state.long("number"); 8:30 $issue                | 9:5 val issue by kept("issue") { long("number") }; 12:9 keepAs("issue"); 14:35 issue      |
      | 3 | relation | 9      | The relation each reason step chose, named  | 5:5 act; 8:5 act; 9:9 act; 10:9 act                                                  | 11:9 "open-issue"; 14:9 "issues-get"; 15:13 "comment"; 16:13 "close"                      |
      | 4 | template | 3      | A string, its values put back as their names | 9:47 $issue                                                                          | 15:34 "Seen in #$issue."                                                                  |
      | 5 | model    | 4      | "completed", the reasoner's own choice      |                                                                                       | 17:25 "completed"                                                                    |
    And the derivation "gen.derivation.yaml" lines up these rows
      | at     | source | generated |
      | inputs | 1      | 6         |
      | 1      | 3      | 8         |
      | 2      | 5      | 11        |
      | 4      | 8      | 14        |
      | 5      | 9      | 15        |
      | 7      | 10     | 16        |
    And the derivation report "derivation/index.html" shows "close.kts" beside "gen.kts", by "gen.derivation.yaml"
    And after the two scripts, the derivation report "derivation/index.html" shows these files, in order
      | file                |
      | gen.pins.yaml       |
      | gen.derivation.yaml |

  Scenario: A scripted run's derivations: each value where its call wrote it
    Given the script "close.kts" is
      """kotlin
      val title by input()

      heart("scripted://service/") {
          var issue = 0L
          act("open-issue", "title" to title) {
              issue = state.long("number")
          }
          act(
              "issues-get",
              "number" to issue,
          ) {
              act("comment", "body" to "Seen in #$issue.") {}
              act("close", "reason" to "completed")
          }
      }
      """
    When artie is run with
      | argument                    |
      | run                         |
      | close.kts                   |
      | title=Login fails on Safari |
      | --trace                     |
      | run.trace.yaml              |
    Then artie exits 0
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.kts        |
      | --script       |
      | close.kts      |
    Then artie exits 0
    And the derivation "gen.derivation.yaml" has these derivations
      | n | kind     | rule   | title                                        | source                                                             | generated                                                          |
      | 1 | input    | 1, 2.1 | title, an input                              | 1:1 val title by input(); 5:34 title                               | 5:1 val title by input(); 10:34 title                              |
      | 2 | answer   | 2.3    | issue, read from step 2's answer             | 4:5 var issue = 0L; 6:9 issue = state.long("number"); 10:21 issue  | 8:5 val issue by kept("issue") { long("number") }; 11:9 keepAs("issue"); 13:35 issue |
      | 3 | template | 3      | A string, its values put back as their names | 12:34 "Seen in #$issue."                                           | 14:34 "Seen in #$issue."                                           |
      | 4 | words    | 2.2    | "completed", as the script wrote it          | 13:34 "completed"                                                  | 15:34 "completed"                                                  |

  Scenario: The derivation report needs the run's script to show beside the generated one
    Given the file "run.trace.yaml" is
      """
      ---
      run:
        inputs: {}
      ---
      mode: script
      say: enter {entry}
      line: 1
      params:
        entry: http://localhost:8392/
      outcome: done
      """
    When artie is run with
      | argument       |
      | generate       |
      | run.trace.yaml |
      | --out          |
      | gen.kts        |
      | --report       |
      | derivation     |
    Then artie exits 2
    And artie's last line is: --report needs --script: the report shows the run's script beside the one generated
