Feature: Choosing the dialect, the rung and the validator
  SPEC §6.1, §6.4 and §6.2. These choices depend only on the values a client
  sent (Accept, Accept-Profile, Prefer, If-Match), never on how they were
  carried, so every binding makes them the same way.

  Scenario Outline: The dialect for Accept "<accept>" and Accept-Profile "<profile>"
    When a client sends Accept "<accept>" and Accept-Profile "<profile>"
    Then the dialect is <dialect>

    Examples:
      | accept                                               | profile                                                     | dialect    |
      |                                                      |                                                             | HALFORMS   |
      | application/hal+json                                 |                                                             | JSONSCHEMA |
      | APPLICATION/HAL+JSON                                 |                                                             | JSONSCHEMA |
      | application/prs.hal-forms+json;q=0.5, application/hal+json |                                                   | JSONSCHEMA |
      | application/hal+json, application/prs.hal-forms+json |                                                             | JSONSCHEMA |
      | */*                                                  | <https://github.com/jogoshugh/heart-rise/hal-schema-forms>  | JSONSCHEMA |
      | */*                                                  | <https://example.org/unknown>                               | HALFORMS   |

  Scenario: Accept that names no dialect and no wildcard is refused
    When a client sends Accept "text/html" and Accept-Profile ""
    Then the dialect is refused as not acceptable

  Scenario Outline: JSON Home is wanted when Accept ranks it above both dialects
    When a client sends Accept "<accept>"
    Then JSON Home is <wanted>

    Examples:
      | accept                                              | wanted     |
      | application/json-home                               | wanted     |
      | application/json-home, application/hal+json         | wanted     |
      | application/hal+json, application/json-home         | not wanted |
      | application/json-home;q=0.5, application/hal+json   | not wanted |
      | application/hal+json                                | not wanted |

  Scenario Outline: Plain JSON is wanted when Accept names application/json above both dialects: <accept>
    The OpenAPI twin's rendering: the state alone, with every control stripped.
    A wildcard never selects it, so a client that names nothing still gets HEART.

    When a client sends Accept "<accept>"
    Then plain JSON is <wanted>

    Examples:
      | accept                                       | wanted     |
      | application/json                             | wanted     |
      | application/json, application/hal+json;q=0.5 | wanted     |
      | application/hal+json, application/json       | not wanted |
      | application/json;q=0.5, application/hal+json | not wanted |
      | */*                                          | not wanted |
      |                                              | not wanted |

  Scenario Outline: The rung for Prefer "<prefer>"
    When a client sends Prefer "<prefer>"
    Then the rung is <rung>

    Examples:
      | prefer                     | rung |
      |                            | L0   |
      | forms=link                 | L1   |
      | forms=link, schema=link    | L2   |
      | FORMS="link"; x=1          | L1   |
      | schema=link                | L0   |
      | cid                        | L0   |

  Scenario Outline: A Layer 1 validator names one representation of one state
    Then the validator of state 5 in dialect <dialect> at rung <rung> is "<etag>"

    Examples:
      | dialect    | rung | etag              |
      | JSONSCHEMA | L2   | seq-5.hsf.L2 |
      | HALFORMS   | L0   | seq-5.hf.L0  |

  Scenario Outline: If-Match "<ifMatch>" against the current "seq-5.hsf.L2"
    Then If-Match <ifMatch> <verdict> the current validator "seq-5.hsf.L2"

    Examples:
      | ifMatch                          | verdict         |
      | "seq-5.hsf.L2"                   | matches         |
      | *                                | matches         |
      | "seq-4.hsf.L2", "seq-5.hsf.L2"   | matches         |
      | W/"seq-5.hsf.L2"                 | does not match  |
      | "seq-5.hsf.L1"                   | does not match  |
