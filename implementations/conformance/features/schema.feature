Feature: A form's schema checks an act just before it is sent
  A client checks a body against the schema the service declared for the form,
  and sends nothing the schema disallows, nor anything it changed. A violation
  is a JSON Pointer and the keyword broken; at the body itself, the keyword
  alone. Here a build service job's parameters are its "ci:build" act's schema.

  Background:
    Given a client at rung L0 with the build service's credential

  Scenario Outline: What the parameters allow is sent as given: <case>
    Given the build service has a job "nightly" whose parameters are <parameters>
    When it enters the job "nightly"
    And it submits "ci:build" with <body>
    Then the act "ci:build" was sent once, with exactly <body>
    And no schema failure was noted

    Examples:
      | case                     | parameters                                                                     | body                         |
      | an undeclared property   | {"type": "object", "properties": {"text": {"type": "string"}}}                 | {"text": "hi", "mood": "ok"} |
      | an absent optional       | {"type": "object", "properties": {"text": {"type": "string"}}}                 | {}                           |
      | a format left unchecked  | {"type": "object", "properties": {"when": {"format": "date"}}}                 | {"when": "someday"}          |
      | an unknown keyword       | {"type": "object", "hr:unknown": true, "properties": {}}                       | {"text": "hi"}               |
      | a default left unapplied | {"type": "object", "properties": {"text": {"type": "string", "default": "x"}}} | {"count": "7"}               |

  Scenario Outline: What the parameters disallow is refused, and nothing is sent: <case>
    Given the build service has a job "nightly" whose parameters are <parameters>
    When it enters the job "nightly"
    And it tries to submit "ci:build" with <body>
    Then it was refused as "outside-schema" before sending, with the one violation "<violation>"

    Examples:
      | case                     | parameters                                                                                    | body                         | violation                  |
      | a missing required value | {"type": "object", "required": ["text"]}                                                      | {}                           | /text required             |
      | the wrong type           | {"type": "object", "properties": {"text": {"type": "string"}}}                                | {"text": 7}                  | /text type                 |
      | a property it forbids    | {"type": "object", "properties": {"text": {"type": "string"}}, "additionalProperties": false} | {"text": "hi", "mood": "ok"} | /mood additionalProperties |
      | a value too large        | {"type": "object", "properties": {"ml": {"type": "integer", "maximum": 600}}}                 | {"ml": 700}                  | /ml maximum                |
      | the body as a whole      | {"type": "object", "minProperties": 1}                                                        | {}                           | minProperties              |

  Scenario: Every violation is said, in one refusal
    Given the build service has a job "nightly" whose parameters are {"type": "object", "required": ["a", "b"]}
    When it enters the job "nightly"
    And it tries to submit "ci:build" with {}
    Then it was refused as "outside-schema" before sending, with the violations
      | /a required |
      | /b required |
    And it failed, saying "ci:build: /a required; /b required; nothing was sent"

  Scenario: A schema the client cannot compile blocks nothing, and is noted locally once
    Given the build service declares the parameters of its job "nightly" as {"type": "object", "properties": {"text": {"$ref": "#/nowhere"}}}, without checking them
    When it enters the job "nightly"
    And it submits "ci:build" with {"text": "hi"}
    And it submits "ci:build" with {"text": "hi"}
    Then the act "ci:build" was sent 2 times
    And the local log holds one schema failure, for "ci:build"

  Scenario: The service's own refusal of a body is still told apart
    Given the build service has a job "nightly" whose parameters are {"type": "object"}
    And the build service refuses "ci:build" as invalid input, saying "text is too long"
    When it enters the job "nightly"
    And it tries to submit "ci:build" with {"text": "hi"}
    Then it was refused as "invalid-input" with status 422
    And the refusal says "text is too long"

  Scenario: A new schema under the same identifier is compiled as itself
    Given the build service has a job "nightly" whose parameters are {"$id": "https://build.example/nightly", "type": "object"}
    When it enters the job "nightly"
    And the job "nightly" is given the parameters {"$id": "https://build.example/nightly", "type": "object", "required": ["text"]}
    And it reads the resource again
    And it tries to submit "ci:build" with {}
    Then it was refused as "outside-schema" before sending, with the one violation "/text required"
    And no schema failure was noted
