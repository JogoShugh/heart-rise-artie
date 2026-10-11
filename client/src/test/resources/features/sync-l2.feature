Feature: Sync at L2: walk three layers through an HTTP cache
  SPEC §6.3, §6.5, §6.6, §6.7. A client at L2 sends Prefer: forms=link,
  schema=link. Layer 1 links to an affordance set, and the set points to
  each schema by $ref. The client follows those links through an ordinary
  HTTP cache: content-addressed layers are immutable, so a URL already
  held is never fetched again, and Layer 1 is revalidated with
  If-None-Match. URIs are opaque: the client checks bytes only against the
  integrity value of the link that led to them, and against Repr-Digest
  when the server sends it (HAL-SCHEMA-FORMS-HR Rules 2 and 4).

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
          schema: {$ref: "{schema:water}", "hr:integrity": "{integrity:schema:water}"}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      ETag: "seq-1.hsf.L2"
      Preference-Applied: forms=link, schema=link
      Vary: Accept, Prefer, Accept-Profile
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      _links:
        self: {href: /beds/2}
        https://github.com/jogoshugh/heart-rise/rels/forms: {href: "{set:planted}", type: application/hal+json, integrity: "{integrity:set:planted}"}
      """
    And the client works at rung L2

  Scenario: The client asks for L2 and walks Layer 1, the set, then the schema
    When the client enters "http://garden.test/beds/2"
    Then the client sent these requests:
      | GET http://garden.test/beds/2       |
      | GET http://garden.test{set:planted}   |
      | GET http://garden.test{schema:water}  |
    And the client sent GET "http://garden.test/beds/2" with headers:
      | Prefer | forms=link, schema=link |
    And the "milliliters" field in the "waterCell" schema has the "minimum" keyword
    And the client holds the ETag "seq-1.hsf.L2"

  Scenario: A second bed in the same state costs one request: the set and schema come from the cache
    Given the service answers GET "http://garden.test/beds/3" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      ETag: "seq-4.hsf.L2"
      Preference-Applied: forms=link, schema=link
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 3
      status: PLANTED
      _links:
        self: {href: /beds/3}
        https://github.com/jogoshugh/heart-rise/rels/forms: {href: "{set:planted}", integrity: "{integrity:set:planted}"}
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
      Preference-Applied: forms=link, schema=link
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

  Scenario: Two affordances that share one schema fetch it once (an older server's $id pointer is still read)
    An older server sends no integrity values, so this client accepts unchecked links.
    Given the client accepts links without integrity
    And the service serves affordance set "two-waterings" at its content address:
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
      Preference-Applied: forms=link, schema=link
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 5
      _links:
        https://github.com/jogoshugh/heart-rise/rels/forms: {href: "{set:two-waterings}"}
      """
    When the client enters "http://garden.test/beds/5"
    Then the client sent these requests:
      | GET http://garden.test/beds/5               |
      | GET http://garden.test{set:two-waterings}  |
      | GET http://garden.test{schema:water}       |
    And the manifest offers exactly "waterCell, waterAgain"

  Scenario: An act at L2 expands the set's template from Layer 1 and repeats the headers it synced with
    HTTP is stateless: the act carries the same Accept, Accept-Profile and
    Prefer as the sync, so the server can select the representation the
    If-Match names and compare the whole entity-tag (RFC 9110 §13.1.1).
    Given the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L2"
      Preference-Applied: forms=link, schema=link
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      _links:
        self: {href: /beds/2}
        https://github.com/jogoshugh/heart-rise/rels/forms: {href: "{set:planted}", integrity: "{integrity:set:planted}"}
      """
    When the client enters "http://garden.test/beds/2"
    And the client acts on "waterCell" with:
      """yaml
      milliliters: 100
      """
    Then the client sent POST "http://garden.test/beds/2/actions/water-cell" with headers:
      | If-Match       | "seq-1.hsf.L2"                                              |
      | Accept         | application/hal+json                                        |
      | Accept-Profile | <https://github.com/jogoshugh/heart-rise/hal-schema-forms> |
      | Prefer         | forms=link, schema=link                                     |
    And the client holds the ETag "seq-2.hsf.L2"
    And the manifest offers exactly "waterCell"
    And the "milliliters" field in the "waterCell" schema has the "minimum" keyword

  Scenario: The client reads nothing from a URL, even one shaped like a hash
    Given the service serves affordance set "odd-name" at "/affordance-sets/0000000000000000":
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema: {type: object}
      """
    And bed 8 links to affordance set "odd-name" with integrity "{integrity:set:odd-name}"
    When the client enters "http://garden.test/beds/8"
    Then the manifest offers exactly "waterCell"
    And the cache holds "http://garden.test/affordance-sets/0000000000000000"

  Scenario: A set whose bytes do not match the link's integrity is refused, and not cached
    Given the service serves affordance set "planted-v2" at "/forms/planted":
      """yaml
      _forms: {}
      """
    And bed 9 links to affordance set "planted-v2" with integrity "sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    When the client tries to enter "http://garden.test/beds/9"
    Then the client raised an error mentioning "integrity"
    And the cache holds nothing for "http://garden.test/forms/planted"

  Scenario: A schema whose bytes do not match its hr:integrity is refused, and not cached
    Given the service serves affordance set "wrong-schema-pin" at its content address:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema: {$ref: "{schema:water}", "hr:integrity": "sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}
      """
    And bed 10 links to affordance set "wrong-schema-pin" with integrity "{integrity:set:wrong-schema-pin}"
    When the client tries to enter "http://garden.test/beds/10"
    Then the client raised an error mentioning "integrity"
    And the cache holds nothing for "http://garden.test{schema:water}"

  Scenario: A Repr-Digest that disagrees with the bytes is refused, and not cached
    Given the service serves affordance set "liar" at "/forms/liar" with Repr-Digest "sha-256=:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=:":
      """yaml
      _forms: {}
      """
    And bed 11 links to affordance set "liar" with integrity "{integrity:set:liar}"
    When the client tries to enter "http://garden.test/beds/11"
    Then the client raised an error mentioning "Repr-Digest"
    And the cache holds nothing for "http://garden.test/forms/liar"

  Scenario: A Repr-Digest that matches, in RFC 8941 syntax, is accepted
    Given the service serves affordance set "honest" at "/forms/honest" with its own Repr-Digest:
      """yaml
      _forms:
        waterCell:
          _links: {target: {href: "/beds/{id}/actions/water-cell", templated: true}}
          method: POST
          schema: {type: object}
      """
    And bed 12 links to affordance set "honest" with integrity "{integrity:set:honest}"
    When the client enters "http://garden.test/beds/12"
    Then the manifest offers exactly "waterCell"
    And the cache holds "http://garden.test/forms/honest"
