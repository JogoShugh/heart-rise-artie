Feature: Choices from state: a form says where a property's choices are (HAL-SCHEMA-FORMS-HR Rule 8)
  A form can fix its choices with JSON Schema's enum. Some choices depend on
  state: the players of the chosen team who have not fouled out, or the
  league's officials who are available. A property's schema carries
  hr:options, which says where those choices are: in the resource itself, by
  JSON Pointer, or in another resource, by a link. It is an instruction, not
  the values, so the form stays the same bytes for every match in the same
  phase and the affordance set stays shared. The server refuses a value that
  is not among the choices when the act arrives, and a HAL-FORMS rendering
  carries the same choices as HAL-FORMS options.

  Background:
    Given a playing match at level "senior" with home players "4 Ana, 7 Bea fouled out" and away players "5 Cy, 9 Dee"

  Scenario: The HAL Schema Forms schema carries hr:options for a choice from the resource itself
    When the schema of the act "foul" is read
    Then its property "playerNumber" carries hr:options:
      """json
      {
        "pointer": "/{team}/players",
        "valueField": "number",
        "promptField": "name",
        "where": { "fouledOut": false },
        "maxItems": 1
      }
      """

  Scenario: The HAL Schema Forms schema carries hr:options for a choice from another resource
    When the schema of the act "assignOfficial" is read
    Then its property "official" carries hr:options:
      """json
      {
        "link": { "href": "/officials{?level}", "templated": true },
        "templatePointers": { "level": "/level" },
        "items": "/officials",
        "valueField": "name",
        "promptField": "name",
        "where": { "available": true },
        "maxItems": 1
      }
      """

  Scenario: Matches in the same phase share one affordance set, whoever plays
    Given another playing match at level "junior" with home players "11 Eve" and away players "12 Fay"
    Then both matches link the same affordance set at L1 and at L2

  Scenario: At L0 the form embeds the same schema, hr:options and all
    When the match is rendered at L0
    Then its form "league:foul" has a property "playerNumber" that carries hr:options

  Scenario Outline: The server takes the choices from the state the act was checked against: <case>
    When the body <body> is checked for the act "foul" against the match
    Then <verdict>

    Examples:
      | case                     | body                                 | verdict                                                        |
      | a home player            | {"team": "home", "playerNumber": 4}  | the choices accept the body                                          |
      | a player who fouled out  | {"team": "home", "playerNumber": 7}  | the choices refuse the body at "/playerNumber" as not one of the choices |
      | the other team's player  | {"team": "home", "playerNumber": 5}  | the choices refuse the body at "/playerNumber" as not one of the choices |
      | an away player           | {"team": "away", "playerNumber": 9}  | the choices accept the body                                          |

  Scenario Outline: Choices from another resource are checked with the server's own copy of it: <case>
    When the body <body> is checked for the act "assignOfficial" against the match
    Then <verdict>

    Examples:
      | case                       | body                  | verdict                                                       |
      | an available senior        | {"official": "Kim"}   | the choices accept the body                                         |
      | a senior who is away       | {"official": "Lou"}   | the choices refuse the body at "/official" as not one of the choices  |
      | a junior, for a senior game | {"official": "Max"}  | the choices refuse the body at "/official" as not one of the choices  |

  Scenario: HAL-FORMS carries the same choices as HAL-FORMS options
    When the match is rendered in HAL-FORMS
    Then the template "foul" property "playerNumber" has inline options "4 Ana, 5 Cy, 9 Dee"
    And the template "assignOfficial" property "official" has an options link "/officials?level=senior"
    And that HAL-FORMS representation has a validator, as every Layer 1 representation does

  Scenario: The OpenAPI description carries the choices as an extension, and says them in prose
    When the OpenAPI description of the matches is rendered
    Then its schema "Foul" property "playerNumber" carries x-heart-options with the pointer "/{team}/players"
    And the description of "foul" says "playerNumber: one of the values at /{team}/players in its state"
    And the description of "assignOfficial" says "official: one of the values that /officials{?level} lists"
