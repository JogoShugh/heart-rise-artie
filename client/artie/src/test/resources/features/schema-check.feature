Feature: ARTIE's own side of the schema check
  What a body the schema allows or disallows does is held to every client in
  implementations/conformance/features/schema.feature. Here, what is ARTIE's
  own: a schema linked at L2 is checked as an inline one is, and a schema it
  cannot compile is compiled once by its integrity value and reported once in
  its event log, with no field values.

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
