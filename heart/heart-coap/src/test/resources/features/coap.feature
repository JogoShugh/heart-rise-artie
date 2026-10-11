Feature: A declared resource over CoAP, served by heart-coap
  SPEC §6.0.1. heart-coap answers over CoAP from the same declarations as
  every other binding, with no CoAP code in the server. The profile and the
  preference ride in experimental options (65000 Accept-Profile, 65004
  Prefer, 65008 Profile, 65012 Preference-Applied); the validator is the
  first 8 bytes of SHA-256 over the HTTP ETag value.

  Background:
    Given lamp 7 is "ON" at version 3 in the CoAP lamp server

  Scenario: A lamp says its profile, its rung, its validator, and that it is never fresh
    When a CoAP client gets "/lamps/7" with Prefer "forms=link, schema=link"
    Then the CoAP response is 2.05
    And the CoAP Content-Format is 65000
    And the CoAP option 65008 is "https://github.com/jogoshugh/heart-rise/hal-schema-forms"
    And the CoAP option 65012 is "forms=link, schema=link"
    And the CoAP Max-Age is 0
    And the CoAP ETag is the first 8 bytes of SHA-256 over "\"seq-3.hsf.L2\""

  Scenario: An unchanged lamp revalidates to 2.03 Valid
    Given a CoAP client got "/lamps/7" with Prefer ""
    When the CoAP client gets "/lamps/7" again with the ETag it holds
    Then the CoAP response is 2.03

  Scenario: The shared layers are served immutable, as the bytes their link names
    When a CoAP client gets "/lamps/7" with Prefer "forms=link, schema=link"
    And the CoAP client follows the representation's "hr:forms" link
    Then the CoAP response is 2.05
    And the CoAP Max-Age is 31536000
    And the CoAP body matches the integrity of the link that led to it

  Scenario Outline: An act over CoAP: <case>
    Given a CoAP client got "/lamps/7" with Prefer ""
    When the CoAP client posts <body> to "/lamps/7/actions/<action>" with <if-match>
    Then the CoAP response is <code>
    And the CoAP problem type is "<problem>"

    Examples:
      | case                  | action    | if-match          | body              | code | problem               |
      | no validator          | dim       | no If-Match       | {"brightness": 5} | 4.00 | precondition-required |
      | a stale validator     | dim       | If-Match "0000"   | {"brightness": 5} | 4.12 | stale                 |
      | an act not offered    | switch-on | the ETag it holds | {}                | 4.09 | not-offered           |
      | a body the form fails | dim       | the ETag it holds | {"brightness": 0} | 4.22 | invalid-input         |

  Scenario: A successful act answers with the lamp's new representation
    Given a CoAP client got "/lamps/7" with Prefer ""
    When the CoAP client posts {"brightness": 20} to "/lamps/7/actions/dim" with the ETag it holds
    Then the CoAP response is 2.04
    And the CoAP body links self to "/lamps/7"
    And lamp 7 is at version 4

  Scenario: An act on the bookmark that brings a lamp into being answers 2.01 Created, with the lamp and its location
    Given a CoAP client got "/" with Prefer ""
    When the CoAP client posts {"brightness": 20} to "/actions/install-lamp" with the ETag it holds
    Then the CoAP response is 2.01
    And the CoAP Location-Path is "lamps/8"
    And the CoAP body links self to "/lamps/8"
    And the CoAP ETag is the first 8 bytes of SHA-256 over "\"seq-1.hsf.L0\""

  Scenario Outline: A refused act on the bookmark over CoAP: <case>
    Given a CoAP client got "/" with Prefer ""
    When the CoAP client posts <body> to "/actions/install-lamp" with <if-match>
    Then the CoAP response is <code>
    And the CoAP problem type is "<problem>"

    Examples:
      | case                  | if-match          | body              | code | problem               |
      | no validator          | no If-Match       | {"brightness": 5} | 4.00 | precondition-required |
      | a stale validator     | If-Match "0000"   | {"brightness": 5} | 4.12 | stale                 |
      | a body the form fails | the ETag it holds | {"brightness": 0} | 4.22 | invalid-input         |

  Scenario: The service index, a search, and the profile work over CoAP
    When a CoAP client gets "/" with Prefer ""
    Then the CoAP response is 2.05
    And the CoAP body offers the form "home:search-lamps"
    When a CoAP client gets "/lamps?name=lamp" with Prefer ""
    Then the CoAP response is 2.05
    And the CoAP body lists the item "/lamps/7"
    When a CoAP client gets "/profiles/home" with Prefer ""
    Then the CoAP response is 2.05
    And the CoAP Content-Format is 65004

  Scenario: HAL-FORMS is the HTTP on-ramp, not served over CoAP
    When a CoAP client asks for HAL-FORMS at "/lamps/7"
    Then the CoAP response is 4.06

  Scenario: A lamp the server does not hold is not found
    When a CoAP client gets "/lamps/99" with Prefer ""
    Then the CoAP response is 4.04
