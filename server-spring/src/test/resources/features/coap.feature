Feature: The CoAP binding (SPEC §6.0.1)
  The server answers over CoAP (RFC 7252) as well as HTTP, with the same
  resources and the same rules. CoAP has no headers for the profile or the
  preference, so the binding carries them in experimental options, one for
  one with the HTTP headers: 65000 Accept-Profile, 65004 Prefer, 65008
  Profile, 65012 Preference-Applied, and 65003 No-Cache on a Layer 1 read.
  A CoAP ETag holds at most 8 bytes, so the CoAP validator is the first 8
  bytes of SHA-256 over the HTTP ETag value.

  Background:
    Given bed 2 is PLANTED

  Scenario: A bed over CoAP says its profile, its rung, its validator and that it is never fresh
    When a CoAP client gets "/beds/2" with Prefer "forms=link, schema=link"
    Then the CoAP response is 2.05
    And the CoAP Content-Format is 65000
    And the CoAP option 65008 is "https://github.com/jogoshugh/heart-rise/hal-schema-forms"
    And the CoAP option 65012 is "forms=link, schema=link"
    And the CoAP Max-Age is 0
    And the CoAP ETag is the first 8 bytes of SHA-256 over "\"seq-1.hsf.L2\""

  Scenario: CoAP and HTTP serve the same representation
    When a CoAP client gets "/beds/2" with Prefer "forms=link, schema=link"
    And a client got bed 2 as hal-schema-forms with Prefer "forms=link, schema=link"
    Then the CoAP body is the same JSON as the HTTP body

  Scenario: An unchanged bed revalidates to 2.03 Valid
    Given a CoAP client got "/beds/2" with Prefer ""
    When the CoAP client gets "/beds/2" again with the ETag it holds
    Then the CoAP response is 2.03

  Scenario: The shared layers a CoAP representation links to are served over CoAP, immutable
    When a CoAP client gets "/beds/2" with Prefer "forms=link, schema=link"
    And the CoAP client follows the representation's "hr:forms" link
    Then the CoAP response is 2.05
    And the CoAP Max-Age is 31536000
    And the CoAP body matches the integrity of the link that led to it

  Scenario Outline: An act over CoAP: <case>
    Given a CoAP client got "/beds/2" with Prefer ""
    When the CoAP client posts <body> to "/beds/2/actions/<action>" with <if-match>
    Then the CoAP response is <code>
    And the CoAP problem type is "<problem>"

    Examples:
      | case                  | action     | if-match             | body                 | code | problem                  |
      | no validator          | water-cell | no If-Match          | {"milliliters": 100} | 4.00 | precondition-required    |
      | a stale validator     | water-cell | If-Match "0000"      | {"milliliters": 100} | 4.12 | stale                    |
      | an act not offered    | sow-seed   | the ETag it holds    | {"seedType": "kale"} | 4.09 | not-offered              |
      | a body the form fails | water-cell | the ETag it holds    | {"milliliters": 0}   | 4.22 | invalid-input            |

  Scenario: A successful act over CoAP answers with the bed's new representation
    Given a CoAP client got "/beds/2" with Prefer ""
    When the CoAP client posts {"milliliters": 100} to "/beds/2/actions/water-cell" with the ETag it holds
    Then the CoAP response is 2.04
    And the CoAP body links self to "/beds/2"
    And bed 2 is at state version 2

  Scenario: The service index and a search work over CoAP
    When a CoAP client gets "/" with Prefer ""
    Then the CoAP response is 2.05
    When a CoAP client gets "/beds?id=2" with Prefer ""
    Then the CoAP response is 2.05
    And the CoAP body links item to "/beds/2"

  Scenario: HAL-FORMS is the HTTP on-ramp, not served over CoAP
    When a CoAP client asks for HAL-FORMS at "/beds/2"
    Then the CoAP response is 4.06
