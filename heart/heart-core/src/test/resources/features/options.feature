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
    Given a playing match at level "senior" with these players
      | team | number | name | fouled out |
      | home | 4      | Ana  | no         |
      | home | 7      | Bea  | yes        |
      | away | 5      | Cy   | no         |
      | away | 9      | Dee  | no         |

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
    Given another playing match at level "junior" with these players
      | team | number | name | fouled out |
      | home | 11     | Eve  | no         |
      | away | 12     | Fay  | no         |
    Then both matches link the same affordance set at L1 and at L2

  Scenario: At L0 the form embeds the same schema, hr:options and all
    When the match is rendered at L0
    Then its form "league:foul" has a property "playerNumber" that carries hr:options

  Scenario Outline: The server checks the choices against the state the act was checked against: <case>
    The fouls' choices come from the match itself; the officials' from the server's own copy of another resource.
    When the body <body> is checked for the act "<act>" against the match
    Then <verdict>

    Examples:
      | case                        | act            | body                                | verdict                                                                  |
      | a home player               | foul           | {"team": "home", "playerNumber": 4} | the choices accept the body                                              |
      | a player who fouled out     | foul           | {"team": "home", "playerNumber": 7} | the choices refuse the body at "/playerNumber" as not one of the choices |
      | the other team's player     | foul           | {"team": "home", "playerNumber": 5} | the choices refuse the body at "/playerNumber" as not one of the choices |
      | an away player              | foul           | {"team": "away", "playerNumber": 9} | the choices accept the body                                              |
      | an available senior         | assignOfficial | {"official": "Kim"}                 | the choices accept the body                                              |
      | a senior who is away        | assignOfficial | {"official": "Lou"}                 | the choices refuse the body at "/official" as not one of the choices     |
      | a junior, for a senior game | assignOfficial | {"official": "Max"}                 | the choices refuse the body at "/official" as not one of the choices     |

  Scenario: HAL-FORMS carries the same choices as HAL-FORMS options, filled in where a link could not say where they are
    A HAL-FORMS options link must answer the list itself: it has no pointer to the list inside a document, as
    hr:options's "items" is. The officials' list sits under "/officials" in its document, so the server fills
    those choices in from its own copy, as it does for a page a person reads.
    When the match is rendered in HAL-FORMS
    Then the template "league:foul" property "playerNumber" has these inline options
      | value | prompt |
      | 4     | Ana    |
      | 5     | Cy     |
      | 9     | Dee    |
    And the template "league:assign-official" property "official" has these inline options
      | value | prompt |
      | Kim   | Kim    |
    And that HAL-FORMS representation has a validator, as every Layer 1 representation does

  # A page for a person is one resource's, so the server fills each choice in, as HAL-FORMS's inline options do:
  # from the resource itself, and from its own copy of another resource. A browser follows no link to find them.
  Scenario: HTML offers each property's choices in its act's dialog, filled in by the server
    When the match is rendered as HTML
    Then the dialogs offer exactly these choices
      | act            | field        | value | prompt |
      | foul           | playerNumber | 4     | Ana    |
      | foul           | playerNumber | 5     | Cy     |
      | foul           | playerNumber | 9     | Dee    |
      | assignOfficial | official     | Kim   | Kim    |

  # A JSON object's members have no order, so the canonical schema lists them by name. A person filling a form
  # does need one: the team before its players, whose choices depend on it. The forms for people follow the
  # order the input declares.
  Scenario Outline: <representation> asks for an act's fields in the order its input declares them, the team before its player
    When the match is rendered <rendered>
    Then the "foul" act asks for its fields in this order
      | field        |
      | team         |
      | playerNumber |

    Examples:
      | representation | rendered     |
      | HAL-FORMS      | in HAL-FORMS |
      | HTML           | as HTML      |

  Scenario: The OpenAPI description carries the choices as an extension, and says them in prose
    When the OpenAPI description of the matches is rendered
    Then its schema "Foul" property "playerNumber" carries x-heart-options with the pointer "/{team}/players"
    And the description of "foul" says "playerNumber: one of the values at /{team}/players in its state"
    And the description of "assignOfficial" says "official: one of the values that /officials{?level} lists"
