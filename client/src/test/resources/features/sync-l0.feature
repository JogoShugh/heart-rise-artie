Feature: Sync at L0: enter a service and read one collapsed response
  SPEC §6.1, §6.3, §6.4, §6.7. With no Prefer header the server sends state,
  forms and full schemas in one response. The client asks for the
  hal-schema-forms dialect by media type and profile, checks the profile the
  server names in Link, keeps state apart from the manifest, resolves each
  target against the URL it entered at, and holds the ETag for its next act.

  Background:
    Given the service answers GET "http://garden.test/beds/2" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://github.com/jogoshugh/heart-rise/hal-schema-forms>; rel="profile", <https://github.com/jogoshugh/heart-rise>; rel="profile"

      id: 2
      label: Bed 2
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
            $id: /schemas/sha256-7ab6ce2f03278bdf
            title: Water Cell
            type: object
            properties:
              milliliters: {type: integer, minimum: 1, maximum: 1000, description: Water to add}
            required: [milliliters]
      """

  Scenario: The client asks for the dialect and profile, at rung L0
    When the client enters "http://garden.test/beds/2"
    Then the client sent GET "http://garden.test/beds/2" with headers:
      | Accept         | application/hal+json                                        |
      | Accept-Profile | <https://github.com/jogoshugh/heart-rise/hal-schema-forms> |
    And that request had no "Prefer" header

  Scenario: State is held apart from the manifest
    When the client enters "http://garden.test/beds/2"
    Then the resource state contains:
      | key    | value   |
      | id     | 2       |
      | status | PLANTED |
    And the resource state does not contain the key "_forms"
    And the manifest offers exactly "waterCell"

  Scenario: Each target is resolved against the URL the client entered at
    When the client enters "http://garden.test/beds/2"
    Then the "waterCell" affordance is POST to "http://garden.test/beds/2/actions/water-cell"

  Scenario Outline: The schema keeps every JSON Schema keyword: <keyword>
    When the client enters "http://garden.test/beds/2"
    Then the "milliliters" field in the "waterCell" schema has the "<keyword>" keyword

    Examples:
      | keyword     |
      | minimum     |
      | maximum     |
      | description |

  Scenario: Required fields are captured
    When the client enters "http://garden.test/beds/2"
    Then the "waterCell" schema marks "milliliters" as required

  Scenario: The client holds the ETag for its next act
    When the client enters "http://garden.test/beds/2"
    Then the client holds the ETag "seq-1.hsf.L0"

  Scenario Outline: A response that does not name the hal-schema-forms profile is refused: <case>
    Given the service answers GET "http://garden.test/beds/9" with:
      """
      200 OK
      Content-Type: application/hal+json
      <profile header>

      id: 9
      """
    When the client tries to enter "http://garden.test/beds/9"
    Then the client raised an error mentioning "profile"

    Examples:
      | case                 | profile header                                                          |
      | another profile      | Link: <https://github.com/jogoshugh/heart-rise/hal-forms>; rel="profile" |
      | no profile link      | Link: </beds/9>; rel="self"                                             |
      | withdrawn media-type | X-Note: profile only in Content-Type parameter                          |
