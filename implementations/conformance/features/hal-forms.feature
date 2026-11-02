Feature: What a HEART client must do in HAL-FORMS, the L0 on-ramp
  HAL-FORMS is one of HEART's two dialects (SPEC §6.1). It is served at L0 only,
  with each form inline under _templates and its fields in a properties array.
  A client that speaks it asks for it, and reads whichever dialect comes back.

  The garden serves here, and the build service, whose job "nightly" offers
  "build"; where a scenario says so, its template is the one written. A
  template's type is a hint for a person filling in a form, so a client
  refuses only what its other members disallow.

  Scenario: Find a bed from the bookmark and read what it offers
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "getBed" with {"id": 2}
    And it follows the "item" link titled "bed-2"
    Then the state's "status" is "PLANTED"
    And the representation is at rung L0, in HAL-FORMS
    And it offers exactly "waterCell"

  Scenario: Find a bed by part of its name
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "searchBeds" with {"name": "bed-2"}
    Then it holds these embedded resources
      | relation | title | status  |
      | item     | bed-2 | PLANTED |

  Scenario: An act in HAL-FORMS is conditional, and its answer is the resource's new state
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "getBed" with {"id": 2}
    And it follows the "item" link titled "bed-2"
    And it submits "waterCell" with {"milliliters": 250}
    Then the act carried If-Match with the validator it read
    And the state's "moistureLevel" is 65
    And it offers exactly "harvest"

  Scenario: When someone else acts first, the act is stale
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    And another HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "getBed" with {"id": 2}
    And it follows the "item" link titled "bed-2"
    And the other client waters bed 2 with 100 ml, in HAL-FORMS
    And it tries to submit "waterCell" with {"milliliters": 100}
    Then it was refused as "stale" with status 412
    And the state's "moistureLevel" is 50

  Scenario: A body a rule refuses is told apart in HAL-FORMS
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "getBed" with {"id": 2}
    And it follows the "item" link titled "bed-2"
    And it tries to submit "waterCell" with {"milliliters": 700}
    Then it was refused as "rule-refused" with status 422

  Scenario: A body outside a template's limits is refused before anything is sent
    Given the garden is at its seed state
    And a HAL-FORMS client with the garden's credential
    When it enters at the bookmark
    And it submits "getBed" with {"id": 2}
    And it follows the "item" link titled "bed-2"
    And it tries to submit "waterCell" with {"milliliters": 0}
    Then it was refused as "outside-schema" before sending, with the one violation "/milliliters minimum"

  Scenario: A client asks for HAL-FORMS, and for no layering
    Given a HAL-FORMS client at rung L2 with the build service's credential
    When it enters the job "nightly"
    Then the last read accepted "application/prs.hal-forms+json, application/hal+json;q=0.5", at the HAL-FORMS profile, with no cache
    And the last read asked for no layering
    And the representation is at rung L0, in HAL-FORMS

  Scenario: A client that asks for HAL-FORMS reads the reference dialect, if that is what comes back
    Given the deploy service serves only HAL Schema Forms
    And a HAL-FORMS client with the deploy service's credential
    When it enters the app "web"
    Then the representation is at rung L0, in HAL Schema Forms
    And it offers exactly these acts
      | cd:deploy |
      | cd:scale  |

  Scenario Outline: In HAL-FORMS the forms are the templates: <case>
    Given <service fault>
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    Then the representation is at rung L0, in HAL-FORMS
    And it offers exactly "build"

    Examples:
      | case                                    | service fault                                                    |
      | the media type is enough, no profile    | the build service names no profile                               |
      | a layering header means nothing         | the build service says it applied "forms=link, schema=link"      |

  Scenario Outline: What a template does not disallow is sent: <case>
    Given the build service's job "nightly" has the HAL-FORMS parameters <properties>
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    And it submits "build" with <body>
    Then the act "build" was sent 1 times
    And no schema failure was noted

    Examples:
      | case                     | properties                                                  | body                  |
      | a boolean for a checkbox | [{"name": "done", "type": "checkbox"}]                      | {"done": true}        |
      | text for a number        | [{"name": "n", "type": "number"}]                           | {"n": "5"}            |
      | an undeclared property   | [{"name": "a"}]                                             | {"a": 1, "b": 2}      |
      | an absent optional       | [{"name": "a", "required": false, "minLength": 2}]          | {}                    |
      | a member it does not use | [{"name": "a", "readOnly": true, "prompt": "A", "x": 1}]    | {"a": 1}              |
      | a nameless property      | [{"prompt": "no name", "required": true}, "text"]           | {}                    |
      | values at the limits     | [{"name": "n", "min": 1, "max": 3}]                         | {"n": 3}              |

  Scenario Outline: What a template disallows is refused, and nothing is sent: <case>
    Given the build service's job "nightly" has the HAL-FORMS parameters <properties>
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    And it tries to submit "build" with <body>
    Then it was refused as "outside-schema" before sending, with the one violation "<violation>"

    Examples:
      | case             | properties                        | body        | violation    |
      | a required value | [{"name": "a", "required": true}] | {}          | /a required  |
      | below min        | [{"name": "n", "min": 2}]         | {"n": 1}    | /n minimum   |
      | above max        | [{"name": "n", "max": 2}]         | {"n": 3}    | /n maximum   |
      | under minLength  | [{"name": "s", "minLength": 2}]   | {"s": "a"}  | /s minLength |
      | over maxLength   | [{"name": "s", "maxLength": 1}]   | {"s": "ab"} | /s maxLength |
      | against regex    | [{"name": "s", "regex": "^a$"}]   | {"s": "b"}  | /s pattern   |

  Scenario: A value outside a template's inline options is refused, and nothing is sent
    Given the build service's job "nightly" has the HAL-FORMS parameters [{"name": "s", "options": {"inline": ["a"]}}]
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    And it tries to submit "build" with {"s": "b"}
    Then it was refused as "not-a-choice" before sending, because "s: b is not one of the choices"

  Scenario: A regex the client cannot compile blocks nothing, and is noted locally once
    Given the build service's job "nightly" has the HAL-FORMS parameters [{"name": "s", "regex": "["}]
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    And it submits "build" with {"s": "a"}
    Then the act "build" was sent 1 times
    And the local log holds one schema failure, for "build"

  Scenario: A template says little, and the defaults fill the rest: a read of the resource itself
    Given the build service's template "build" is {}
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    And it submits "build" with {}
    Then the last request was GET with the body ""
    And the last request was for "/jobs/nightly"

  Scenario: A template with no target acts on the resource, as JSON
    Given the build service's template "build" is {"method": "POST"}
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    And it tries to submit "build" with {"a": 1}
    Then the last request was POST with the body "{\"a\":1}"
    And the last request was for "/jobs/nightly"
    And it had the content type "application/json"

  Scenario: A template's own title and content type are kept
    Given the build service's template "build" is {"method": "PUT", "target": "/jobs/nightly/config", "title": "Configure", "contentType": "application/vnd.jenkins+json"}
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    And it tries to submit "build" with {}
    Then it had the content type "application/vnd.jenkins+json"
    And the last request was for "/jobs/nightly/config"

  Scenario Outline: Fields of a <method> go in the query, form-encoded, replacing any there
    Given the build service's template "build" is {"method": "<method>", "target": "/jobs/nightly?old=1"}
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    And it tries to submit "build" with {"name": "a b"}
    Then the last request was <method> with the body ""
    And the last request was for "/jobs/nightly?name=a+b"

    Examples:
      | method |
      | GET    |
      | DELETE |

  Scenario: A field with several values repeats its name in the query, once for each
    Given the build service's template "build" is {"method": "GET", "target": "/jobs/nightly"}
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    And it tries to submit "build" with {"tag": ["a", "b"]}
    Then it sent GET for "/jobs/nightly?tag=a&tag=b", with no body

  Scenario: Options written inline are the choices
    Given the build service's job "nightly" has the HAL-FORMS parameters [{"name": "s", "options": {"inline": ["a", {"value": "b", "prompt": "Bee"}]}}]
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    Then the choices for "s" of "build" are
      | value | prompt |
      | a     | a      |
      | b     | Bee    |

  Scenario: Options may come from another resource
    Given the build service's job "nightly" has the HAL-FORMS parameters [{"name": "label", "options": {"link": {"href": "/labels"}, "valueField": "code", "promptField": "name"}}]
    And the build service serves "/labels" with status 200 and the body [{"code": "linux", "name": "Linux agents"}]
    And a HAL-FORMS client with the build service's credential
    When it enters the job "nightly"
    Then the choices for "label" of "build" are
      | value | prompt       |
      | linux | Linux agents |
