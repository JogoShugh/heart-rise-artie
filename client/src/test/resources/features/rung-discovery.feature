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
          schema: {$ref: "{schema:water}"}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L2"
      Preference-Applied: forms=link, schema=link
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
        https://github.com/jogoshugh/heart-rise/rels/forms: {href: "{set:planted}"}
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
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

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
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
        https://github.com/jogoshugh/heart-rise/rels/forms: {href: "{set:planted-l1}"}
      """
    When the client enters "http://garden.test/beds/2"
    Then the current representation is at rung L1
    And the client sent these requests:
      | GET http://garden.test/beds/2         |
      | GET http://garden.test{set:planted-l1} |
    And the "milliliters" field in the "waterCell" schema has the "minimum" keyword
