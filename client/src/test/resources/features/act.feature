Feature: Act: submit a form the manifest offers
  SPEC §6.3, §6.8. The client sends the act to the target the server gave,
  with the payload as JSON, and the response becomes the new state and
  manifest.

  Background:
    Given the service answers GET "http://garden.test/beds/1" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-0.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 1
      status: EMPTY
      _forms:
        sowSeed:
          _links:
            target: {href: /beds/1/actions/sow-seed}
          method: POST
          contentType: application/json
          schema:
            type: object
            properties:
              seedType: {type: string}
            required: [seedType]
      """
    And the service answers POST "http://garden.test/beds/1/actions/sow-seed" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile"

      id: 1
      status: PLANTED
      _forms:
        waterCell:
          _links:
            target: {href: /beds/1/actions/water-cell}
          method: POST
          contentType: application/json
          schema:
            type: object
            properties:
              milliliters: {type: integer}
            required: [milliliters]
      """
    When the client enters "http://garden.test/beds/1"
    And the client acts on "sowSeed" with:
      """yaml
      seedType: basil
      """

  Scenario: The act goes to the resolved target, with the payload as JSON
    Then the client sent POST "http://garden.test/beds/1/actions/sow-seed" with headers:
      | Content-Type | application/json |
    And that request had the JSON body {"seedType":"basil"}

  Scenario: Resource state reflects the transition
    Then the resource state contains:
      | key    | value   |
      | status | PLANTED |

  Scenario: Manifest reflects the transition
    Then the manifest offers exactly "waterCell"
