Feature: Choices from state: ARTIE offers only the choices a form allows now
  HAL-SCHEMA-FORMS-HR Rule 8. A property's schema may carry hr:options:
  where its choices are, in the resource itself by JSON Pointer, or in
  another resource by a link. ARTIE resolves them with deterministic code:
  from the state it holds, or by reading the link. It shows the reasoner
  the closed set, refuses a selection outside it, and never sends a value
  that is not a choice.

  Background:
    Given the service answers GET "http://league.test/matches/1" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 1
      phase: PLAYING
      level: senior
      home:
        players:
          - {number: 4, name: Ana, fouledOut: false}
          - {number: 7, name: Bea, fouledOut: true}
      away:
        players:
          - {number: 5, name: Cy, fouledOut: false}
          - {number: 9, name: Dee, fouledOut: false}
      _links:
        self: {href: /matches/1}
      _forms:
        foul:
          title: Record a foul
          _links: {target: {href: /matches/1/actions/foul}}
          method: POST
          contentType: application/json
          schema:
            type: object
            properties:
              team: {type: string, enum: [home, away], title: Team}
              playerNumber:
                type: integer
                title: Player
                "hr:options":
                  pointer: "/{team}/players"
                  valueField: number
                  promptField: name
                  where: {fouledOut: false}
                  maxItems: 1
            required: [team, playerNumber]
        assign-official:
          title: Assign an official
          _links: {target: {href: /matches/1/actions/assign-official}}
          method: POST
          contentType: application/json
          schema:
            type: object
            properties:
              official:
                type: string
                title: Official
                "hr:options":
                  link: {href: "/officials{?level}", templated: true}
                  templatePointers: {level: /level}
                  items: /officials
                  valueField: name
                  promptField: name
                  where: {available: true}
                  maxItems: 1
            required: [official]
      """
    And the service answers GET "http://league.test/officials?level=senior" with:
      """
      200 OK
      Content-Type: application/json
      Cache-Control: no-cache
      ETag: "list-1"

      officials:
        - {name: Kim, level: senior, available: true}
        - {name: Lou, level: senior, available: false}
      """
    And the service answers POST "http://league.test/matches/1/actions/foul" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-2.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 1
      phase: PLAYING
      _forms: {}
      """

  Scenario: Choices from the resource itself depend on the other fields
    When the client enters "http://league.test/matches/1"
    Then the choices of "foul" for "playerNumber", by the field "team", are
      | team | value | prompt |
      | home | 4     | Ana    |
      | away | 5     | Cy     |
      | away | 9     | Dee    |

  Scenario: Choices from another resource are read by following the link, filled from the state
    When the client enters "http://league.test/matches/1"
    Then the choices of "assign-official" for "official", with {}, are
      | value | prompt |
      | Kim   | Kim    |
    And the client sent these requests after entering:
      | GET http://league.test/officials?level=senior |

  Scenario: A value outside the choices is never sent
    When the client enters "http://league.test/matches/1"
    And the client tries to act on "foul" with:
      """yaml
      team: home
      playerNumber: 7
      """
    Then the client raised an error mentioning "playerNumber: 7 is not one of the choices"
    And the client sent no request after entering

  Scenario: A choice is sent as usual
    When the client enters "http://league.test/matches/1"
    And the client acts on "foul" with:
      """yaml
      team: home
      playerNumber: 4
      """
    Then the client's last request was POST "http://league.test/matches/1/actions/foul"

  Scenario: The reasoner is shown the closed set, and a selection outside it is rejected
    Given the reasoner will select "foul" with {"team": "home", "playerNumber": 7}
    When the agent handles "foul by Bea" at "http://league.test/matches/1"
    Then the reasoner was shown "foul"'s "playerNumber" with the choices "when team is home: 4 (Ana); when team is away: 5 (Cy), 9 (Dee)"
    And the agent rejected the selection because "playerNumber: 7 is not one of the choices"
