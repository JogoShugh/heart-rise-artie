Feature: Sync at L2: walk three layers through an HTTP cache
  SPEC §6.3, §6.5, §6.6, §6.7. A client at L2 sends Prefer: forms=cid,
  schema=cid. Layer 1 links to an affordance set, and the set points to
  each schema by $id. The client follows those links through an ordinary
  HTTP cache: content-addressed layers are immutable, so a URL already
  held is never fetched again, and Layer 1 is revalidated with
  If-None-Match. Bytes that do not hash to their address are refused.

  Background:
    Given the service serves schema "water" at its content address:
      """yaml
      title: Water Cell
      type: object
      properties:
        milliliters: {type: integer, minimum: 1, maximum: 1000}
      required: [milliliters]
      """
    And the service serves affordance set "planted" at its content address:
      """yaml
      _forms:
        waterCell:
          _links:
            target: {href: "/beds/{id}/actions/water-cell", templated: true}
          method: POST
          contentType: application/json
          schema: {$id: "{schema:water}"}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      ETag: "seq-1.hsf.L2"
      Vary: Accept, Prefer, Accept-Profile
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      _links:
        self: {href: /beds/2}
        http://api.example.org/rels/forms: {href: "{set:planted}", type: application/hal+json}
      """
    And the client works at rung L2

  Scenario: The client asks for L2 and walks Layer 1, the set, then the schema
    When the client enters "http://garden.test/beds/2"
    Then the client sent these requests:
      | GET http://garden.test/beds/2       |
      | GET http://garden.test{set:planted}   |
      | GET http://garden.test{schema:water}  |
    And the client sent GET "http://garden.test/beds/2" with headers:
      | Prefer | forms=cid, schema=cid |
    And the "milliliters" field in the "waterCell" schema has the "minimum" keyword
    And the client holds the ETag "seq-1.hsf.L2"

  Scenario: A second bed in the same state costs one request: the set and schema come from the cache
    Given the service answers GET "http://garden.test/beds/3" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      ETag: "seq-4.hsf.L2"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 3
      status: PLANTED
      _links:
        self: {href: /beds/3}
        http://api.example.org/rels/forms: {href: "{set:planted}"}
      """
    When the client enters "http://garden.test/beds/2"
    And the client enters "http://garden.test/beds/3"
    Then the client sent these requests:
      | GET http://garden.test/beds/2          |
      | GET http://garden.test{set:planted}   |
      | GET http://garden.test{schema:water}  |
      | GET http://garden.test/beds/3          |
    And the "waterCell" affordance is POST to "/beds/{id}/actions/water-cell"

  Scenario: Re-syncing an unchanged bed is one conditional request answered 304
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      304 Not Modified
      ETag: "seq-1.hsf.L2"
      Cache-Control: no-cache
      """
    When the client enters "http://garden.test/beds/2"
    And the client enters "http://garden.test/beds/2"
    Then the client sent these requests:
      | GET http://garden.test/beds/2          |
      | GET http://garden.test{set:planted}   |
      | GET http://garden.test{schema:water}  |
      | GET http://garden.test/beds/2          |
    And the last request had header "If-None-Match" set to "\"seq-1.hsf.L2\""
    And the manifest offers exactly "waterCell"

  Scenario: Two affordances that share one schema fetch it once
    Given the service serves affordance set "two-waterings" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema: {$id: "{schema:water}"}
        waterAgain:
          _links: {target: {href: "/beds/{id}/actions/water-again", templated: true}}
          method: POST
          schema: {$id: "{schema:water}"}
      """
    And the service answers GET "http://garden.test/beds/5" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L2"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 5
      _links:
        http://api.example.org/rels/forms: {href: "{set:two-waterings}"}
      """
    When the client enters "http://garden.test/beds/5"
    Then the client sent these requests:
      | GET http://garden.test/beds/5               |
      | GET http://garden.test{set:two-waterings}  |
      | GET http://garden.test{schema:water}       |
    And the manifest offers exactly "waterCell, waterAgain"

  Scenario: An act at L2 expands the set's template from Layer 1 and carries the L2 ETag
    Given the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L2"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
        http://api.example.org/rels/forms: {href: "{set:planted}"}
      """
    When the client enters "http://garden.test/beds/2"
    And the client acts on "waterCell" with:
      """yaml
      milliliters: 100
      """
    Then the client sent POST "http://garden.test/beds/2/actions/water-cell" with headers:
      | If-Match | "seq-1.hsf.L2" |
    And the client holds the ETag "seq-2.hsf.L2"

  Scenario: Bytes that do not hash to their address are refused, and not cached
    Given the service answers GET "http://garden.test/affordance-sets/sha256-0000000000000000" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: public, max-age=31536000, immutable

      _forms: {}
      """
    And the service answers GET "http://garden.test/beds/8" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L2"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 8
      _links:
        http://api.example.org/rels/forms: {href: /affordance-sets/sha256-0000000000000000}
      """
    When the client tries to enter "http://garden.test/beds/8"
    Then the client raised an error mentioning "does not hash to"
    And the cache holds nothing for "http://garden.test/affordance-sets/sha256-0000000000000000"
