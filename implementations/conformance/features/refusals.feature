Feature: Each refusal a service sends is told apart
  A client tells a refusal by its status and problem type, says what the
  service said of it, and re-reads only when what it held is stale or the act
  is not offered now; it never resends. The deploy service answers its
  "cd:scale" act here as each scenario says.

  Background:
    Given a client at rung L0 with the deploy service's credential

  Scenario Outline: Each refusal is told apart: status <status>, <says>
    Given the deploy service answers "cd:scale" with status <status> and the body <body>
    When it enters the app "web"
    And it tries to submit "cd:scale" with {"replicas": 2}
    Then it was refused as "<kind>" with status <status>
    And the refusal says "<says>"
    And the resource was read <reads> times

    Examples:
      | status | body                                                                                                               | kind                  | says                                     | reads |
      | 412    | {"detail": "changed"}                                                                                              | stale                 | changed; re-read, not resent             | 2     |
      | 409    | {"title": "taken"}                                                                                                 | not-offered           | cd:scale was refused (409): taken        | 2     |
      | 428    | {"title": "no validator"}                                                                                          | precondition-required | cd:scale was refused (428): no validator | 1     |
      | 401    | {"title": "who"}                                                                                                   | not-authenticated     | cd:scale was refused (401): who          | 1     |
      | 500    | {"title": "broke"}                                                                                                 | failed                | cd:scale was refused (500): broke        | 1     |
      | 500    | not json                                                                                                           | failed                | cd:scale was refused (500)               | 1     |
      | 422    | {"type": "https://w3id.org/heart-rise/problems/rule-refused", "violations": [{"pointer": "/a", "message": "big"}]} | rule-refused          | /a big                                   | 1     |
      | 422    | {"violations": [{"pointer": "/b", "detail": "small"}]}                                                             | invalid-input         | /b small                                 | 1     |
      | 422    | {"violations": [{"message": "only words"}]}                                                                        | invalid-input         | only words                               | 1     |

  Scenario: A refusal with no violations names none
    Given the deploy service answers "cd:scale" with status 409 and the body {}
    When it enters the app "web"
    And it tries to submit "cd:scale" with {"replicas": 2}
    Then it names no violations

  Scenario Outline: A violation shows what the service said of it: <shows>
    Given the deploy service answers "cd:scale" with status 422 and the body <body>
    When it enters the app "web"
    And it tries to submit "cd:scale" with {"replicas": 2}
    Then the only violation is "<shows>"

    Examples:
      | body                                                 | shows      |
      | {"violations": [{"message": "only words"}]}          | only words |
      | {"violations": [{"detail": "a detail"}]}             | a detail   |
      | {"violations": [{"pointer": "/c"}]}                  | /c         |
      | {"violations": [{"pointer": "/a", "detail": "a d"}]} | /a a d     |

  Scenario: A rule the service keeps is told apart from a body it disallows
    When it enters the app "web"
    And it tries to submit "cd:scale" with {"replicas": 50}
    Then it was refused as "rule-refused" with status 422
    And the refusal says "/replicas at most 10"
