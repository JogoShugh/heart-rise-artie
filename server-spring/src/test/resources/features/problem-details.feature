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
    And the problem's "rel" member is "sowSeed"

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
