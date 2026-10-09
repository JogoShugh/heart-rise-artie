Feature: The service index as a JSON Home document, and a server that serves fewer rungs
  SPEC §6.11.1. Many servers start at L0. A client learns what a server
  serves from the index's JSON Home representation (draft-nottingham-json-home),
  whose resource hints (draft-ietf-httpapi-link-hint) name the dialects, the
  Prefer values honoured, and that acts need If-Match. Each response then
  says, in Preference-Applied, what it actually is. heart.max-rung caps the
  rungs this server serves.

  Background:
    Given bed 2 is PLANTED

  Scenario: GET / as application/json-home names the resources and what they support
    When a client gets the home document
    Then the response status is 200
    And the response Content-Type is "application/json-home"
    And the response Vary lists "Accept"
    And the response has an ETag
    And the home document names "item" at template "/beds/{id}"
    And the home document names "collection" at template "/beds{?name,id}"
    And the "item" hint formats offers "application/hal+json" with profile "https://github.com/jogoshugh/heart-rise/hal-schema-forms"
    And the "item" hint formats offers "application/prs.hal-forms+json" with profile "https://github.com/jogoshugh/heart-rise/hal-forms"
    And the "item" hint "precondition-req" is "etag"

  Scenario Outline: A server capped at <max> declares it, and answers L2 requests at <max>
    Given the server serves rungs up to <max>
    When a client gets the home document
    Then the "item" hint "accept-prefer" is <declared>
    When a client gets bed 2 as hal-schema-forms with Prefer "forms=link, schema=link"
    Then the response status is 200
    And the response ETag is "seq-1.hsf.<max>"
    And Preference-Applied is <applied>

    Examples:
      | max | declared                  | applied                   |
      | L2  | "forms=link, schema=link" | "forms=link, schema=link" |
      | L1  | "forms=link"              | "forms=link"              |
      | L0  | absent                    | absent                    |

  Scenario: At a capped server, an act carrying the headers it synced with still matches its ETag
    Given the server serves rungs up to L0
    And a client got bed 2 as hal-schema-forms with Prefer "forms=link, schema=link"
    When the client waters bed 2 with 100 ml using the ETag and headers it synced with
    Then the response status is 200
