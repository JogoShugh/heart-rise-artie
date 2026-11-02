Feature: What an act carries, and what its answer leaves held
  An act goes where its form says, by its method, with its body in its content
  type and the validator the client read; a credential goes only to the
  bookmark's own origin. An answer names the resource it represents in
  Content-Location, and an answer with no body is read again. Here: the GitHub
  service's pull request 482 of acme/web, the deploy service's app "web", and
  the build service's job "nightly", which answers as a Jenkins trigger may.

  Scenario Outline: An act carries what its form says, by <method>
    Given a client at rung L0 with the GitHub service's credential
    When it enters the pull request 482 of "acme/web"
    And it submits "<act>" with <body>
    Then it sent <method> with the body "<sent>" and the content type "<type>"
    And it carried the validator it read

    Examples:
      | method | act            | body                | sent                   | type                         |
      | POST   | github:comment | {"body": "Thanks."} | {\"body\":\"Thanks.\"} | application/json             |
      | PUT    | github:merge   | {}                  | {}                     | application/json             |
      | PATCH  | github:close   | {}                  | {}                     | application/merge-patch+json |

  Scenario: A DELETE carries no body and no content type
    Given the deploy service's app "web" starts deploying
    And a client at rung L0 with the deploy service's credential
    When it enters the app "web"
    And it submits "cd:cancel" with {}
    Then it sent DELETE with the body "" and the content type ""
    And it carried the validator it read

  Scenario Outline: A form says how its body is sent: <case>
    Given the GitHub service's form "github:comment" <form>
    And a client at rung L0 with the GitHub service's credential
    When it enters the pull request 482 of "acme/web"
    And it tries to submit "github:comment" with {"body": "Thanks."}
    Then it sent POST with the body "{\"body\":\"Thanks.\"}" and the content type "<type>"

    Examples:
      | case                                    | form                                   | type                        |
      | naming no method or type, a JSON POST   | names no method or content type        | application/json            |
      | its own content type                    | takes "application/vnd.github+json"    | application/vnd.github+json |

  Scenario: A credential goes only to the bookmark's own origin
    Given the GitHub service sends "github:comment" to another origin
    And a client at rung L0 with the GitHub service's credential
    When it enters the pull request 482 of "acme/web"
    And it submits "github:comment" with {"body": "Thanks."}
    Then the service was sent the credential
    And the other origin was sent none

  Scenario Outline: What an act's answer leaves held: <case>
    Given the build service answers acts with <body> body, from <from>
    And a client at rung L0 with the build service's credential
    When it enters the job "nightly"
    And it submits "ci:build" with {}
    Then the client holds "<held>"
    And the resource was read <reads> times

    Examples:
      | case                                 | body | from            | held          | reads |
      | a body that names another resource   | its  | "/jobs/release" | /jobs/release | 1     |
      | a body that names none               | its  | nowhere         | /jobs/nightly | 1     |
      | no body, read again                  | no   | nowhere         | /jobs/nightly | 2     |
      | no body, naming the resource to read | no   | "/jobs/release" | /jobs/release | 2     |
