Feature: ARTIE's own side of choices from state: the reasoner sees the closed set
  What a form's choices are, and that a value outside them is never sent, is
  held to every client in implementations/conformance/features/choices.feature.
  Here, what is ARTIE's own: it shows the reasoner the closed set, with each
  choice's prompt, and rejects a selection outside it.

  Background:
    Given the service answers GET "http://league.test/matches/1" with:
      """
      200 OK
      Content-Type: application/hal+json
      ETag: "seq-1.hsf.L0"
      Link: <https://w3id.org/heart-rise/hal-schema-forms>; rel="profile"

      id: 1
      phase: PLAYING
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
      """

  Scenario: The reasoner is shown the closed set, and a selection outside it is rejected
    Given the reasoner will select "foul" with {"team": "home", "playerNumber": 7}
    When the agent handles "foul by Bea" at "http://league.test/matches/1"
    Then the reasoner was shown "foul"'s "playerNumber" with the choices "when team is home: 4 (Ana); when team is away: 5 (Cy), 9 (Dee)"
    And the agent rejected the selection because "playerNumber: 7 is not one of the choices"
