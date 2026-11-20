Feature: The garden over both bindings (SPEC §6.0.1)
  The server answers over CoAP (RFC 7252) as well as HTTP, from one
  declaration. heart-coap's own features test the CoAP binding alone, and
  heart-spring's the HTTP binding; this one checks what only a server with
  both shows: that they serve the same representation.

  Background:
    Given bed 2 is PLANTED

  Scenario: CoAP and HTTP serve the same representation
    When a CoAP client gets "/beds/2" with Prefer "forms=link, schema=link"
    And a client got bed 2 as hal-schema-forms with Prefer "forms=link, schema=link"
    Then the CoAP body is the same JSON as the HTTP body
