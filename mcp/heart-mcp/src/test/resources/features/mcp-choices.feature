Feature: heart-mcp shows a form's choices as a closed set
  HAL-SCHEMA-FORMS-HR Rule 8: a property may take its choices from state,
  with hr:options, from the resource that offers the act or from another one.
  ARTIE resolves them, never the model. heart-mcp shows the host's model each
  act's schema with those choices as a JSON Schema enum, their prompts in the
  property's description, and no hr:options, so a model sees only values the
  service will take and never where they came from.

  Background:
    Given heart-mcp holds a game whose home team's players are
      | number | name | fouledOut |
      | 4      | Ana  | false     |
      | 7      | Bea  | true      |
      | 9      | Cy   | false     |

  Scenario: Choices from the resource's own state are shown as an enum, with their prompts
    Given the game offers "game:record-foul", whose playerNumber is one of "/home/players" not fouled out
    When heart-mcp shows what it holds
    Then the act "game:record-foul" shows playerNumber as one of
      | value | prompt |
      | 4     | Ana    |
      | 9     | Cy     |
    And no act's schema in the view says "hr:options"

  Scenario: Choices from another resource are read by ARTIE and shown the same way
    Given the agents resource lists
      | name  | onDuty |
      | priya | true   |
      | marco | false  |
      | lena  | true   |
    And the game offers "game:call-agent", whose agent is one of the agents on duty
    When heart-mcp shows what it holds
    Then the act "game:call-agent" shows agent as one of
      | value | prompt |
      | priya | priya  |
      | lena  | lena   |
    And no act's schema in the view says "hr:options"
    And no address appears in the view
