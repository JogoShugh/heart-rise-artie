Feature: Cold-start response parsing

  Background:
    Given the following cold-start HAL response:
      """yaml
      id: "1"
      name: item-1
      _forms:
        update:
          href: https://example.com/items/1
          method: POST
          hash: abc123def456
          _schema:
            type: object
            properties:
              name:
                type: string
                description: A human-readable name
              count:
                type: integer
                minimum: 0
              status:
                type: string
                enum: [ACTIVE, INACTIVE]
            required: [name, status]
      """
    And the Content-Type declares the H.E.A.R.T. dual profile
    When the client parses the response

  Scenario: Affordance fields are parsed correctly
    Then the "update" affordance has:
      | href                        | hash         |
      | https://example.com/items/1 | abc123def456 |

  Scenario Outline: Schema preserves full JSON Schema keywords
    Then the "<property>" field in the "update" schema has the "<keyword>" keyword

    Examples:
      | property | keyword     |
      | name     | description |
      | count    | minimum     |
      | status   | enum        |

  Scenario Outline: Required fields are captured
    Then the "update" schema marks "<field>" as required

    Examples:
      | field  |
      | name   |
      | status |

  Scenario: Resource state is extracted independently of forms
    Then the resource state contains:
      | key  | value  |
      | id   | 1      |
      | name | item-1 |
    And the resource state does not contain the key "_forms"
