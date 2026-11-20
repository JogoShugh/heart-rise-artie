Feature: What a property's options offer, at the edges
  HAL-SCHEMA-FORMS-HR Rule 8, where hr:options is odd or thin: plain values,
  default member names, options that point nowhere or at no list, fields not
  filled yet, and lists read from another resource as plain JSON. The build
  service's job "nightly" takes a "size" parameter whose options each scenario
  writes, over state the job is shown holding.

  Background:
    Given a client at rung L0 with the build service's credential

  Scenario Outline: What a property's options offer: <case>
    Given the build service's job also holds <state>
    And the build service has a job "nightly" whose parameters are {"properties": {"size": {"hr:options": <options>}}}
    When it enters the job "nightly"
    Then the values for "size" of "ci:build" are <values>
    And the resource was read 1 times

    Examples:
      | case                                     | options                                   | state                             | values   |
      | plain values, where nothing is asked     | {"pointer": "/sizes"}                     | {"sizes": ["s", 2, null, ["x"]]}  | ["s", 2] |
      | no plain value, where members are asked  | {"pointer": "/sizes", "where": {"a": 1}}  | {"sizes": ["s"]}                  | []       |
      | neither pointer nor link                 | {}                                        | {}                                | []       |
      | a pointer to no list                     | {"pointer": "/sizes"}                     | {"sizes": 3}                      | []       |
      | a field not filled yet, empty            | {"pointer": "/by/{team}"}                 | {"by": {"": ["x"]}}               | ["x"]    |

  Scenario: A choice's value and prompt have default names, and the prompt falls back to the value
    Given the build service's job also holds {"sizes": [{"value": 1, "prompt": "one"}, {"value": 2}, {"prompt": "none"}]}
    And the build service has a job "nightly" whose parameters are {"properties": {"size": {"hr:options": {"pointer": "/sizes"}}}}
    When it enters the job "nightly"
    Then the choices for "size" of "ci:build" are
      | value | prompt |
      | 1     | one    |
      | 2     | 2      |

  Scenario: A field in a pointer is escaped
    Given the build service's job also holds {"by": {"a/b~c": ["x"]}}
    And the build service has a job "nightly" whose parameters are {"properties": {"size": {"hr:options": {"pointer": "/by/{team}"}}}}
    When it enters the job "nightly"
    Then the choices for "size" of "ci:build", by "team", are
      | team  | value | prompt |
      | a/b~c | x     | x      |

  Scenario: Only properties with options have choices
    Given the build service's job also holds {"sizes": []}
    And the build service has a job "nightly" whose parameters are {"properties": {"size": {"hr:options": {"pointer": "/sizes"}}, "text": {"type": "string"}}}
    When it enters the job "nightly"
    Then the form "ci:build" has choices for exactly "size"

  Scenario Outline: The choices stand between a value and the wire: <case>
    Given the build service's job also holds {"sizes": ["s"], "none": []}
    And the build service has a job "nightly" whose parameters are <parameters>
    When it enters the job "nightly"
    And it tries to submit "ci:build" with <fields>
    Then it was refused as "not-a-choice" before sending, because "<says>"

    Examples:
      | case                         | parameters                                                                           | fields               | says                              |
      | required, with no choice now | {"required": ["size"], "properties": {"size": {"hr:options": {"pointer": "/none"}}}} | {}                   | size has no choices now           |
      | one of a list                | {"properties": {"size": {"hr:options": {"pointer": "/sizes"}}}}                      | {"size": ["s", "z"]} | size: z is not one of the choices |

  Scenario: Every value outside its choices is said
    Given the build service's job also holds {"sizes": ["s"]}
    And the build service has a job "nightly" whose parameters are {"properties": {"a": {"hr:options": {"pointer": "/sizes"}}, "b": {"hr:options": {"pointer": "/sizes"}}}}
    When it enters the job "nightly"
    And it tries to submit "ci:build" with {"a": 1, "b": 2}
    Then it failed, saying "a: 1 is not one of the choices; b: 2 is not one of the choices; nothing was sent"

  Scenario: Leaving out a property with no choices now needs no choice
    Given the build service's job also holds {"none": []}
    And the build service has a job "nightly" whose parameters are {"properties": {"size": {"hr:options": {"pointer": "/none"}}}}
    When it enters the job "nightly"
    And it submits "ci:build" with {}
    Then the act "ci:build" was sent 1 times

  Scenario Outline: Choices from another resource: <case>
    Given the build service serves "<path>" with status <status> and the body <body>
    And the build service has a job "nightly" whose parameters are {"properties": {"size": {"hr:options": <options>}}}
    When it enters the job "nightly"
    Then the values for "size" of "ci:build" are <values>

    Examples:
      | case                  | path          | status | body            | options                                        | values |
      | a bare list           | /sizes        | 200    | ["x"]           | {"link": {"href": "/sizes"}}                   | ["x"]  |
      | a list inside         | /sizes        | 200    | {"list": ["y"]} | {"link": {"href": "/sizes"}, "items": "/list"} | ["y"]  |
      | the root as a pointer | /sizes        | 200    | ["z"]           | {"link": {"href": "/sizes"}, "items": ""}      | ["z"]  |
      | a refused read        | /sizes        | 404    | ["x"]           | {"link": {"href": "/sizes"}}                   | []     |
      | an untemplated link   | /sizes%7Bx%7D | 200    | ["w"]           | {"link": {"href": "/sizes{x}"}}                | ["w"]  |

  Scenario: Another resource is read as plain JSON, never from a cache
    Given the build service serves "/sizes" with status 200 and the body ["x"]
    And the build service has a job "nightly" whose parameters are {"properties": {"size": {"hr:options": {"link": {"href": "/sizes"}}}}}
    When it enters the job "nightly"
    And it resolves the choices for "size" of "ci:build" twice
    Then the last read accepted "application/json", with no cache
    And the last read carried no validator

  Scenario: A resource that changed replaces what the client kept
    Given the build service serves "/sizes" with the validator "1" and the body ["x"]
    And the build service has a job "nightly" whose parameters are {"properties": {"size": {"hr:options": {"link": {"href": "/sizes"}}}}}
    When it enters the job "nightly"
    Then the values for "size" of "ci:build" are ["x"]
    When the build service serves "/sizes" with the validator "2" and the body ["y"]
    Then the values for "size" of "ci:build" are ["y"]
