Feature: A body outside the form's schema is never sent
  SPEC §6.3: a client populates form fields within the constraints the
  schema declares. Just before an act is sent, ARTIE checks its fields
  against the form's own JSON Schema (2020-12), inline or linked. It refuses
  only what the schema disallows: it never coerces a value, fills in a
  default or removes a field; it ignores keywords it does not know, such as
  hr:options; and it does not enforce formats. A refusal sends nothing.
  A schema ARTIE cannot compile blocks nothing: the act is sent and the
  service decides, and the event log says so once, with no field values.
  Each schema is compiled once, kept by its integrity value, or by a digest
  of it when it came inline.

  Background:
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      agents: [priya, zed]
      _links:
        self: {href: /beds/2}
      _forms:
        waterCell:
          _links:
            target: {href: /beds/2/actions/water-cell}
          method: POST
          contentType: application/json
          schema:
            type: object
            properties:
              milliliters: {type: integer, minimum: 1, maximum: 1000}
              unit: {type: string, default: ml}
              contact: {type: string, format: email}
              agent: {type: string, "hr:options": {pointer: /agents}}
            required: [milliliters]
      """
    And the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
      """

  Scenario Outline: Fields the schema allows are sent exactly as given: <why>
    When the client enters "http://garden.test/beds/2"
    And the client acts on "waterCell" with:
      """yaml
      <fields>
      """
    Then the client sent these requests after entering:
      | POST http://garden.test/beds/2/actions/water-cell |
    And that request had the JSON body <fields>

    Examples:
      | why                                     | fields                                            |
      | within its limits, no default filled in | {"milliliters": 250}                              |
      | a field it does not declare is kept     | {"milliliters": 250, "note": "by the gate"}       |
      | a format is not enforced                | {"milliliters": 250, "contact": "not an address"} |
      | a keyword it does not know is ignored   | {"milliliters": 250, "agent": "zed"}              |

  Scenario Outline: Fields the schema disallows are refused, and nothing is sent: <why>
    When the client enters "http://garden.test/beds/2"
    And the client tries to act on "waterCell" with:
      """yaml
      <fields>
      """
    Then the client raised a "OutsideSchema" refusal
    And the client raised an error mentioning "<mentions>"
    And the client sent no request after entering

    Examples:
      | why                                   | fields                 | mentions     |
      | below its minimum                     | {"milliliters": 0}     | /milliliters |
      | a string is not coerced to an integer | {"milliliters": "250"} | /milliliters |
      | a required field is missing           | {"unit": "ml"}         | milliliters  |

  Scenario: A linked schema is checked as an inline one is
    Given the service serves schema "water" at its content address:
      """yaml
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
            target: {href: /beds/3/actions/water-cell}
          method: POST
          contentType: application/json
          schema: {$ref: "{schema:water}", "hr:integrity": "{integrity:schema:water}"}
      """
    And bed 3 links to affordance set "planted"
    And the client works at rung L2
    When the client enters "http://garden.test/beds/3"
    And the client tries to act on "waterCell" with:
      """yaml
      milliliters: 0
      """
    Then the client raised a "OutsideSchema" refusal
    And the client sent no request after entering

  Scenario: A schema that cannot be compiled blocks nothing, and is reported once with no field values
    Two beds link to one affordance set, so both forms have one schema, kept by its integrity value.
    Given the client keeps an event log
    And the service serves schema "broken" at its content address:
      """yaml
      type: object
      properties:
        milliliters: {type: integer, minimum: one}
      """
    And the service serves affordance set "broken" at its content address:
      """yaml
      _forms:
        waterCell:
          _links:
            target: {href: "/beds/{id}/actions/water-cell", templated: true}
          method: POST
          contentType: application/json
          schema: {$ref: "{schema:broken}", "hr:integrity": "{integrity:schema:broken}"}
      """
    And bed 3 and bed 5 both link to affordance set "broken"
    And the service answers POST "http://garden.test/beds/3/actions/water-cell" with:
      """
      200 OK
      Content-Type: application/hal+json
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 3
      """
    And the service answers POST "http://garden.test/beds/5/actions/water-cell" with:
      """
      200 OK
      Content-Type: application/hal+json
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 5
      """
    And the client works at rung L2
    When the client enters "http://garden.test/beds/3"
    And the client acts on "waterCell" with:
      """yaml
      milliliters: 7777
      """
    And the client enters "http://garden.test/beds/5"
    And the client acts on "waterCell" with:
      """yaml
      milliliters: 8888
      """
    Then the client sent these requests:
      | GET http://garden.test/beds/3                     |
      | GET http://garden.test{set:broken}                |
      | GET http://garden.test{schema:broken}             |
      | POST http://garden.test/beds/3/actions/water-cell |
      | GET http://garden.test/beds/5                     |
      | POST http://garden.test/beds/5/actions/water-cell |
    And the event log has 1 "schema.uncompiled" line
    And no event line holds any of these values
      | 7777 |
      | 8888 |
