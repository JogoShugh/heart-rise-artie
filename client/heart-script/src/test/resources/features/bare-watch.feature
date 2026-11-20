Feature: A bare watch: the goal words over plain HTTP, every guardrail off
  spec/ideas/bare-runs.md, "Rails". A kept script's watch brought down to
  plain calls to fixed addresses, the way a hand-written script polls: no
  entry, no forms, no validators, no reasons for absence. The words stay
  the goal's own. `watch` looks again and again until its condition holds;
  `whenever` visits each member of a list that is new or changed since the
  last look, or that a `once` still waits on; `has` and `leaveWhere` guard
  a member's turn, `leaveWhere` saying so once; `once` tries its call at
  each look until the server takes it, and after `giveUp` runs its
  `otherwise`, told why in the server's own message. Where the server's
  refusal is the only gate, the call itself is the check. `write` asks the
  reasoner for words, the one line a goal marked reasoned.

  Scenario: A watch looks until its condition holds, and answers each member once
    Given the server answers, in order
      | method | url                               | status | body                                                                                                                                       |
      | GET    | https://api.example/pulls         | 200    | [{"url":"https://api.example/pulls/1","labels":[{"name":"deps"}]},{"url":"https://api.example/pulls/2","labels":[]}]                       |
      | PUT    | https://api.example/pulls/1/merge | 200    | {"merged":true}                                                                                                                            |
      | GET    | https://api.example/issues/9      | 200    | {"state":"open"}                                                                                                                           |
      | GET    | https://api.example/pulls         | 200    | [{"url":"https://api.example/pulls/1","labels":[{"name":"deps"}],"merged":true},{"url":"https://api.example/pulls/2","labels":[]}]         |
      | GET    | https://api.example/issues/9      | 200    | {"state":"closed"}                                                                                                                         |
    When this script runs
      """kotlin
      bare {
          watch(until = { call("GET", "https://api.example/issues/9")["state"].text == "closed" }) {
              whenever(call("GET", "https://api.example/pulls")) { pr ->
                  has(pr["labels"] has "deps")
                  once("PUT", "${pr["url"].text}/merge", mapOf("merge_method" to "squash"))
              }
          }
      }
      """
    Then the script finished
    And the client was asked, in order
      | call | what                                                                 |
      | send | GET https://api.example/pulls                                        |
      | send | PUT https://api.example/pulls/1/merge {"merge_method":"squash"}      |
      | send | GET https://api.example/issues/9                                     |
      | send | GET https://api.example/pulls                                        |
      | send | GET https://api.example/issues/9                                     |

  Scenario: A once tries again at each look, until the server takes it or the wait gives up
    Given the server answers, in order
      | method | url                                 | status | body                                                                                              |
      | GET    | https://api.example/pulls           | 200    | [{"url":"https://api.example/pulls/1","number":1},{"url":"https://api.example/pulls/2","number":2}] |
      | PUT    | https://api.example/pulls/1/merge   | 405    | {"message":"Pull Request is still a draft"}                                                       |
      | PUT    | https://api.example/pulls/2/merge   | 405    | {"message":"Merge conflict"}                                                                      |
      | GET    | https://api.example/issues/9        | 200    | {"state":"open"}                                                                                  |
      | GET    | https://api.example/pulls           | 200    | [{"url":"https://api.example/pulls/1","number":1},{"url":"https://api.example/pulls/2","number":2}] |
      | PUT    | https://api.example/pulls/1/merge   | 200    | {"merged":true}                                                                                   |
      | PUT    | https://api.example/pulls/2/merge   | 405    | {"message":"Merge conflict"}                                                                      |
      | GET    | https://api.example/issues/9        | 200    | {"state":"open"}                                                                                  |
      | GET    | https://api.example/pulls           | 200    | [{"url":"https://api.example/pulls/1","number":1},{"url":"https://api.example/pulls/2","number":2}] |
      | PUT    | https://api.example/pulls/2/merge   | 405    | {"message":"Merge conflict"}                                                                      |
      | POST   | https://api.example/issues/2/comments | 201  | {"id":77}                                                                                         |
      | GET    | https://api.example/issues/9        | 200    | {"state":"closed"}                                                                                |
    And the reasoner writes
      | intent                                   | words                                         |
      | Tell its author why: Merge conflict      | Your branch conflicts with its base; rebase it. |
    When this script runs, allowed a reasoner, each pause moving the clock 15 seconds
      """kotlin
      import kotlin.time.Duration.Companion.seconds

      bare {
          watch(until = { call("GET", "https://api.example/issues/9")["state"].text == "closed" }) {
              whenever(call("GET", "https://api.example/pulls")) { pr ->
                  once("PUT", "${pr["url"].text}/merge", giveUp = 30.seconds, otherwise = { why ->
                      call("POST", "https://api.example/issues/${pr["number"].text}/comments",
                          mapOf("body" to write("Tell its author why: $why")))
                  })
              }
          }
      }
      """
    Then the script finished
    And the client was asked, in order
      | call  | what                                                                                        |
      | send  | GET https://api.example/pulls                                                               |
      | send  | PUT https://api.example/pulls/1/merge                                                       |
      | send  | PUT https://api.example/pulls/2/merge                                                       |
      | send  | GET https://api.example/issues/9                                                            |
      | send  | GET https://api.example/pulls                                                               |
      | send  | PUT https://api.example/pulls/1/merge                                                       |
      | send  | PUT https://api.example/pulls/2/merge                                                       |
      | send  | GET https://api.example/issues/9                                                            |
      | send  | GET https://api.example/pulls                                                               |
      | send  | PUT https://api.example/pulls/2/merge                                                       |
      | write | Tell its author why: Merge conflict                                                         |
      | send  | POST https://api.example/issues/2/comments {"body":"Your branch conflicts with its base; rebase it."} |
      | send  | GET https://api.example/issues/9                                                            |

  Scenario: Guards leave a member, saying so once, and a failed look is noted and tried again
    Given the server answers, in order
      | method | url                          | status | body                                                                                                                                      |
      | GET    | https://api.example/pulls    | 200    | [{"url":"https://api.example/pulls/3","labels":[{"name":"deps"},{"name":"hold"}]},{"url":"https://api.example/pulls/4","labels":[]}]      |
      | GET    | https://api.example/issues/9 | 500    | {"message":"Server Error"}                                                                                                                |
      | GET    | https://api.example/pulls    | 200    | [{"url":"https://api.example/pulls/3","labels":[{"name":"deps"},{"name":"hold"}],"comments":1},{"url":"https://api.example/pulls/4","labels":[]}] |
      | GET    | https://api.example/issues/9 | 200    | {"state":"closed"}                                                                                                                        |
    When this script runs
      """kotlin
      bare {
          watch(until = { call("GET", "https://api.example/issues/9")["state"].text == "closed" }) {
              whenever(call("GET", "https://api.example/pulls")) { pr ->
                  has(pr["labels"] has "deps")
                  leaveWhere("leave it, and say so once.", pr["labels"] has "hold")
                  once("PUT", "${pr["url"].text}/merge")
              }
          }
      }
      """
    Then the script finished
    And the script noted, in order
      | note                                                                                                                         |
      | Left https://api.example/pulls/3: leave it, and say so once.                                                                 |
      | Reading whether the watch is over failed: the server answered 500: {"message":"Server Error"}; looking again next time.      |
