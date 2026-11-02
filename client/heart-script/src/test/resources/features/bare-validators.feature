Feature: A bare script with validators: plain HTTP done right, no HEART
  spec/ideas/gateway-pass-through.md, step 2. A bare script sends plain calls
  and reads plain answers, but it may keep what HTTP has always offered: the
  ETag of each resource it read, sent back as If-Match on a write that
  replaces that resource (PATCH, PUT or DELETE at its address). A server
  that checks it, as the gateway's conditional rung does in front of GitHub,
  then refuses a write over someone else's change (412), and the script
  stops, saying so. Headers given to bare go with every call: the one that
  asks the gateway to pass a call through, say. Without validators a bare
  script sends no If-Match, as before.

  Scenario Outline: The write carries the ETag read when the script keeps validators: <case>
    Given the server answers, in order
      | method | url                                 | status | etag   | body                    |
      | GET    | https://gw.test/repos/a/b/issues/10 | 200    | "gw-1" | {"title":"Login fails"} |
      | PATCH  | https://gw.test/repos/a/b/issues/10 | 200    |        | {"state":"closed"}      |
    When this script runs
      """kotlin
      bare(<options>) {
          call("GET", "https://gw.test/repos/a/b/issues/10")
          call("PATCH", "https://gw.test/repos/a/b/issues/10", mapOf("state" to "closed"))
      }
      """
    Then the script finished
    And the bare calls carried these headers
      | call                                      | If-Match   | X-Gateway-Mode   |
      | GET https://gw.test/repos/a/b/issues/10   |            | <mode>           |
      | PATCH https://gw.test/repos/a/b/issues/10 | <if match> | <mode>           |

    Examples:
      | case                         | options                                                              | if match | mode         |
      | validators                   | validators = true                                                    | "gw-1"   |              |
      | no validators: none sent     |                                                                      |          |              |
      | through the gateway          | validators = true, headers = mapOf("X-Gateway-Mode" to "pass-through") | "gw-1"   | pass-through |

  Scenario: A write refused as stale stops the script, saying so
    Given the server answers, in order
      | method | url                                 | status | etag   | body                                                       |
      | GET    | https://gw.test/repos/a/b/issues/11 | 200    | "gw-2" | {"title":"Crash"}                                          |
      | PATCH  | https://gw.test/repos/a/b/issues/11 | 412    |        | {"message":"the resource changed since it was read"} |
    When this script runs
      """kotlin
      bare(validators = true) {
          call("GET", "https://gw.test/repos/a/b/issues/11")
          call("PATCH", "https://gw.test/repos/a/b/issues/11", mapOf("state" to "closed"))
      }
      """
    Then the script stopped at step 2, "PATCH https://gw.test/repos/a/b/issues/11": the server answered 412: {"message":"the resource changed since it was read"}
