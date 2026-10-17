Feature: A resource knows who asks, and may refuse them
  spec/PLAN-GITHUB-PULLS.md, phase 7. Some resources act on their caller's
  behalf: a gateway in front of GitHub calls GitHub as each caller, with the
  caller's own credential, since what a caller may see and do is GitHub's to
  say. The binding hands every read, act and search the credential its
  request carried. A resource may then refuse the caller: 401 when the
  credential is no longer good, 403 when it is good but not enough. Both are
  problems, as every refusal is.

  Background:
    Given pull request 482 of "acme/rockets" is "open" in the pull request server

  Scenario: A read is handed the caller's credential
    When a client gets "/repos/acme/rockets/pulls/482" as hal-schema-forms
    Then the pull request server was last asked by "Bearer lamp-token"

  Scenario: A search is handed the caller's credential
    When a client gets "/repos/acme/rockets/pulls" as hal-schema-forms
    Then the pull request server was last asked by "Bearer lamp-token"

  Scenario: An act is handed the caller's credential
    When a client gets "/repos/acme/rockets/pulls/482" as hal-schema-forms
    And the client posts {} to "/repos/acme/rockets/pulls/482/actions/close" with the ETag and headers it synced with
    Then the response status is 200
    And the pull request server was last asked by "Bearer lamp-token"

  Scenario: A resource that finds the caller's credential no longer good answers 401
    Given pull request 1 of "acme/expired" is "open" in the pull request server
    When a client gets "/repos/acme/expired/pulls/1" as hal-schema-forms
    Then the response status is 401
    And the response is a problem of type "not-authenticated"
    And the response offers these challenges
      | challenge           |
      | Basic realm="home"  |
      | Bearer realm="home" |

  Scenario: A resource that won't let this caller see it answers 403
    Given pull request 1 of "acme/secret" is "open" in the pull request server
    When a client gets "/repos/acme/secret/pulls/1" as hal-schema-forms
    Then the response status is 403
    And the response is a problem of type "forbidden"
