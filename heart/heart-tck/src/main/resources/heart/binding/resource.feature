Feature: A declared resource and its acts, served over HTTP
  SPEC §6.1, §6.2, §6.4 and RFC 9110 §8.7. The HTTP binding serves Layer 1
  of every declared resource type at /{collection}/{id}, in either dialect,
  and takes its acts at /{collection}/{id}/actions/{path}. The server
  supplies only how a resource is read and what each act does.

  Background:
    Given lamp 7 is "ON" at version 3 in the lamp server

  Scenario: L0 in HAL Schema Forms: state and forms in one response, revalidated on every use
    When a client gets "/lamps/7" as hal-schema-forms
    Then the response status is 200
    And the response has Content-Type "application/hal+json"
    And the response has ETag "\"seq-3.hsf.L0.{digest}\""
    And the response has Cache-Control "no-cache"
    And the response has Vary "Accept, Prefer, Accept-Profile"
    And the response links its profile "https://w3id.org/heart-rise/hal-schema-forms"
    And the response offers
      | form            |
      | home:switch-off |
      | home:dim        |

  Scenario: L2 links to the shared affordance set, and says which preference it applied
    When a client gets "/lamps/7" as hal-schema-forms with Prefer "forms=link, schema=link"
    Then the response status is 200
    And the response has ETag "\"seq-3.hsf.L2.{digest}\""
    And the response has Preference-Applied "forms=link, schema=link"
    And the response links "hr:forms" to an affordance set the server serves

  Scenario: An unchanged resource is revalidated with 304
    When a client gets "/lamps/7" as hal-schema-forms with If-None-Match "\"seq-3.hsf.L0.{digest}\""
    Then the response status is 304
    And the response has Cache-Control "no-cache"

  Scenario: HAL-FORMS shows the same acts, as templates named by the acts' logical names
    Every template keeps its act's name, the first one too. HAL-FORMS says a
    lone template SHOULD be keyed "default", but that name says nothing about
    the act, and which act it fell on would depend only on its place in the
    list.
    When a client gets "/lamps/7" as hal-forms
    Then the response status is 200
    And the response has Content-Type "application/prs.hal-forms+json"
    And the response has ETag "\"seq-3.hf.L0.{digest}\""
    And the response has templates named
      | template  |
      | switchOff |
      | dim       |
    And the response has templates targeting
      | target                      |
      | /lamps/7/actions/switch-off |
      | /lamps/7/actions/dim        |

  Scenario: Accept that names no dialect is refused
    When a client gets "/lamps/7" with Accept "text/html"
    Then the response status is 406

  Scenario: A resource the server does not hold is not found
    When a client gets "/lamps/99" as hal-schema-forms
    Then the response status is 404

  Scenario: An act answers with the resource's new Layer 1, which it names
    When a client puts {"brightness": 20} to "/lamps/7/actions/dim" as hal-schema-forms with If-Match "\"seq-3.hsf.L0.{digest}\""
    Then the response status is 200
    And the response has Content-Location "/lamps/7"
    And the response has ETag "\"seq-4.hsf.L0.{digest}\""
    And the response body has "brightness" 20

  Scenario: An act still answers at the path it moved from, while its forms name only the new one
    The lamp's dim act used to be at "brighten". A client that kept the
    former target still reaches the act, with the same checks.
    When a client puts {"brightness": 20} to "/lamps/7/actions/brighten" as hal-schema-forms with If-Match "\"seq-3.hsf.L0.{digest}\""
    Then the response status is 200
    And the response has Content-Location "/lamps/7"
    And the response body has "brightness" 20
    When a client gets "/lamps/7" as hal-forms
    Then the response has templates targeting
      | target                      |
      | /lamps/7/actions/switch-off |
      | /lamps/7/actions/dim        |

  Scenario Outline: A refused act is a problem: <case>
    When a client <sends> <body> to "/lamps/7/actions/<path>" as hal-schema-forms with If-Match "<ifMatch>"
    Then the response status is <status>
    And the response is a problem of type "<type>"
    And lamp 7 is still at version 3

    Examples:
      | case                 | sends | path      | ifMatch           | body              | status | type                  |
      | no If-Match          | puts  | dim       |                   | {"brightness": 5} | 428    | precondition-required |
      | stale If-Match       | puts  | dim       | "seq-2.hsf.L0"    | {"brightness": 5} | 412    | stale                 |
      | not offered          | posts | switch-on | "seq-3.hsf.L0.{digest}" | {}          | 409    | not-offered           |
      | body breaks schema   | puts  | dim       | "seq-3.hsf.L0.{digest}" | {"brightness": 0} | 422 | invalid-input       |

  Scenario Outline: An act asked by a method not its own is refused, and Allow names its own: <sends> <path>
    SPEC §6.2: an act is served by the method its form says, and only by it.
    When a client <sends> <body> to "/lamps/7/actions/<path>" as hal-schema-forms with If-Match "\"seq-3.hsf.L0.{digest}\""
    Then the response status is 405
    And the response has Allow "<allow>"
    And lamp 7 is still at version 3

    Examples:
      | sends   | path       | body               | allow |
      | posts   | dim        | {"brightness": 20} | PUT   |
      | deletes | switch-off | {}                 | POST  |
      | patches | dim        | {"brightness": 20} | PUT   |

  Scenario: A body the schema accepts but a rule of the lamp refuses is its own problem, naming the field
    SPEC §6.2. The lamp is at 50, and dimming it to 50 is a rule only the
    lamp knows; the body is valid. invalid-input would tell the client its
    form handling is wrong, so the refusal says what it is: rule-refused.
    When a client puts {"brightness": 50} to "/lamps/7/actions/dim" as hal-schema-forms with If-Match "\"seq-3.hsf.L0.{digest}\""
    Then the response status is 422
    And the response is a problem of type "rule-refused"
    And the problem lists a violation at "/brightness"
    And the problem's detail is "The lamp is already at 50"
    And lamp 7 is still at version 3

  Scenario: The service index offers its own act, installing a lamp, beside its search and lookup
    When a client gets "/" as hal-schema-forms
    Then the response offers
      | form              |
      | home:search-lamps |
      | home:get-lamp     |
      | home:install-lamp |
      | pulls:list        |
      | pulls:get         |

  Scenario Outline: An act on the bookmark that brings a lamp into being answers 201 with the new lamp in <dialect>
    When a client posts {"brightness": 20} to "/actions/install-lamp" as <dialect> with the index's ETag
    Then the response status is 201
    And the response has Location "/lamps/8"
    And the response has Content-Location "/lamps/8"
    And the response has ETag "<etag>"
    And the response body has "brightness" 20

    Examples:
      | dialect          | etag                        |
      | hal-schema-forms | \"seq-1.hsf.L0.{digest}\" |
      | hal-forms        | \"seq-1.hf.L0.{digest}\"  |

  Scenario Outline: A refused act on the bookmark is a problem, and installs nothing: <case>
    When a client posts <body> to "/actions/install-lamp" as hal-schema-forms with If-Match "<ifMatch>"
    Then the response status is <status>
    And the response is a problem of type "<type>"
    And the lamp server holds only lamp 7

    Examples:
      | case               | ifMatch                      | body              | status | type                  |
      | no If-Match        |                              | {"brightness": 5} | 428    | precondition-required |
      | stale If-Match     | "index-hsf-0000000000000000" | {"brightness": 5} | 412    | stale                 |

  Scenario: An act on the bookmark whose body breaks the schema is refused, and installs nothing
    When a client posts {"brightness": 0} to "/actions/install-lamp" as hal-schema-forms with the index's ETag
    Then the response status is 422
    And the response is a problem of type "invalid-input"
    And the lamp server holds only lamp 7

  Scenario: The bookmark offers no act at an unknown path
    When a client posts {} to "/actions/nothing" as hal-schema-forms with If-Match "*"
    Then the response status is 404
