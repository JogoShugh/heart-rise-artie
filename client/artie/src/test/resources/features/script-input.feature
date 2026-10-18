Feature: A script's input, checked by ARTIE before it is sent, and refused by whoever refused it
  SPEC §6.3: a client populates a form's fields within the constraints its
  schema declares. Run by a script, ARTIE checks the fields against the
  form's schema before sending, as it does for its reasoner: a value the
  schema doesn't allow is refused by the client, and nothing is sent. A
  value the schema allows, which the server still refuses (422), is refused
  by the server, in the server's words. A refusal names who refused it.

  Background:
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 2
      status: PLANTED
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
              milliliters: {type: integer, minimum: 1}
            required: [milliliters]
      """

  Scenario: A value the schema doesn't allow is refused by ARTIE, and nothing is sent
    When this script runs with ARTIE
      """kotlin
      heart("http://garden.test/beds/2") {
          act("waterCell", "milliliters" to 0)
      }
      """
    Then the script stopped at step 1, "waterCell": invalid input, refused by the client: "milliliters is below 1"
    And the client sent these requests:
      | GET http://garden.test/beds/2 |

  Scenario: A value the schema allows, refused by the server, is the server's refusal
    Given the service answers POST "http://garden.test/beds/2/actions/water-cell" with:
      """
      422 Unprocessable Content
      Content-Type: application/problem+json

      type: https://w3id.org/heart-rise/problems/invalid-input
      title: The body does not match the form's schema
      status: 422
      violations:
        - {pointer: /milliliters, message: must be at most 1000}
      """
    When this script runs with ARTIE
      """kotlin
      heart("http://garden.test/beds/2") {
          act("waterCell", "milliliters" to 5000)
      }
      """
    Then the script stopped at step 1, "waterCell": invalid input, refused by the server: "/milliliters: must be at most 1000"
    And the client sent these requests:
      | GET http://garden.test/beds/2                     |
      | POST http://garden.test/beds/2/actions/water-cell |
