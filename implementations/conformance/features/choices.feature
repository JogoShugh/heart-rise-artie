Feature: A form's choices from state
  HAL-SCHEMA-FORMS-HR Rule 8: a property of a form may take its choices from
  state, with hr:options. They come from the resource that offers the form,
  at a JSON Pointer that may name another field of the same form, or from
  another resource, read like any Layer 1 resource. Only items whose members
  equal every one of "where" are choices. A client offers only those, and
  sends no value outside them.

  The service here is a small one the steps serve. It has one game. The
  game's sport is basketball. Its home players are 4 Ana and 9 Cy, and 7 Bea,
  who has fouled out; its away player is 3 Dee. Its agents are Priya and Lena,
  on duty, and Marco, off duty, all for basketball, and Tom for football.
  The game offers record-foul, whose playerNumber is one of the chosen team's
  players who have not fouled out, and call-agent, whose agent is one of the
  agents for the game's sport who are on duty.

  Background:
    Given a service whose game takes its players and its agents from state
    And a client at rung L0 with no credential
    When it enters that service

  Scenario: A choice that depends on another field: the chosen team's players who have not fouled out
    Then the choices for "playerNumber" of "record-foul", by "team", are
      | team | value | prompt |
      | home | 4     | Ana    |
      | home | 9     | Cy     |
      | away | 3     | Dee    |

  Scenario: Choices from another resource, its address filled from the game's state
    Then the choices for "agent" of "call-agent" are
      | value | prompt |
      | priya | Priya  |
      | lena  | Lena   |
    And the agents were read at "/agents?skill=basketball"

  Scenario: The other resource is revalidated, not read again in full
    When it resolves the choices for "agent" of "call-agent" twice
    Then the second read of the agents carried the ETag of the first, and was answered 304

  Scenario Outline: A value outside the choices is refused before anything is sent: <case>
    When it tries to submit "<form>" with <body>
    Then it was refused before sending, because "<says>"

    Examples:
      | case                       | form        | body                                 | says                                     |
      | a player who fouled out    | record-foul | {"team": "home", "playerNumber": 7}  | playerNumber: 7 is not one of the choices |
      | a player of the other team | record-foul | {"team": "away", "playerNumber": 4}  | playerNumber: 4 is not one of the choices |
      | an agent off duty          | call-agent  | {"agent": "marco"}                   | agent: marco is not one of the choices    |

  Scenario: A value among the choices is sent
    When it submits "record-foul" with {"team": "home", "playerNumber": 9}
    Then the act "record-foul" was sent once
