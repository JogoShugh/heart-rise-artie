Feature: One schema per act: generated from its input class, and enforced as served
  spec/PLAN-HEART-LIBRARY-DOMAINS-EVAL.md, A4 rule 1. An act's JSON Schema is
  generated from its input class: Jakarta Validation for the constraints,
  OpenAPI's @Schema for the words. A body is checked against that same
  schema, so what a form says and what the server enforces cannot drift.

  Scenario: The schema of the lamp's dim act, generated from its input class
    When the schema of the input class "Dim" is generated
    Then the schema is:
      """json
      {
        "title": "Dim",
        "type": "object",
        "properties": {
          "brightness": {
            "type": "integer",
            "minimum": 1,
            "maximum": 100,
            "title": "Brightness",
            "description": "Percent of full brightness",
            "examples": [50]
          },
          "label": {
            "type": "string",
            "minLength": 1,
            "title": "Label",
            "examples": ["reading"]
          }
        },
        "required": ["brightness"]
      }
      """

  Scenario: A class with no fields is an object with no properties
    When the schema of the input class "SwitchOff" is generated
    Then the schema is:
      """json
      {"title": "Switch off", "type": "object", "properties": {}}
      """

  Scenario Outline: A body is checked against the generated schema: <body>
    When the body <body> is checked against the schema of "Dim"
    Then the body is <verdict>

    Examples:
      | body                                  | verdict                           |
      | {"brightness": 50}                    | accepted                          |
      | {"brightness": 50, "label": "lamp"}   | accepted                          |
      | {"brightness": 0}                     | refused at "/brightness"          |
      | {"brightness": 101}                   | refused at "/brightness"          |
      | {"brightness": 2.5}                   | refused at "/brightness"          |
      | {"brightness": "lots"}                | refused at "/brightness"          |
      | {}                                    | refused at "/brightness"          |
      | {"brightness": 50, "label": ""}       | refused at "/label"               |
      | {"label": ""}                         | refused at "/brightness, /label"  |
      |                                       | refused at "/brightness"          |
      | {"brightness":                        | malformed                         |
