Feature: A declared resource and its acts, served over HTTP
  SPEC §6.1, §6.2, §6.4 and RFC 9110 §8.7. heart-spring serves Layer 1 of
  every declared resource type at /{collection}/{id}, in either dialect, and
  takes its acts at /{collection}/{id}/actions/{path}. The server supplies
  only how a resource is read and what each act does.

  Background:
    Given lamp 7 is "ON" at version 3 in the lamp server

  Scenario: L0 in HAL Schema Forms: state and forms in one response, revalidated on every use
    When a client gets "/lamps/7" as hal-schema-forms
    Then the response status is 200
    And the response has Content-Type "application/hal+json"
    And the response has ETag "\"seq-3.hsf.L0\""
    And the response has Cache-Control "no-cache"
    And the response has Vary "Accept, Prefer, Accept-Profile"
    And the response links its profile "https://github.com/jogoshugh/heart-rise/hal-schema-forms"
    And the response offers "home:switch-off, home:dim"

  Scenario: L2 links to the shared affordance set, and says which preference it applied
    When a client gets "/lamps/7" as hal-schema-forms with Prefer "forms=link, schema=link"
    Then the response status is 200
    And the response has ETag "\"seq-3.hsf.L2\""
    And the response has Preference-Applied "forms=link, schema=link"
    And the response links "hr:forms" to an affordance set the server serves

  Scenario: An unchanged resource is revalidated with 304
    When a client gets "/lamps/7" as hal-schema-forms with If-None-Match "\"seq-3.hsf.L0\""
    Then the response status is 304
    And the response has Cache-Control "no-cache"

  Scenario: HAL-FORMS shows the same acts, as templates named by the acts' logical names
    When a client gets "/lamps/7" as hal-forms
    Then the response status is 200
    And the response has Content-Type "application/prs.hal-forms+json"
    And the response has ETag "\"seq-3.hf.L0\""
    And the response has templates targeting "/lamps/7/actions/switch-off, /lamps/7/actions/dim"

  Scenario: Accept that names no dialect is refused
    When a client gets "/lamps/7" with Accept "text/html"
    Then the response status is 406

  Scenario: A resource the server does not hold is not found
    When a client gets "/lamps/99" as hal-schema-forms
    Then the response status is 404

  Scenario: An act answers with the resource's new Layer 1, which it names
    When a client posts {"brightness": 20} to "/lamps/7/actions/dim" as hal-schema-forms with If-Match "\"seq-3.hsf.L0\""
    Then the response status is 200
    And the response has Content-Location "/lamps/7"
    And the response has ETag "\"seq-4.hsf.L0\""
    And the response body has "brightness" 20

  Scenario Outline: A refused act is a problem: <case>
    When a client posts <body> to "/lamps/7/actions/<path>" as hal-schema-forms with If-Match "<ifMatch>"
    Then the response status is <status>
    And the response is a problem of type "<type>"
    And lamp 7 is still at version 3

    Examples:
      | case                 | path      | ifMatch           | body              | status | type                  |
      | no If-Match          | dim       |                   | {"brightness": 5} | 428    | precondition-required |
      | stale If-Match       | dim       | "seq-2.hsf.L0"    | {"brightness": 5} | 412    | stale                 |
      | not offered          | switch-on | "seq-3.hsf.L0"    | {}                | 409    | not-offered           |
      | body breaks schema   | dim       | "seq-3.hsf.L0"    | {"brightness": 0} | 422    | invalid-input         |
