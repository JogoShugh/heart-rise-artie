Feature: Layer 1 is never fresh; the client says so on every request
  SPEC §6.1, §6.3, §6.7 (revised 2026-10-10). A resource's state can change
  the moment after it is read, so no Cache-Control a response carries makes
  it safe to reuse unchecked. The client sends the request directive
  Cache-Control: no-cache (RFC 9111 §5.2.1.4) on every Layer 1 request, with
  If-None-Match once it holds an ETag. Every cache on the path, its own and
  any shared one, must then check with the server. Affordance sets and
  schemas stay cacheable: their integrity values pin the bytes.

  Background:
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
      Cache-Control: private, max-age=3600
      ETag: "seq-1.hsf.L2"
      Preference-Applied: forms=link, schema=link
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
        https://github.com/jogoshugh/heart-rise/rels/forms: {href: "{set:planted}", integrity: "{integrity:set:planted}"}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      304 Not Modified
      ETag: "seq-1.hsf.L2"
      Preference-Applied: forms=link, schema=link
      """
    And the client works at rung L2

  Scenario: Every Layer 1 request says no-cache; set and schema requests do not
    When the client enters "http://garden.test/beds/2"
    Then the client sent GET "http://garden.test/beds/2" with headers:
      | Cache-Control | no-cache |
    And the request for "http://garden.test{set:planted}" had no "Cache-Control" header
    And the request for "http://garden.test{schema:water}" had no "Cache-Control" header

  Scenario: A bed the server said to reuse for an hour is still checked on the next sync
    When the client enters "http://garden.test/beds/2"
    And the client enters "http://garden.test/beds/2"
    Then the client sent these requests:
      | GET http://garden.test/beds/2         |
      | GET http://garden.test{set:planted}   |
      | GET http://garden.test{schema:water}  |
      | GET http://garden.test/beds/2         |
    And the last request had header "If-None-Match" set to "\"seq-1.hsf.L2\""
    And the last request had header "Cache-Control" set to "no-cache"
    And the manifest offers exactly "waterCell"

  Scenario: Behind a shared cache, a second client's read of the bed still reaches the server
    Given the second client shares the client's cache
    When the client enters "http://garden.test/beds/2"
    And a second client enters "http://garden.test/beds/2"
    Then the client sent these requests:
      | GET http://garden.test/beds/2         |
      | GET http://garden.test{set:planted}   |
      | GET http://garden.test{schema:water}  |
      | GET http://garden.test/beds/2         |
    And the last request had header "Cache-Control" set to "no-cache"

  Scenario: At L0 the single request says no-cache too
    Given the client works at rung L0
    And the service answers GET "http://garden.test/beds/7" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      ETag: "seq-1.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 7
      _links:
        self: {href: /beds/7}
      """
    When the client enters "http://garden.test/beds/7"
    Then the client sent GET "http://garden.test/beds/7" with headers:
      | Cache-Control | no-cache |
