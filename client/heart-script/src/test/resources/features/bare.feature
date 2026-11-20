Feature: A bare script: plain HTTP calls, every guardrail off
  spec/ideas/bare-runs.md. A recorded run brought down to the calls it
  made, replayed the way the native Python is: each request sent as
  written, to the address written, with no entry, no forms, no schema, no
  validator and no checks. An answer is read by JSON Pointer as it came;
  any answer but a 2xx stops the script, saying what the server said. A
  call the run waited before is tried again, a pause apart, until the
  server takes it. The client still sends it: its own credentials, its
  own trace.

  Scenario: Each call is sent as written, and its answer read by pointer
    Given the server answers, in order
      | method | url                                  | status | body                       |
      | POST   | https://api.example/issues           | 201    | {"number":452,"id":9001}   |
      | POST   | https://api.example/issues/452/label | 200    | [{"name":"bug"}]           |
      | PATCH  | https://api.example/issues/7         | 200    | {"number":7}               |
    When this script runs
      """kotlin
      bare {
          val issue = call("POST", "https://api.example/issues", mapOf("title" to "Crash"))
          call("POST", "https://api.example/issues/${issue["number"].text}/label", mapOf("labels" to listOf("bug")))
          call("PATCH", "https://api.example/issues/7", mapOf("duplicate_of" to issue["id"], "body" to b64("seen")))
      }
      """
    Then the script finished
    And the client was asked, in order
      | call | what                                                            |
      | send | POST https://api.example/issues {"title":"Crash"}               |
      | send | POST https://api.example/issues/452/label {"labels":["bug"]}    |
      | send | PATCH https://api.example/issues/7 {"duplicate_of":9001,"body":"c2Vlbg=="} |
    And the trace has these steps
      | step | say                  | outcome | at                                   |
      | 1    | send {method} {url}  | done    | https://api.example/issues           |
      | 2    | send {method} {url}  | done    | https://api.example/issues/452/label |
      | 3    | send {method} {url}  | done    | https://api.example/issues/7         |

  Scenario Outline: Any answer but a 2xx stops the script, the server's words kept: <status>
    Given the server answers, in order
      | method | url                          | status   | body   |
      | PUT    | https://api.example/pulls/9  | <status> | <body> |
    When this script runs
      """kotlin
      bare {
          call("PUT", "https://api.example/pulls/9", mapOf("merge_method" to "squash"))
          call("PUT", "https://api.example/issues/452/lock")
      }
      """
    Then the script stopped at step 1, "PUT https://api.example/pulls/9": the server answered <status>: <body>
    And the client was asked, in order
      | call | what                                                   |
      | send | PUT https://api.example/pulls/9 {"merge_method":"squash"} |

    Examples:
      | status | body                                            |
      | 405    | {"message":"Pull Request is still a draft"}     |
      | 412    | {"message":"Precondition Failed"}               |
      | 500    | {"message":"Server Error"}                      |

  Scenario Outline: A call the run waited before is tried again until the server takes it: <case>
    Given the server answers, in order
      | method | url                               | status | body                                  |
      | PUT    | https://api.example/pulls/9/merge | 405    | {"message":"not mergeable yet"}       |
      | PUT    | https://api.example/pulls/9/merge | 405    | {"message":"not mergeable yet"}       |
      | PUT    | https://api.example/pulls/9/merge | 200    | {"merged":true}                       |
    When this script runs
      """kotlin
      bare {
          call("PUT", "https://api.example/pulls/9/merge", retry = <tries>)
      }
      """
    Then <ended>

    Examples:
      | case                          | tries | ended                                                                                                      |
      | taken on the third try        | 3     | the script finished                                                                                        |
      | still refused after two tries | 2     | the script stopped at step 1, "PUT https://api.example/pulls/9/merge": the server answered 405: {"message":"not mergeable yet"} |
