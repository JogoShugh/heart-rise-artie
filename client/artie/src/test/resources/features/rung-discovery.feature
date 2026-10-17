Feature: Which rung a response is at, and which rung to ask for
  SPEC §6.4, §6.11.1. A client asks for a rung with Prefer, but a server may
  ignore any preference (RFC 7240). Preference-Applied says what the server
  honoured, so the client takes the rung of each response from it: no header
  means L0, forms=link means L1, forms=link and schema=link mean L2. The
  client never fails because it got less than it asked for.

  Background:
    Given the client works at rung L2

  Scenario: An L2 answer, confirmed by Preference-Applied
    Given the service serves schema "water" at its content address:
      """yaml
      type: object
      properties:
        milliliters: {type: integer, minimum: 1}
      """
    And the service serves affordance set "planted" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema: {$ref: "{schema:water}", "hr:integrity": "{integrity:schema:water}"}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L2"
      Preference-Applied: forms=link, schema=link
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
        https://w3id.org/heart-rise/rels/forms: {href: "{set:planted}", integrity: "{integrity:set:planted}"}
      """
    When the client enters "http://garden.test/beds/2"
    Then the current representation is at rung L2
    And the manifest offers exactly "waterCell"

  Scenario: A server that ignores Prefer answers at L0, and the client carries on
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
      _forms:
        waterCell:
          _links: {target: {href: /beds/2/actions/water-cell}}
          method: POST
          schema:
            type: object
            properties:
              milliliters: {type: integer, minimum: 1}
      """
    When the client enters "http://garden.test/beds/2"
    Then the client sent GET "http://garden.test/beds/2" with headers:
      | Prefer | forms=link, schema=link |
    And the current representation is at rung L0
    And the client sent these requests:
      | GET http://garden.test/beds/2 |
    And the "milliliters" field in the "waterCell" schema has the "minimum" keyword

  Scenario: Asked for L2, the server honours forms=link only, and the set carries its schemas
    Given the service serves affordance set "planted-l1" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema:
            type: object
            properties:
              milliliters: {type: integer, minimum: 1}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L1"
      Preference-Applied: forms=link
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
        https://w3id.org/heart-rise/rels/forms: {href: "{set:planted-l1}", integrity: "{integrity:set:planted-l1}"}
      """
    When the client enters "http://garden.test/beds/2"
    Then the current representation is at rung L1
    And the client sent these requests:
      | GET http://garden.test/beds/2         |
      | GET http://garden.test{set:planted-l1} |
    And the "milliliters" field in the "waterCell" schema has the "minimum" keyword

  Scenario: A forms link without Preference-Applied is not followed: no header means L0
    The response is at L0 whatever its body links, so there is no
    affordance set to fetch. Only embedded _forms count, and there are none,
    so the client offers nothing and sends nothing more (Buster #37).
    Given the service serves affordance set "planted" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema: {type: object}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L2"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
        https://w3id.org/heart-rise/rels/forms: {href: "{set:planted}", integrity: "{integrity:set:planted}"}
      """
    When the client enters "http://garden.test/beds/2"
    Then the current representation is at rung L0
    And the client's current manifest is empty
    And the client sent these requests:
      | GET http://garden.test/beds/2 |

  Scenario Outline: The client asks for the best the home document offers, up to its own rung: <server>
    The service index's JSON Home representation declares the dialects
    (formats) and the Prefer values honoured (accept-prefer). The client
    prefers HAL Schema Forms, and asks for no rung above the one it is set to.
    Given the service answers GET "http://garden.test/" with:
      """
      200 OK
      Content-Type: application/json-home

      resources:
        item:
          hrefTemplate: "/beds/{id}"
          hints:
            formats: <formats>
            <accept-prefer>
      """
    When the client discovers "http://garden.test/"
    And the client tries to enter "http://garden.test/beds/2"
    Then the client sent GET "http://garden.test/beds/2" with headers:
      | Accept | <accept> |
    And that request <prefer>

    Examples:
      | server               | formats                                                         | accept-prefer                            | accept                         | prefer                                   |
      | L2, both dialects    | {application/hal+json: {}, application/prs.hal-forms+json: {}} | accept-prefer: [forms=link, schema=link] | application/hal+json           | asked for "forms=link, schema=link"      |
      | L1                   | {application/hal+json: {}, application/prs.hal-forms+json: {}} | accept-prefer: [forms=link]              | application/hal+json           | asked for "forms=link"                   |
      | L0, HAL Schema Forms | {application/hal+json: {}}                                      |                                          | application/hal+json           | had no "Prefer" header                   |
      | L0, HAL-FORMS only   | {application/prs.hal-forms+json: {}}                            |                                          | application/prs.hal-forms+json | had no "Prefer" header                   |

  Scenario: A client set to L1 asks for L1 even when the server offers L2
    Given the client works at rung L1
    And the service answers GET "http://garden.test/" with:
      """
      200 OK
      Content-Type: application/json-home

      resources:
        item:
          hints:
            formats: {application/hal+json: {}}
            accept-prefer: [forms=link, schema=link]
      """
    When the client discovers "http://garden.test/"
    And the client tries to enter "http://garden.test/beds/2"
    Then that request asked for "forms=link"

  Scenario: With no home document, the client keeps its own settings
    Given the service answers GET "http://garden.test/" with:
      """
      406 Not Acceptable
      """
    When the client discovers "http://garden.test/"
    And the client tries to enter "http://garden.test/beds/2"
    Then the client sent GET "http://garden.test/beds/2" with headers:
      | Accept | application/hal+json    |
      | Prefer | forms=link, schema=link |

  Scenario: The home document promised L2, but the response is L0: the response wins
    Given the service answers GET "http://garden.test/" with:
      """
      200 OK
      Content-Type: application/json-home

      resources:
        item:
          hints:
            formats: {application/hal+json: {}}
            accept-prefer: [forms=link, schema=link]
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _forms:
        waterCell:
          _links: {target: {href: /beds/2/actions/water-cell}}
          method: POST
          schema: {type: object}
      """
    When the client discovers "http://garden.test/"
    And the client enters "http://garden.test/beds/2"
    Then the current representation is at rung L0
    And the manifest offers exactly "waterCell"
