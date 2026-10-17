Feature: Choosing the representation over HTTP: dialect, profile and rung
  SPEC §6.1, §6.4 and §6.11.1. heart-core decides from the header values
  (its negotiation.feature); these scenarios check that the HTTP binding
  reads them from the right HTTP headers and says what it chose:
  Content-Type, Link rel="profile", Vary and Preference-Applied. The
  server's maximum rung caps the rungs it serves.

  Background:
    Given lamp 7 is "ON" at version 3 in the lamp server

  Scenario Outline: Accept-Profile picks the dialect when Accept leaves it open: Accept "<accept>", Accept-Profile "<profile>"
    When a client gets "/lamps/7" with Accept "<accept>" and Accept-Profile "<profile>"
    Then the response status is 200
    And the response has Content-Type "<type>"
    And the response links its profile "https://w3id.org/heart-rise/<dialect>"
    And the response carries its forms under "<forms>"

    Examples:
      | accept | profile                                                     | type                           | dialect          | forms      |
      | */*    | <https://w3id.org/heart-rise/hal-schema-forms> | application/hal+json           | hal-schema-forms | _forms     |
      |        | <https://w3id.org/heart-rise/hal-forms>        | application/prs.hal-forms+json | hal-forms        | _templates |
      |        |                                                             | application/prs.hal-forms+json | hal-forms        | _templates |
      |        | <https://example.org/unknown-profile>                       | application/prs.hal-forms+json | hal-forms        | _templates |

  Scenario: Layer 1 also names the parent heart-rise profile
    When a client gets "/lamps/7" as hal-schema-forms
    Then the response links its profile "https://w3id.org/heart-rise"

  Scenario: HAL-FORMS is the L0 on-ramp: it ignores layering preferences, so it does not vary on Prefer
    When a client gets "/lamps/7" as hal-forms with Prefer "forms=link, schema=link"
    Then the response has ETag "\"seq-3.hf.L0.{digest}\""
    And the response has Vary "Accept, Accept-Profile"
    And the response carries its forms under "_templates"
    And the response has no Preference-Applied header

  Scenario Outline: A Prefer token the server does not know degrades to L0: <prefer>
    When a client gets "/lamps/7" as hal-schema-forms with Prefer "<prefer>"
    Then the response has ETag "\"seq-3.hsf.L0.{digest}\""
    And the response carries its forms under "_forms"
    And the response has no Preference-Applied header

    Examples:
      | prefer        |
      | embed=schemas |
      | forms=cid     |

  Scenario Outline: A server capped at <max> answers a request for L2 at <max>, and its home document says so
    Given the server serves rungs up to <max>
    When a client gets "/lamps/7" as hal-schema-forms with Prefer "forms=link, schema=link"
    Then the response has ETag "\"seq-3.hsf.<max>.{digest}\""
    And Preference-Applied is <applied>
    When a client gets "/" with Accept "application/json-home"
    Then the home document's "lamp" hint "accept-prefer" is <applied>

    Examples:
      | max | applied                   |
      | L2  | "forms=link, schema=link" |
      | L1  | "forms=link"              |
      | L0  | absent                    |

  Scenario: At a capped server, an act carrying the headers it synced with still matches its ETag
    Given the server serves rungs up to L0
    When a client gets "/lamps/7" as hal-schema-forms with Prefer "forms=link, schema=link"
    And the client posts {"brightness": 20} to "/lamps/7/actions/dim" with the ETag and headers it synced with
    Then the response status is 200
