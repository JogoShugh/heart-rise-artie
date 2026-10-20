Feature: ARTIE over CoAP (SPEC §6.0.1)
  A coap: URI is reached through the CoAP binding, over UDP, with the same
  core rules as HTTP. The binding carries the requested rendering in the
  Accept option and in the experimental options 65000 Accept-Profile and
  65004 Prefer, says 65003 No-Cache on every Layer 1 read, and reads the
  applied rendering from 65008 Profile and 65012 Preference-Applied. Each
  request carries the client's W3C Trace Context in 65020 traceparent, the
  same value HTTP's traceparent header carries. These scenarios run against
  a scripted CoAP server on this machine.

  Background:
    Given a CoAP service holds bed 1 EMPTY

  Scenario: A read over CoAP asks for the rendering and is never answered from a cache
    Given the client works at rung L2
    When the client enters the CoAP bed
    Then the CoAP request had Accept 65000
    And the CoAP request had option 65000 "<https://w3id.org/heart-rise/hal-schema-forms>"
    And the CoAP request had option 65004 "forms=link, schema=link"
    And the CoAP request had option 65003
    And nothing went over HTTP

  Scenario: The applied rendering and the validator come from the CoAP options
    Given the CoAP service answers at rung L1
    When the client enters the CoAP bed
    Then the current representation is at rung L1
    And the client holds the CoAP bed's validator

  Scenario: An act over CoAP carries the 8-byte validator and takes the new representation
    When the client enters the CoAP bed
    And the client acts on "sow" with:
      """yaml
      seedType: tomato
      """
    Then the CoAP act carried If-Match with the bed's validator
    And the manifest offers exactly "water"
    And nothing went over HTTP

  Scenario: An act goes over CoAP by the method its form says
    The bed's sow form says POST; once planted, its water form says PUT.
    When the client enters the CoAP bed
    And the client acts on "sow" with:
      """yaml
      seedType: tomato
      """
    And the client acts on "water" with:
      """yaml
      milliliters: 250
      """
    Then the CoAP acts were
      | code | path         |
      | POST | beds/1/sow   |
      | PUT  | beds/1/water |

  Scenario: An act that brings a resource into being over CoAP: ARTIE then holds the new resource
    SPEC §6.0, "New resource": 2.01 Created, the new resource at its
    Location-Path. The client holds that resource next, as over HTTP.
    Given the CoAP service will bring bed 2 into being on the next act
    When the client enters the CoAP bed
    And the client acts on "sow" with:
      """yaml
      seedType: tomato
      """
    Then the resource state contains:
      | key    | value |
      | status | NEW   |
    And nothing went over HTTP

  Scenario: Every CoAP request carries traceparent: one trace per client, a new span per request
    When the client enters the CoAP bed
    And the client acts on "sow" with:
      """yaml
      seedType: tomato
      """
    Then every CoAP request had option 65020 with a W3C traceparent
    And the CoAP requests share a trace id but not a span id

  Scenario: An event about a CoAP request names that request's span
    Given the client keeps an event log
    When the client enters the CoAP bed
    And the client acts on "sow" with:
      """yaml
      seedType: tomato
      """
    Then the "act.sent" event names the span of the CoAP act

  Scenario Outline: A CoAP refusal <code> is the core's <refusal>
    Given the CoAP service will refuse the next act with <code> and problem type "<problem>"
    When the client enters the CoAP bed
    And the client tries to act on "sow" with:
      """yaml
      seedType: tomato
      """
    Then the client raised a "<refusal>" refusal

    Examples:
      | code | problem       | refusal           |
      | 4.12 | stale         | StaleAct          |
      | 4.09 | not-offered   | IllegalTransition |
      | 4.22 | invalid-input | InvalidPayload    |
