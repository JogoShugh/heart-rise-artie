Feature: Conditional acts and the error model (L4)
  SPEC §6.2, §6.10.5. Every act must carry If-Match. The server checks an
  act in a fixed order and returns the first failure (§6.2). These requests
  send no Accept or Prefer, so they select the default representation,
  HAL-FORMS at L0, whose ETag is "seq-N.hf.L0":
  428 no If-Match, 412 stale If-Match, 409 affordance not in the current
  manifest, 422 body fails the schema. A refused act changes nothing.

  Background:
    Given bed 2 is PLANTED

  Scenario: An act without If-Match is refused with 428
    When a client waters bed 2 with 100 ml and no If-Match
    Then the response status is 428
    And bed 2 is still at state version 1

  Scenario: A stale If-Match is refused with 412
    Given a client got bed 2 as hal-schema-forms with Prefer ""
    And someone else waters bed 2 with 100 ml
    When the client waters bed 2 with 100 ml using the ETag it holds
    Then the response status is 412
    And bed 2 is still at state version 2

  Scenario: An affordance absent from the manifest is refused with 409, even with a fresh ETag
    Given a client got bed 2 as hal-schema-forms with Prefer ""
    When the client posts {"seedType": "tomato"} to sow-seed on bed 2 using the ETag it holds
    Then the response status is 409
    And bed 2 is still at state version 1

  Scenario Outline: A body that fails the schema is refused with 422: <body>
    Given a client got bed 2 as hal-schema-forms with Prefer ""
    When the client posts <body> to water-cell on bed 2 using the ETag it holds
    Then the response status is 422
    And bed 2 is still at state version 1

    Examples:
      | body                    |
      | {"milliliters": 0}      |
      | {"milliliters": 5000}   |
      | {"milliliters": "lots"} |
      | {}                      |

  Scenario: A body that is not JSON at all is a bad request, not a schema failure
    Given a client got bed 2 as hal-schema-forms with Prefer ""
    When the client posts {"milliliters": to water-cell on bed 2 using the ETag it holds
    Then the response status is 400
    And bed 2 is still at state version 1

  Scenario Outline: The first failing check decides the status: <action>, If-Match <if-match>, <body> gives <status>
    When a client posts <body> to <action> on bed 2 with If-Match <if-match>
    Then the response status is <status>

    Examples: each row breaks every later check too, so only the order picks the code
      | action     | if-match       | body                 | status |
      | sow-seed   | none           | {"seedType": ""}     | 428    |
      | sow-seed   | "seq-0.hf.L0"  | {"seedType": ""}     | 412    |
      | sow-seed   | "seq-1.hf.L0"  | {"seedType": ""}     | 409    |
      | harvest    | "seq-1.hf.L0"  | {}                   | 409    |
      | water-cell | "seq-1.hsf.L2" | {"milliliters": 100} | 412    |
      | water-cell | "seq-1.hf.L0"  | {"milliliters": 0}   | 422    |
      | water-cell | "seq-1.hf.L0"  | {"milliliters": 100} | 200    |
