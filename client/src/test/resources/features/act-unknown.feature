Feature: Act: refuse an affordance the manifest does not offer
  SPEC §6.3, §6.8. The client acts only on what the current manifest offers.
  Anything else is refused before any request is sent.

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
          schema: {type: object}
      """
    And the client enters "http://garden.test/beds/1"

  Scenario: Acting on an affordance absent from the manifest raises an error
    When the client tries to act on "harvest" with:
      """yaml
      note: hello
      """
    Then the client raised an error mentioning "harvest"
    And the client sent no request after entering
