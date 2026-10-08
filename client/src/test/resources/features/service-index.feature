Feature: Enter at the service index and find a bed
  Fielding: "A REST API should be entered with no prior knowledge beyond the
  initial URI (bookmark)". ARTIE enters at the service index. A GET form is
  submitted by filling its target template from its fields (HAL Schema
  Forms, RFC 6570 form-style query for {?name}); it is safe, so it sends no
  body and no If-Match. A link is followed by choosing it by relation and
  title. ARTIE builds no URI of its own.

  Background:
    Given the service answers GET "http://garden.test/" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      ETag: "index-1"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      _links:
        self: {href: /}
      _forms:
        searchBeds:
          _links: {target: {href: "/beds{?name}", templated: true}}
          method: GET
          schema:
            type: object
            properties: {name: {type: string, minLength: 1}}
            required: [name]
        getBed:
          _links: {target: {href: "/beds/{id}", templated: true}}
          method: GET
          schema:
            type: object
            properties: {id: {type: integer, minimum: 1}}
            required: [id]
      """
    And the service answers GET "http://garden.test/beds?name=south%20border" with:
      """
      200 OK
      Content-Type: application/hal+json
      Cache-Control: no-cache
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      count: 2
      _links:
        self: {href: "/beds?name=south%20border"}
        item:
          - {href: /beds/1, title: South border east}
          - {href: /beds/2, title: South border}
      """
    And the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      label: South border
      _links:
        self: {href: /beds/2}
      _forms:
        waterCell:
          _links:
            target: {href: "/beds/{id}/actions/water-cell", templated: true}
          "hr:templatePointers": {id: /id}
          method: POST
          schema: {type: object}
      """
    And the client enters "http://garden.test/"

  Scenario: The index is a manifest like any other
    Then the manifest offers exactly "searchBeds, getBed"

  Scenario: A search form fills its query from its field, percent-encoded, and sends no body or If-Match
    When the client acts on "searchBeds" with:
      """yaml
      name: south border
      """
    Then the client sent GET "http://garden.test/beds?name=south%20border" with headers:
      | Accept | application/hal+json |
    And that request had no "If-Match" header
    And that request had no body
    And the resource links to "item" titled "South border east, South border"

  Scenario: The client follows a search result by its title
    When the client acts on "searchBeds" with:
      """yaml
      name: south border
      """
    And the client follows the "item" link titled "South border"
    Then the client's last request was GET "http://garden.test/beds/2"
    And the manifest offers exactly "waterCell"

  Scenario: A lookup form fills its path from its field
    When the client acts on "getBed" with:
      """yaml
      id: 2
      """
    Then the client's last request was GET "http://garden.test/beds/2"
    And the manifest offers exactly "waterCell"

  Scenario: Following a link the representation does not offer is refused before any request
    When the client acts on "searchBeds" with:
      """yaml
      name: south border
      """
    And the client tries to follow the "item" link titled "Herb spiral"
    Then the client raised an error mentioning "Herb spiral"
    And the client sent no request for it
