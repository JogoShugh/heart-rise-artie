Feature: A form's schema checks an act just before it is sent
  The schema is the one the service declared for the form, inline or linked,
  found at run time. A client compiles it once and refuses, sending nothing,
  only what the schema disallows. The service remains the authority: its own
  refusals still count, and a schema the client cannot compile blocks nothing.

  A violation is said the same by every client, whatever validator it uses:
  the JSON Pointer of the value it is about, then the schema keyword that
  value breaks ("/text type"). A missing required member, or a member
  additionalProperties forbids, is said at that member's own pointer. A
  violation of the body as a whole is the keyword alone.

  The service here is a small one the steps serve. It has one resource, which
  offers the act "note".

  Scenario Outline: What the schema does not disallow is sent: <case>
    Given a service offering "note" with the schema <schema>
    And a client at rung L0 with no credential
    When it enters that service
    And it submits "note" with <body>
    Then the act "note" was sent 1 times
    And no schema failure was noted

    Examples:
      | case                   | schema                                                                      | body                        |
      | an undeclared property | {"type": "object", "properties": {"text": {"type": "string"}}}              | {"text": "hi", "mood": "ok"} |
      | an absent optional     | {"type": "object", "properties": {"text": {"type": "string"}}}              | {}                          |
      | a format left unchecked | {"type": "object", "properties": {"when": {"format": "date"}}}             | {"when": "someday"}         |
      | an unknown keyword     | {"type": "object", "hr:unknown": true, "properties": {}}                    | {"text": "hi"}              |

  Scenario Outline: What the schema disallows is refused before anything is sent: <case>
    Given a service offering "note" with the schema <schema>
    And a client at rung L0 with no credential
    When it enters that service
    And it tries to submit "note" with <body>
    Then it was refused as "outside-schema" before sending, because "<says>"

    Examples:
      | case                       | schema                                                                                      | body                        | says                  |
      | a missing required value   | {"type": "object", "required": ["text"]}                                                    | {}                          | /text required             |
      | the wrong type             | {"type": "object", "properties": {"text": {"type": "string"}}}                              | {"text": 7}                 | /text type                 |
      | a property it forbids      | {"type": "object", "properties": {"text": {"type": "string"}}, "additionalProperties": false} | {"text": "hi", "mood": "ok"} | /mood additionalProperties |
      | a value too large          | {"type": "object", "properties": {"ml": {"type": "integer", "maximum": 600}}}               | {"ml": 700}                 | /ml maximum                |

  Scenario: A value is sent as given, with nothing added or coerced
    Given a service offering "note" with the schema {"type": "object", "properties": {"text": {"type": "string", "default": "x"}}}
    And a client at rung L0 with no credential
    When it enters that service
    And it submits "note" with {"count": "7"}
    Then the act "note" was sent with exactly {"count": "7"}

  Scenario: A schema the client cannot compile blocks nothing, and is noted locally once
    Given a service offering "note" with the schema {"type": "object", "properties": {"text": {"$ref": "#/nowhere"}}}
    And a client at rung L0 with no credential
    When it enters that service
    And it submits "note" with {"text": "hi"}
    And it submits "note" with {"text": "hi"}
    Then the act "note" was sent 2 times
    And the local log holds one schema failure, for "note"

  Scenario: A refusal the service makes for a body is still told apart
    Given a service offering "note" with the schema {"type": "object"}
    And that service refuses "note" as invalid input, saying "text is too long"
    And a client at rung L0 with no credential
    When it enters that service
    And it tries to submit "note" with {"text": "hi"}
    Then it was refused as "invalid-input" with status 422
    And the refusal says "text is too long"

  Scenario: Every way a body breaks the schema is said
    Given a service offering "note" with the schema {"type": "object", "required": ["a", "b"]}
    And a client at rung L0 with no credential
    When it enters that service
    And it tries to submit "note" with {}
    Then it failed, saying "note: /a required; /b required; nothing was sent"

  Scenario: A violation of the whole body has no address to show
    Given a service offering "note" with the schema {"type": "object", "minProperties": 1}
    And a client at rung L0 with no credential
    When it enters that service
    And it tries to submit "note" with {}
    Then the only violation is "minProperties"

  Scenario: A new schema with the same identifier is compiled as itself
    Given a service offering "note" with the schema {"$id": "https://garden.example/note", "type": "object"}
    And a client at rung L0 with no credential
    When it enters that service
    And that service now declares the schema {"$id": "https://garden.example/note", "type": "object", "required": ["text"]}
    And it reads the resource again
    And it tries to submit "note" with {}
    Then it was refused as "outside-schema" before sending, because "/text required"
    And no schema failure was noted
