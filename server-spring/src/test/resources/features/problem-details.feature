Feature: Refusals are RFC 9457 problem details
  SPEC §6.2 (decided 2026-10-10). Each refusal of an act is an
  application/problem+json body with type, title and status. The type URI
  names the refusal, so a client tells refusals apart without reading prose.
  A 422 lists each failing field as an RFC 6901 JSON Pointer.

  Background:
    Given bed 2 is PLANTED

  Scenario: 428 says the precondition is required
    When a client waters bed 2 with 100 ml and no If-Match
    Then the response status is 428
    And the response is a problem of type "precondition-required" with status 428

  Scenario: 412 says the act is stale
    Given a client got bed 2 as hal-schema-forms with Prefer ""
    And someone else waters bed 2 with 100 ml
    When the client waters bed 2 with 100 ml using the ETag it holds
    Then the response status is 412
    And the response is a problem of type "stale" with status 412

  Scenario: 409 says the act is not offered, and names it
    Given a client got bed 2 as hal-schema-forms with Prefer ""
    When the client posts {"seedType": "tomato"} to sow-seed on bed 2 using the ETag it holds
    Then the response status is 409
    And the response is a problem of type "not-offered" with status 409
    And the problem's "rel" member is "/profiles/garden#sow-seed"

  Scenario Outline: A sowing depth must be a whole number of centimetres, as the schema says: <depth>
    The form's schema is generated from the act's input class, and the body
    is checked against it, so the server refuses what the form forbids. It
    used to cut 2.5 to 2 without saying so.
    Given bed 1 is EMPTY
    And a client got bed 1 as hal-schema-forms with Prefer ""
    When the client posts {"seedType": "basil", "depthCm": <depth>} to sow-seed on bed 1 using the ETag it holds
    Then the response status is 422
    And the response is a problem of type "invalid-input" with status 422
    And the problem lists a violation at "/depthCm"

    Examples:
      | depth |
      | 2.5   |
      | 0     |
      | "two" |

  Scenario Outline: 422 lists each failing field by JSON Pointer: <body>
    Given a client got bed 2 as hal-schema-forms with Prefer ""
    When the client posts <body> to water-cell on bed 2 using the ETag it holds
    Then the response status is 422
    And the response is a problem of type "invalid-input" with status 422
    And the problem lists a violation at "/milliliters"

    Examples:
      | body                    |
      | {"milliliters": 0}      |
      | {"milliliters": "lots"} |
      | {}                      |
