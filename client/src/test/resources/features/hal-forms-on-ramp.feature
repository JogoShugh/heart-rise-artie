Feature: L5 from the client: ARTIE at the HAL-FORMS on-ramp
  SPEC §6.1 (0.5 decision), §6.10.6. HAL-FORMS is served at L0 only. ARTIE
  can speak it: it asks by media type and profile, reads _templates, and
  turns each template's inline properties into the same JSON Schema model
  it uses for HAL Schema Forms, so the rest of the client, and the Reason
  step, see one shape whichever dialect it came from. ARTIE keeps the
  server's key as the affordance name rather than guess another. A HEART
  server keys every template by its act's name; another server may key a
  lone template "default", as HAL-FORMS suggests, and ARTIE keeps that too.

  Background:
    Given the service answers GET "http://garden.test/beds/1" with:
      """
      200 OK
      Content-Type: application/prs.hal-forms+json
      ETag: "seq-0.hf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-forms>; rel="profile"

      id: 1
      status: EMPTY
      _links:
        self: {href: /beds/1}
      _templates:
        sowSeed:
          method: POST
          target: /beds/1/actions/sow-seed
          properties:
            - {name: depthCm, type: number, min: 1, max: 10, prompt: Depth in cm}
            - {name: seedType, type: text, required: true, regex: "^(?=\\s*\\S).*$"}
      """
    And the client works in dialect hal-forms

  Scenario: The client asks for HAL-FORMS by media type and profile, never with Prefer
    When the client enters "http://garden.test/beds/1"
    Then the client sent GET "http://garden.test/beds/1" with headers:
      | Accept         | application/prs.hal-forms+json                       |
      | Accept-Profile | <https://github.com/jogoshugh/heart-rise/hal-forms> |
    And that request had no "Prefer" header

  Scenario: The template is an affordance named by its key, with a resolved target
    When the client enters "http://garden.test/beds/1"
    Then the manifest offers exactly "sowSeed"
    And the "sowSeed" affordance is POST to "http://garden.test/beds/1/actions/sow-seed"
    And the resource state contains:
      | key    | value |
      | status | EMPTY |

  Scenario Outline: HAL-FORMS properties become JSON Schema: <property> has <keyword>
    When the client enters "http://garden.test/beds/1"
    Then the "<property>" field in the "sowSeed" schema has the "<keyword>" keyword
    Examples:
      | property | keyword     |
      | depthCm  | minimum     |
      | depthCm  | maximum     |
      | depthCm  | description |
      | depthCm  | type        |
      | seedType | pattern     |

  Scenario: Required properties are carried into the schema
    When the client enters "http://garden.test/beds/1"
    Then the "sowSeed" schema marks "seedType" as required

  Scenario: An act from a HAL-FORMS manifest is conditional like any other
    Given the service answers POST "http://garden.test/beds/1/actions/sow-seed" with:
      """
      200 OK
      Content-Type: application/prs.hal-forms+json
      ETag: "seq-1.hf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-forms>; rel="profile"

      id: 1
      status: PLANTED
      _templates: {}
      """
    When the client enters "http://garden.test/beds/1"
    And the client acts on "sowSeed" with:
      """yaml
      seedType: basil
      """
    Then the client sent POST "http://garden.test/beds/1/actions/sow-seed" with headers:
      | If-Match       | "seq-0.hf.L0"                                        |
      | Content-Type   | application/json                                     |
      | Accept         | application/prs.hal-forms+json                       |
      | Accept-Profile | <https://github.com/jogoshugh/heart-rise/hal-forms> |
    And that request had no "Prefer" header
    And the client holds the ETag "seq-1.hf.L0"

  Scenario: A HAL-FORMS client cannot be set to a rung above L0
    When the client is set to rung L2
    Then the client raised an error mentioning "L0 only"

  Scenario: A HAL-FORMS client enters at the service index, searches by name, and follows a result
    HAL-FORMS GET templates put their fields in the query string, replacing
    any query the target had (the HTML mutate-action-URL algorithm, which
    encodes as application/x-www-form-urlencoded: a space is "+").
    Given the service answers GET "http://garden.test/" with:
      """
      200 OK
      Content-Type: application/prs.hal-forms+json
      Cache-Control: no-cache
      ETag: "index-hf"
      Link: <https://github.com/jogoshugh/heart-rise/hal-forms>; rel="profile"

      _links:
        self: {href: /}
      _templates:
        searchBeds:
          method: GET
          target: /beds
          properties:
            - {name: name, type: search, required: true, minLength: 1, prompt: Name}
        getBed:
          method: GET
          target: /beds
          properties:
            - {name: id, type: number, required: true, min: 1, prompt: Bed id}
      """
    And the service answers GET "http://garden.test/beds?name=south+border" with:
      """
      200 OK
      Content-Type: application/prs.hal-forms+json
      Cache-Control: no-cache
      Link: <https://github.com/jogoshugh/heart-rise/hal-forms>; rel="profile"

      count: 1
      _links:
        self: {href: "/beds?name=south+border"}
        item:
          - {href: /beds/1, title: South border}
      """
    When the client enters "http://garden.test/"
    Then the manifest offers exactly "searchBeds, getBed"
    When the client acts on "searchBeds" with:
      """yaml
      name: south border
      """
    Then the client's last request was GET "http://garden.test/beds?name=south+border"
    And that request had no "If-Match" header
    And that request had no body
    When the client follows the "item" link titled "South border"
    Then the client's last request was GET "http://garden.test/beds/1"
    And the manifest offers exactly "sowSeed"

  Scenario: A HAL-FORMS client looks a bed up by id through the query
    Given the service answers GET "http://garden.test/" with:
      """
      200 OK
      Content-Type: application/prs.hal-forms+json
      ETag: "index-hf"
      Link: <https://github.com/jogoshugh/heart-rise/hal-forms>; rel="profile"

      _templates:
        getBed:
          method: GET
          target: /beds?stale=1
          properties:
            - {name: id, type: number, required: true, min: 1}
      """
    And the service answers GET "http://garden.test/beds?id=1" with:
      """
      200 OK
      Content-Type: application/prs.hal-forms+json
      Link: <https://github.com/jogoshugh/heart-rise/hal-forms>; rel="profile"

      count: 1
      _links:
        item:
          - {href: /beds/1, title: South border}
      """
    When the client enters "http://garden.test/"
    And the client acts on "getBed" with:
      """yaml
      id: 1
      """
    Then the client's last request was GET "http://garden.test/beds?id=1"
    And the resource links to "item" titled "South border"
