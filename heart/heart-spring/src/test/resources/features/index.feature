Feature: The bookmark and the searches, served over HTTP from the declarations
  SPEC §6.11 and §6.11.1. heart-spring serves the service index at / in
  either dialect, or as JSON Home when Accept ranks it first, and each
  findable type's search at /{collection}. All are Layer 1: revalidated on
  every use.

  Background:
    Given lamp 1 is "ON" at version 1 in the lamp server
    And lamp 2 is "OFF" at version 1 in the lamp server

  Scenario Outline: The index answers in the dialect asked for: <dialect>
    When a client gets "/" as <dialect>
    Then the response status is 200
    And the response has Content-Type "<type>"
    And the response has Cache-Control "no-cache"
    And the response has Vary "Accept, Accept-Profile"
    And the response has an ETag

    Examples:
      | dialect          | type                           |
      | hal-schema-forms | application/hal+json           |
      | hal-forms        | application/prs.hal-forms+json |

  Scenario: An unchanged index is revalidated with 304
    When a client gets "/" as hal-schema-forms
    And a client gets "/" again with the ETag it was given
    Then the response status is 304

  Scenario: Ranked first, JSON Home is the answer
    When a client gets "/" with Accept "application/json-home, application/hal+json"
    Then the response status is 200
    And the response has Content-Type "application/json-home"
    And the response has Vary "Accept"

  Scenario: A search lists the lamps found, titled, in the dialect asked for
    When a client gets "/lamps?name=lamp" as hal-schema-forms
    Then the response status is 200
    And the response has Content-Type "application/hal+json"
    And the response lists the items "/lamps/1" titled "Lamp 1", "/lamps/2" titled "Lamp 2"
