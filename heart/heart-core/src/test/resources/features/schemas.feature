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

  Scenario: A field that must not be blank needs a character that is not white space
    Jakarta's @NotBlank means at least one character that is not white
    space. minLength 1 alone lets "   " through, so the schema says it with a
    pattern too, and the form and the server agree with the annotation. JSON
    Schema searches for a pattern anywhere in the value; a HAL-FORMS client
    may match its regex against the whole value, like HTML. This pattern
    means the same either way.
    When the schema of the input class "Rename" is generated
    Then the schema is:
      """json
      {
        "title": "Rename",
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "minLength": 1,
            "pattern": "[\\s\\S]*\\S[\\s\\S]*",
            "title": "Name"
          }
        },
        "required": ["name"]
      }
      """

  Scenario: A field that must not be blank and must match its own pattern keeps both
    The field's own @Pattern gives it a pattern. JSON Schema has one
    "pattern" per schema, so the not-blank rule is added beside it, under
    "allOf", rather than replacing it.
    When the schema of the input class "Code" is generated
    Then the schema is:
      """json
      {
        "title": "Code",
        "type": "object",
        "properties": {
          "code": {
            "type": "string",
            "minLength": 1,
            "pattern": "^[A-Z ]+$",
            "allOf": [{"pattern": "[\\s\\S]*\\S[\\s\\S]*"}],
            "title": "Code"
          }
        },
        "required": ["code"]
      }
      """

  Scenario Outline: A body's not-blank field is checked as the annotation says: <body>
    When the body <body> is checked against the schema of "Rename"
    Then the body is <verdict>

    Examples:
      | body                | verdict            |
      | {"name": "desk"}    | accepted           |
      | {"name": " desk "}  | accepted           |
      | {"name": ""}        | refused at "/name" |
      | {"name": "   "}     | refused at "/name" |
      | {"name": "\\t\\n"}  | refused at "/name" |
      | {}                  | refused at "/name" |

  Scenario Outline: A body's field is checked against both its own pattern and the not-blank rule: <body>
    When the body <body> is checked against the schema of "Code"
    Then the body is <verdict>

    Examples:
      | body              | verdict            |
      | {"code": "AB C"}  | accepted           |
      | {"code": "ab"}    | refused at "/code" |
      | {"code": "   "}   | refused at "/code" |

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
